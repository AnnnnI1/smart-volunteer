"""
动态隐式反馈个性化 Feed 推荐引擎

核心逻辑：
  1. 用户向量融合：
     - 技能画像向量（基准）
     - 历史行为向量（加权平均）：完成(1.0) > 签到(0.7) > 报名(0.3)
     - 融合比例：行为向量 × 0.6 + 画像向量 × 0.4（无历史时纯画像；无画像时用平台热门兜底）
  2. 活动池全局余弦相似度排序
  3. 分页截取当前页批次
  4. DeepSeek 为每个活动生成 ≤30 字专属推荐语
"""
import os
import json
import logging
from typing import Dict, Any, List, Optional

logger = logging.getLogger("feed-service")

DEEPSEEK_API_KEY = os.getenv("DEEPSEEK_API_KEY", "")
DEEPSEEK_API_URL = "https://api.deepseek.com/v1/chat/completions"
DEEPSEEK_MODEL   = os.getenv("DEEPSEEK_MODEL", "deepseek-chat")

# 平台热门兜底文本（新用户无画像无历史时使用）
_FALLBACK_PROFILE_TEXT = "志愿服务 社区公益 环保 教育 医疗救援 文化活动"

# 行为类型权重
_BEHAVIOR_WEIGHTS = {
    "completed": 1.0,   # 已完成（status=3 且已签到）
    "checked_in": 0.7,  # 已签到
    "registered": 0.3,  # 已报名未签到
}


def _get_model():
    """复用 recommend_service 里的单例 Transformer 模型"""
    from recommend_service import _get_transformer
    return _get_transformer()


def _encode(texts: List[str]):
    """编码文本列表为归一化向量矩阵"""
    import numpy as np
    model = _get_model()
    return model.encode(texts, normalize_embeddings=True)


def _build_user_vector(
    user_skills: List[str],
    user_profile: str,
    history_activities: List[Dict]  # [{title, description, behavior_type}, ...]
):
    """
    构建用户融合向量：
      - 画像文本 = 技能 + profile 描述
      - 行为文本 = 每条历史活动的 title+desc，按权重加权平均
      - 最终 = 行为向量×0.6 + 画像向量×0.4（无历史降级，无画像降级）
    """
    import numpy as np

    # ── 1. 画像向量 ──────────────────────────────────────────────────
    profile_text = "、".join(user_skills) if user_skills else ""
    if user_profile:
        profile_text += f"。{user_profile}"
    if not profile_text.strip():
        profile_text = _FALLBACK_PROFILE_TEXT

    profile_vec = _encode([profile_text])[0]  # shape (768,)

    # ── 2. 行为向量（加权平均）──────────────────────────────────────
    if not history_activities:
        # 无历史 → 纯画像
        return profile_vec

    behavior_vecs = []
    behavior_weights = []
    texts = []
    weights_raw = []

    for h in history_activities:
        text = (h.get("title") or "") + "。" + (h.get("description") or "")
        texts.append(text.strip() or "志愿服务活动")
        w = _BEHAVIOR_WEIGHTS.get(h.get("behavior_type", "registered"), 0.3)
        weights_raw.append(w)

    vecs = _encode(texts)  # shape (N, 768)
    weights_arr = np.array(weights_raw, dtype=float)
    weights_arr /= weights_arr.sum()  # 归一化权重

    behavior_vec = np.average(vecs, axis=0, weights=weights_arr)
    # 再归一化
    norm = np.linalg.norm(behavior_vec)
    if norm > 0:
        behavior_vec /= norm

    # ── 3. 融合 ────────────────────────────────────────────────────
    fused = 0.6 * behavior_vec + 0.4 * profile_vec
    norm = np.linalg.norm(fused)
    if norm > 0:
        fused /= norm
    return fused


def _score_activities(user_vec, activities: List[Dict]) -> List[Dict]:
    """
    对活动池全局余弦相似度计算，返回带 feed_score 字段的列表（降序）
    activities 每项需含：id, title, description, required_skills
    """
    import numpy as np

    if not activities:
        return []

    texts = []
    for a in activities:
        text = (a.get("title") or "") + "。" + (a.get("description") or "") \
               + "。" + (a.get("required_skills") or "")
        texts.append(text.strip() or "志愿服务活动")

    act_vecs = _encode(texts)  # shape (M, 768)
    scores = np.dot(act_vecs, user_vec)  # cosine sim（向量已归一化）

    result = []
    for i, a in enumerate(activities):
        item = dict(a)
        item["feed_score"] = float(round(scores[i], 4))
        result.append(item)

    result.sort(key=lambda x: x["feed_score"], reverse=True)
    return result


