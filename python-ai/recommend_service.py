"""
双阶段复合推荐引擎 v2.0

阶段一（召回）：使用 sentence-transformers 向量匹配，截取 Top-3
阶段二（精排）：使用 DeepSeek Agent 选出最合适的人选并输出推荐理由

依赖库：
  - sentence-transformers（轻量中文向量模型）
  - numpy
  - httpx（调用 DeepSeek API）
"""
import os
import json
import logging
from typing import Dict, Any, List, Optional

logger = logging.getLogger("recommend-service")

DEEPSEEK_API_KEY = os.getenv("DEEPSEEK_API_KEY", "")
DEEPSEEK_API_URL = "https://api.deepseek.com/v1/chat/completions"
DEEPSEEK_MODEL  = os.getenv("DEEPSEEK_MODEL", "deepseek-chat")

# ── Transformer 向量模型（全局单例，避免重复加载）───────────────────────────

_transformer_model = None
_transformer_tokenizer = None


def _get_transformer():
    """
    懒加载 sentence-transformers 模型。
    首次调用时从 HuggingFace 下载（约 400MB）。
    """
    global _transformer_model, _transformer_tokenizer
    if _transformer_model is None:
        try:
            from sentence_transformers import SentenceTransformer
            logger.info("正在加载 sentence-transformers 模型 (text2vec-base-chinese)...")
            _transformer_model = SentenceTransformer("shibing624/text2vec-base-chinese")
            logger.info("模型加载完成")
        except Exception as e:
            logger.error(f"模型加载失败: {e}")
            raise RuntimeError(f"sentence-transformers 模型加载失败: {e}")
    return _transformer_model


def _encode_texts(texts: List[str]) -> "np.ndarray":
    """将文本列表编码为向量矩阵（numpy array）"""
    model = _get_transformer()
    embeddings = model.encode(texts, normalize_embeddings=True)
    return embeddings


def _cosine_similarity_matrix(vecs: "np.ndarray") -> "np.ndarray":
    """批量计算行向量之间的余弦相似度矩阵"""
    # vecs 已是 normalize过的，直接 dot product = cosine similarity
    import numpy as np
    return np.dot(vecs, vecs.T)


# ── DeepSeek 调用 ──────────────────────────────────────────────────────

def _call_deepseek(system_prompt: str, user_prompt: str, timeout: int = 30) -> str:
    """调用 DeepSeek API（失败时返回空）"""
    if not DEEPSEEK_API_KEY:
        logger.warning("DEEPSEEK_API_KEY 未配置，精排阶段跳过")
        return ""

    import httpx
    headers = {
        "Authorization": f"Bearer {DEEPSEEK_API_KEY}",
        "Content-Type": "application/json"
    }
    payload = {
        "model": DEEPSEEK_MODEL,
        "messages": [
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": user_prompt}
        ],
        "temperature": 0.3,
        "max_tokens": 300
    }
    try:
        with httpx.Client(timeout=timeout) as client:
            resp = client.post(DEEPSEEK_API_URL, headers=headers, json=payload)
            resp.raise_for_status()
            data = resp.json()
            return data["choices"][0]["message"]["content"].strip()
    except Exception as e:
        logger.error(f"DeepSeek API 调用失败: {e}")
        return ""


# ── 核心推荐逻辑 ──────────────────────────────────────────────────────

def hybrid_recommend(data: Dict[str, Any]) -> Dict[str, Any]:
    """
    双阶段复合推荐引擎

    Args:
        data: {
            "user_id": int,              # 当前用户ID（发起匹配者）
            "user_skills": List[str],   # 当前用户技能
            "user_profile": str,         # 当前用户描述
            "required_skills": List[str],# 活动所需技能
            "activity_title": str,       # 活动标题
            "activity_description": str,# 活动描述
            "candidates": List[Dict],    # 候选志愿者列表（由Java传入）
            "top_k": int = 3             # 召回阶段截取数量
        }

    Returns:
        {
            "user_id": int,
            "candidates": [
                {
                    "user_id": int,
                    "real_name": str,
                    "skills": str,
                    "total_hours": int,
                    "credit_balance": int,
                    "similarity_score": float,   # 向量相似度 0~1
                    "rank": int
                },
                ...
            ],
            "final_recommendation": {
                "recommended_user_id": int,
                "recommended_user_name": str,
                "ai_reasoning": str,      # ~100字中文自然语言推荐理由
                "ranking_criteria": str    # 排序依据说明
            },
            "stage": "hybrid" | "vector_only" | "fallback",
            "model_info": str
        }
    """
    import numpy as np
    import datetime

    user_id            = data.get("user_id", 0)
    user_skills        = data.get("user_skills") or []
    user_profile       = data.get("user_profile") or ""
    required_skills    = data.get("required_skills") or []
    activity_title     = data.get("activity_title") or ""
    activity_desc      = data.get("activity_description") or ""
    candidates         = data.get("candidates") or []
    top_k              = min(data.get("top_k", 3), 10)

    if not candidates:
        return {
            "user_id": user_id,
            "candidates": [],
            "final_recommendation": None,
            "stage": "fallback",
            "error": "候选列表为空"
        }

    try:
        # ══ 阶段一：向量召回 ══════════════════════════════════════════════
        candidates_result = _vector_recall(
            user_skills, user_profile, required_skills,
            activity_title, activity_desc, candidates, top_k
        )

        # ══ 阶段二：DeepSeek 精排 ══════════════════════════════════════
        final_recommendation = _deepseek_rerank(
            user_id, user_skills, activity_title, activity_desc, candidates_result
        )

        return {
            "user_id": user_id,
            "candidates": candidates_result,
            "final_recommendation": final_recommendation,
            "stage": "hybrid",
            "recommend_time": datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
            "model_info": "召回: text2vec-base-chinese | 精排: DeepSeek"
        }

    except Exception as e:
        logger.error(f"推荐引擎异常: {e}", exc_info=True)
        # 降级：返回按向量相似度排序的结果，不调用 DeepSeek
        try:
            fallback_candidates = _vector_recall(
                user_skills, user_profile, required_skills,
                activity_title, activity_desc, candidates, top_k
            )
            return {
                "user_id": user_id,
                "candidates": fallback_candidates,
                "final_recommendation": None,
                "stage": "fallback",
                "error": str(e)
            }
        except Exception:
            return {
                "user_id": user_id,
                "candidates": _fallback_ranking(candidates, required_skills),
                "final_recommendation": None,
                "stage": "fallback",
                "error": "推荐服务暂时不可用"
            }


def _vector_recall(
    user_skills: List[str],
    user_profile: str,
    required_skills: List[str],
    activity_title: str,
    activity_desc: str,
    candidates: List[Dict],
    top_k: int
) -> List[Dict[str, Any]]:
    """
    阶段一：使用 sentence-transformers 进行语义向量匹配
    """
    import numpy as np

    # 1. 构建文本描述
    activity_text = activity_title + "。" + (activity_desc or "")
    user_text    = "、".join(user_skills) + (f"。用户自述：{user_profile}" if user_profile else "")

    candidate_texts = []
    for c in candidates:
        skills_str = c.get("skills") or ""
        profile    = c.get("profile") or ""
        text = skills_str + (f"。{profile}" if profile else "")
        candidate_texts.append(text)

    # 2. 批量编码（activity vs user vs candidates）
    all_texts = [activity_text, user_text] + candidate_texts
    try:
        embeddings = _encode_texts(all_texts)
    except Exception as e:
        logger.warning(f"Transformer 编码失败，降级为规则匹配: {e}")
        return _fallback_ranking(candidates, required_skills)

    activity_vec  = embeddings[0]
    user_vec      = embeddings[1]
    cand_vectors  = embeddings[2:]

    # 3. 计算相似度
    activity_sim = np.dot(cand_vectors, activity_vec)
    user_sim     = np.dot(cand_vectors, user_vec)

    # 综合得分：活动需求匹配 × 0.6 + 用户画像匹配 × 0.4
    combined_scores = 0.6 * activity_sim + 0.4 * user_sim

    # 4. 排序取 Top-K
    top_indices = np.argsort(combined_scores)[::-1][:top_k]

    results = []
    for rank, idx in enumerate(top_indices):
        c = candidates[idx]
        results.append({
            "user_id":           c.get("user_id") or c.get("userId") or 0,
            "real_name":         c.get("real_name") or c.get("realName") or c.get("nickname") or "",
            "nickname":          c.get("nickname") or "",
            "skills":            c.get("skills") or "",
            "total_hours":       c.get("total_hours") or 0,
            "credit_balance":    c.get("credit_balance") or c.get("creditBalance") or 0,
            "similarity_score":  round(float(combined_scores[idx]), 4),
            "activity_match_score": round(float(activity_sim[idx]), 4),
            "profile_match_score": round(float(user_sim[idx]), 4),
            "rank":              rank + 1
        })

    return results