def _call_deepseek_batch(
    user_skills: List[str],
    history_titles: List[str],
    activities: List[Dict]
) -> Dict[int, str]:
    """
    批量调用 DeepSeek，为当前页活动生成 ≤30 字的专属推荐语
    返回 {activity_id: reason_str}
    """
    if not DEEPSEEK_API_KEY or not activities:
        return {}

    import httpx

    # 构建用户上下文摘要
    skills_str = "、".join(user_skills) if user_skills else "暂无技能标签"
    history_str = "、".join(history_titles[:5]) if history_titles else "暂无历史"

    # 构建活动列表摘要
    act_lines = []
    for a in activities:
        act_lines.append(
            f"[ID={a['id']}] 「{a['title']}」"
            f"（所需技能：{a.get('required_skills') or '不限'}，"
            f"匹配度：{a['feed_score']:.1%}）"
        )
    acts_text = "\n".join(act_lines)

    system_prompt = (
        "你是一位志愿规划师，正在为志愿者推荐活动。\n"
        "你必须只返回一个严格的 JSON 对象，key 为活动ID（数字），value 为专属推荐语。\n"
        "推荐语要求：\n"
        "- 每条 ≤30 字，中文自然语言\n"
        "- 结合该志愿者的技能或历史参与经历，体现个性化（不能全部千篇一律）\n"
        "- 语气温暖、像朋友推荐，不要用'您'，用'你'\n"
        "- 禁止出现'综合考虑'、'非常适合'等套话\n"
        "- 只输出 JSON，不要任何解释或 markdown\n"
        "示例格式：{\"19\": \"你做过环保活动，这次植树正好能用上！\", \"20\": \"急救经验丰富，马拉松现场最需要你\"}"
    )

    user_prompt = (
        f"志愿者技能：{skills_str}\n"
        f"历史参与活动：{history_str}\n\n"
        f"请为以下 {len(activities)} 个活动分别生成专属推荐语：\n"
        f"{acts_text}\n\n"
        f"返回格式：{{\"活动ID\": \"推荐语\", ...}}"
    )

    try:
        headers = {
            "Authorization": f"Bearer {DEEPSEEK_API_KEY}",
            "Content-Type": "application/json"
        }
        payload = {
            "model": DEEPSEEK_MODEL,
            "messages": [
                {"role": "system", "content": system_prompt},
                {"role": "user",   "content": user_prompt}
            ],
            "temperature": 0.7,
            "max_tokens": 400
        }
        with httpx.Client(timeout=30) as client:
            resp = client.post(DEEPSEEK_API_URL, headers=headers, json=payload)
            resp.raise_for_status()
            raw = resp.json()["choices"][0]["message"]["content"].strip()
    except Exception as e:
        logger.error(f"DeepSeek 批量推荐语生成失败: {e}")
        return {}

    # 解析 JSON
    try:
        parsed = json.loads(raw)
        return {int(k): v for k, v in parsed.items()}
    except Exception:
        import re
        m = re.search(r'\{[\s\S]*\}', raw)
        if m:
            try:
                parsed = json.loads(m.group())
                return {int(k): v for k, v in parsed.items()}
            except Exception:
                pass
    return {}


def feed_recommend(data: Dict[str, Any]) -> Dict[str, Any]:
    """
    Feed 推荐主入口

    Args:
        data: {
            "user_id": int,
            "user_skills": List[str],          # 技能标签
            "user_profile": str,               # 个人简介
            "history_activities": [            # 历史行为（已完成/签到/报名）
                {
                    "title": str,
                    "description": str,
                    "behavior_type": "completed" | "checked_in" | "registered"
                }, ...
            ],
            "activity_pool": [                 # 可用活动池（已过滤已报名）
                {
                    "id": int,
                    "title": str,
                    "description": str,
                    "required_skills": str,
                    "status": int,
                    "start_time": str,
                    "total_quota": int,
                    "joined_quota": int,
                    "remain_quota": int,
                    "organizer_name": str
                }, ...
            ],
            "page": int,       # 从 1 开始
            "page_size": int   # 每页条数，默认 6
        }

    Returns:
        {
            "user_id": int,
            "page": int,
            "page_size": int,
            "total": int,
            "has_more": bool,
            "items": [
                {
                    "id": int,
                    "title": str,
                    "required_skills": str,
                    "status": int,
                    "start_time": str,
                    "total_quota": int,
                    "joined_quota": int,
                    "remain_quota": int,
                    "organizer_name": str,
                    "feed_score": float,       # 匹配度 0~1
                    "ai_reason": str,          # DeepSeek 专属推荐语（≤30字）
                    "fallback_reason": bool    # true=DeepSeek不可用时的降级文案
                }, ...
            ],
            "vector_mode": str  # "behavior+profile" | "profile_only" | "fallback"
        }
    """
    import numpy as np

    user_id           = data.get("user_id", 0)
    user_skills       = data.get("user_skills") or []
    user_profile      = data.get("user_profile") or ""
    history_activities = data.get("history_activities") or []
    activity_pool     = data.get("activity_pool") or []
    page              = max(1, int(data.get("page", 1)))
    page_size         = min(10, max(1, int(data.get("page_size", 6))))

    if not activity_pool:
        return {
            "user_id": user_id, "page": page, "page_size": page_size,
            "total": 0, "has_more": False, "items": [], "vector_mode": "empty"
        }

    # ── 判断向量模式 ────────────────────────────────────────────────
    has_history = bool(history_activities)
    has_profile = bool(user_skills or user_profile)
    if has_history and has_profile:
        vector_mode = "behavior+profile"
    elif has_profile:
        vector_mode = "profile_only"
    else:
        vector_mode = "fallback"

    # ── 构建用户向量并对活动池评分 ──────────────────────────────────
    try:
        user_vec = _build_user_vector(user_skills, user_profile, history_activities)
        scored = _score_activities(user_vec, activity_pool)
    except Exception as e:
        logger.error(f"向量计算失败: {e}", exc_info=True)
        # 完全降级：直接按原顺序返回
        scored = [dict(a, feed_score=0.0) for a in activity_pool]
        vector_mode = "fallback"

    total = len(scored)
    start = (page - 1) * page_size
    end   = start + page_size
    page_items = scored[start:end]
    has_more = end < total

    if not page_items:
        return {
            "user_id": user_id, "page": page, "page_size": page_size,
            "total": total, "has_more": False, "items": [], "vector_mode": vector_mode
        }

    # ── DeepSeek 批量生成专属推荐语 ─────────────────────────────────
    history_titles = [h.get("title", "") for h in history_activities if h.get("title")]
    reasons = _call_deepseek_batch(user_skills, history_titles, page_items)

    # ── 组装结果 ────────────────────────────────────────────────────
    items = []
    for a in page_items:
        act_id = int(a.get("id", 0))
        ai_reason = reasons.get(act_id, "")
        fallback = False
        if not ai_reason:
            # 降级推荐语：基于匹配度生成简短描述
            score = a.get("feed_score", 0)
            if score >= 0.6:
                ai_reason = f"与你的技能高度匹配（{score:.0%}），值得优先关注"
            elif score >= 0.35:
                ai_reason = f"技能有一定契合度（{score:.0%}），可以了解一下"
            else:
                ai_reason = "探索新领域，拓展志愿经历"
            fallback = True

        items.append({
            "id":             act_id,
            "title":          a.get("title", ""),
            "description":    a.get("description", ""),
            "required_skills": a.get("required_skills", ""),
            "status":         a.get("status", 0),
            "start_time":     str(a.get("start_time", "")),
            "total_quota":    int(a.get("total_quota") or 0),
            "joined_quota":   int(a.get("joined_quota") or 0),
            "remain_quota":   int(a.get("remain_quota") or 0),
            "organizer_name": a.get("organizer_name", ""),
            "feed_score":     a.get("feed_score", 0.0),
            "ai_reason":      ai_reason,
            "fallback_reason": fallback
        })

    logger.info(
        f"Feed推荐完成 user_id={user_id} page={page} mode={vector_mode} "
        f"total={total} page_size={len(items)} deepseek={'ok' if reasons else 'fallback'}"
    )

    return {
        "user_id":    user_id,
        "page":       page,
        "page_size":  page_size,
        "total":      total,
        "has_more":   has_more,
        "items":      items,
        "vector_mode": vector_mode
    }