def _deepseek_rerank(
    user_id: int,
    user_skills: List[str],
    activity_title: str,
    activity_desc: str,
    candidates: List[Dict]
) -> Optional[Dict[str, Any]]:
    """
    阶段二：使用 DeepSeek Agent 从 Top-K 候选人中选择最优
    """
    if not candidates:
        return None

    # 组装候选人信息摘要（含出勤率）
    candidate_summary = []
    for c in candidates:
        attendance = c.get('attendance_rate') or c.get('attendanceRate') or 0
        candidate_summary.append(
            f"[候选人{c['rank']}] "
            f"姓名：{c.get('real_name') or c.get('nickname') or '匿名'}（user_id={c['user_id']}）"
            f"，技能：{c.get('skills') or '未填写'}"
            f"，服务时长：{c.get('total_hours', 0)}小时"
            f"，积分：{c.get('credit_balance', 0)}"
            f"，出勤率：{attendance:.0%}"
            f"，语义匹配度：{c.get('similarity_score', 0):.1%}"
        )
    candidates_text = "\n".join(candidate_summary)

    system_prompt = (
        "你是一位专业的志愿服务运营专家，负责为活动精准推荐最合适的志愿者。\n"
        "你必须只返回一个严格格式的JSON对象（不要任何其他文字），格式如下：\n"
        '{\n'
        '  "recommended_user_id": <数字>,\n'
        '  "recommended_user_name": "<姓名>",\n'
        '  "ai_reasoning": "<推荐理由，要求见下>"\n'
        "}\n"
        "【评分权重说明】\n"
        "- 语义匹配度（40%）：由 AI 向量模型计算候选人技能描述与活动需求的语义相关程度，越高代表技能越契合，这是最重要的维度\n"
        "- 服务时长（25%）：反映志愿经验积累，时长越长越可靠\n"
        "- 出勤率（20%）：反映履约可靠性，出勤率低的候选人存在放鸽子风险\n"
        "- 积分（15%）：平台信誉评级，积分高代表历史口碑好\n"
        "【选人规则】\n"
        "- 必须按照上述权重综合评分，语义匹配度最高者在其他维度相近时应优先被推荐\n"
        "- 若语义匹配度明显领先（差距>10%），即使时长或积分略低，也应优先推荐匹配度高的人\n"
        "- recommended_user_id 必须是候选人列表中存在的 user_id\n"
        "【ai_reasoning 写作要求】\n"
        "- 总长度 100-150 字，分两层：\n"
        "  第一层（约60字）：点名推荐人的语义匹配度数值，再结合时长/出勤率/积分中至少1项说明优势\n"
        "  第二层（约60字）：横向对比，须提到至少1位对比者姓名、其匹配度数值和具体不足\n"
        "- 语气专业客观，禁止空泛表述如'综合能力突出'、'表现优异'\n"
        "- 只输出 JSON，不要解释、不要 markdown 代码块"
    )

    user_prompt = (
        f"请从以下 {len(candidates)} 位候选志愿者中，按评分权重综合评估，选出最适合本次活动的最佳人选。\n\n"
        f"【活动信息】\n"
        f"名称：{activity_title}\n"
        f"描述：{activity_desc or '无'}\n\n"
        f"【候选人列表】（语义匹配度由AI向量模型计算，权重最高，请重点参考）\n"
        f"{candidates_text}\n\n"
        f"请严格按照权重（语义匹配度40% > 服务时长25% > 出勤率20% > 积分15%）综合评分，选出最优人选并撰写带横向对比的推荐理由。"
    )

    raw = _call_deepseek(system_prompt, user_prompt)

    if not raw:
        # API 不可用时，降级为相似度最高的候选人
        top = candidates[0] if candidates else None
        if top:
            return {
                "recommended_user_id": top["user_id"],
                "recommended_user_name": top.get("real_name") or top.get("nickname") or "匿名",
                "ai_reasoning": f"基于语义相似度匹配，该志愿者与活动需求相似度最高（{(top.get('similarity_score', 0) * 100):.1f}%），推荐参与本次志愿服务。",
                "ranking_criteria": "降级模式：使用向量相似度最高者，DeepSeek API 不可用"
            }
        return None

    # 解析 JSON
    try:
        parsed = json.loads(raw)
    except json.JSONDecodeError:
        logger.warning(f"DeepSeek 精排返回格式异常: {raw[:200]}")
        parsed = _extract_json_from_text(raw)
        if not parsed:
            top = candidates[0] if candidates else None
            return {
                "recommended_user_id": top["user_id"] if top else 0,
                "recommended_user_name": (top.get("real_name") or top.get("nickname")) if top else "匿名",
                "ai_reasoning": "AI分析服务暂时不可用，已自动选择向量相似度最高的候选人。",
                "ranking_criteria": "降级模式：向量相似度排序"
            }

    return {
        "recommended_user_id": parsed.get("recommended_user_id") or candidates[0]["user_id"],
        "recommended_user_name": parsed.get("recommended_user_name") or candidates[0].get("real_name") or "匿名",
        "ai_reasoning": parsed.get("ai_reasoning") or "AI推荐理由暂时无法生成",
        "ranking_criteria": "双阶段：Transformer向量召回 + DeepSeek Agent精排"
    }


def _extract_json_from_text(text: str) -> Dict[str, Any]:
    """从文本中提取 JSON 对象"""
    import re
    match = re.search(r'\{[\s\S]*\}', text)
    if match:
        try:
            return json.loads(match.group())
        except json.JSONDecodeError:
            pass
    return {}


def _fallback_ranking(
    candidates: List[Dict],
    required_skills: List[str]
) -> List[Dict[str, Any]]:
    """
    降级方案：基于规则的简单匹配（模型不可用时）
    """
    import numpy as np

    if not candidates:
        return []

    req_set = set(required_skills) if required_skills else set()
    scored = []
    for c in candidates:
        skills_str = c.get("skills") or ""
        skills_list = [s.strip() for s in skills_str.replace(",", " ").split() if s.strip()]
        skills_set = set(skills_list)
        matched = len(req_set & skills_set) if req_set else 0
        hours   = c.get("total_hours") or c.get("total_hours") or 0
        credit  = c.get("credit_balance") or c.get("creditBalance") or 0
        # 简单加权得分
        score = matched * 0.5 + min(hours / 100, 1) * 0.3 + min(credit / 500, 1) * 0.2
        scored.append((score, c))

    scored.sort(key=lambda x: x[0], reverse=True)
    results = []
    for rank, (score, c) in enumerate(scored[:5]):
        results.append({
            "user_id":        c.get("user_id") or c.get("userId") or 0,
            "real_name":      c.get("real_name") or c.get("realName") or c.get("nickname") or "",
            "nickname":       c.get("nickname") or "",
            "skills":         c.get("skills") or "",
            "total_hours":    c.get("total_hours") or 0,
            "credit_balance": c.get("credit_balance") or 0,
            "similarity_score": round(score, 4),
            "rank": rank + 1
        })
    return results
