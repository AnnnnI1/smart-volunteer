"""
KNN 志愿者匹配服务（Python 增强版）

相比 Java 版本的改进：
1. 使用 scikit-learn 的 cosine_similarity，支持批量向量化计算
2. 加入 TF-IDF 权重：稀缺技能权重更高，避免常见技能稀释相似度
3. 加入服务时长奖励分：同等技能相似度下优先推荐经验更丰富的志愿者
4. 加入积分信誉加成：高积分志愿者（历史靠谱）获得更高推荐权重
5. 冷启动平滑：新志愿者（无任何历史数据）用活跃用户均值的 50% 作为先验，
   避免因三项 bonus 全为 0 而被老志愿者完全压制（最多差距 +0.45）
"""
import numpy as np
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.metrics.pairwise import cosine_similarity
from typing import List, Dict, Any


def knn_match(required_skills: List[str], volunteers: List[Dict], top_k: int) -> List[Dict]:
    """
    增强版 KNN 匹配：TF-IDF 加权 + 服务时长奖励

    Args:
        required_skills: 活动所需技能列表，如 ["急救", "医疗"]
        volunteers: 志愿者列表，每项含 user_id, skills(逗号分隔), real_name, total_hours
        top_k: 返回前 K 个结果

    Returns:
        按综合评分降序排列的 Top-K 志愿者列表
    """
    if not volunteers or not required_skills:
        return []

    # ── 1. 构建语料库（用空格拼接技能，适配 TfidfVectorizer）──────────────
    query_text = " ".join(required_skills)
    vol_texts = []
    for v in volunteers:
        skills_str = v.get("skills", "") or ""
        skills_list = [s.strip() for s in skills_str.split(",") if s.strip()]
        vol_texts.append(" ".join(skills_list))

    corpus = [query_text] + vol_texts  # index 0 = 需求向量

    # ── 2. TF-IDF 向量化 ─────────────────────────────────────────────────
    # analyzer="word" + token_pattern 匹配中文和英文
    vectorizer = TfidfVectorizer(analyzer="word", token_pattern=r"(?u)\b\w+\b")
    try:
        tfidf_matrix = vectorizer.fit_transform(corpus)
    except ValueError:
        # 语料库为空时降级为 0/1 向量
        return _fallback_binary(required_skills, volunteers, top_k)

    query_vec = tfidf_matrix[0]           # 需求向量
    vol_matrix = tfidf_matrix[1:]         # 志愿者矩阵

    # ── 3. 余弦相似度（批量计算）────────────────────────────────────────
    similarities = cosine_similarity(query_vec, vol_matrix)[0]  # shape (n_volunteers,)

    # ── 4. 冷启动平滑：新志愿者无历史数据时用贝叶斯先验填充 ────────────
    # 若三项历史指标（时长/积分/出勤率）全为 0，判定为冷启动用户。
    # 直接给 0 会导致老志愿者凭 bonus（最多 +0.45）碾压技能匹配更好的新人。
    # 修复：用有历史记录的活跃用户均值的 50% 作为新人的先验值。
    raw_hours = [v.get("total_hours", 0) or 0 for v in volunteers]
    raw_credit = [v.get("credit_balance", 0) or 0 for v in volunteers]
    raw_attendance = [v.get("attendance_rate", 0) or 0 for v in volunteers]

    active_hours_vals = [h for h in raw_hours if h > 0]
    active_credit_vals = [c for c in raw_credit if c > 0]
    active_attendance_vals = [a for a in raw_attendance if a > 0]

    avg_hours = sum(active_hours_vals) / len(active_hours_vals) if active_hours_vals else 0
    avg_credit = sum(active_credit_vals) / len(active_credit_vals) if active_credit_vals else 0
    avg_attendance = sum(active_attendance_vals) / len(active_attendance_vals) if active_attendance_vals else 0

    def _is_cold_start(v):
        return (
            (v.get("total_hours", 0) or 0) == 0
            and (v.get("credit_balance", 0) or 0) == 0
            and (v.get("attendance_rate", 0) or 0) == 0
        )

    eff_hours = np.array([
        avg_hours * 0.5 if _is_cold_start(v) else (v.get("total_hours", 0) or 0)
        for v in volunteers
    ])
    eff_credit = np.array([
        avg_credit * 0.5 if _is_cold_start(v) else (v.get("credit_balance", 0) or 0)
        for v in volunteers
    ])
    eff_attendance = np.array([
        avg_attendance * 0.5 if _is_cold_start(v) else (v.get("attendance_rate", 0) or 0)
        for v in volunteers
    ])

    # ── 5. 服务时长奖励（最多 +0.10，归一化到 [0,1] 再乘系数）──────────
    max_hours = max(eff_hours.max(), 1)
    hours_bonus = eff_hours / max_hours * 0.10

    # ── 6. 积分信誉加成（最多 +0.15，归一化到 [0,1] 再乘系数）──────────
    max_credit = max(eff_credit.max(), 1)
    credit_bonus = eff_credit / max_credit * 0.15

    # ── 7. 出勤率加成（最多 +0.20，权重最高）────────────────────────────
    attendance_bonus = eff_attendance * 0.20

    final_scores = similarities + hours_bonus + credit_bonus + attendance_bonus

    # ── 8. Top-K 排序 ────────────────────────────────────────────────────
    top_indices = np.argsort(final_scores)[::-1][:max(top_k, 1)]

    results = []
    req_set = set(required_skills)
    for rank, idx in enumerate(top_indices):
        vol = volunteers[idx]
        vol_skills = [s.strip() for s in (vol.get("skills", "") or "").split(",") if s.strip()]
        matched = [s for s in vol_skills if s in req_set]
        att_rate = vol.get("attendance_rate", 0) or 0
        results.append({
            "userId":          vol["userId"],
            "realName":        vol.get("realName") or f"用户{vol['userId']}",
            "skills":          vol.get("skills", ""),
            "totalHours":      vol.get("total_hours", 0) or 0,
            "creditBalance":   vol.get("credit_balance", 0) or 0,
            "similarity":      round(float(similarities[idx]), 4),
            "hoursScore":      round(float(hours_bonus[idx]), 4),
            "creditScore":     round(float(credit_bonus[idx]), 4),
            "attendanceScore": round(float(attendance_bonus[idx]), 4),
            "attendanceRate":  round(float(att_rate), 4),
            "finalScore":      round(float(final_scores[idx]), 4),
            "matchedSkills":   matched,
            "rank":            rank + 1,
            "isColdStart":     _is_cold_start(vol),
        })

    return results


def _fallback_binary(required_skills, volunteers, top_k):
    """降级方案：0/1 向量 + 余弦相似度（与 Java 版等价）"""
    vocab = list(set(required_skills))
    for v in volunteers:
        for s in (v.get("skills", "") or "").split(","):
            s = s.strip()
            if s and s not in vocab:
                vocab.append(s)

    def to_vec(skill_list):
        sk = set(skill_list)
        return np.array([1.0 if w in sk else 0.0 for w in vocab])

    query_vec = to_vec(required_skills)
    results = []
    req_set = set(required_skills)
    for v in volunteers:
        vol_skills = [s.strip() for s in (v.get("skills", "") or "").split(",") if s.strip()]
        vol_vec = to_vec(vol_skills)
        norm = np.linalg.norm(query_vec) * np.linalg.norm(vol_vec)
        sim = float(np.dot(query_vec, vol_vec) / norm) if norm > 0 else 0.0
        results.append({
            "userId": v["userId"], "realName": v.get("realName", ""),
            "skills": v.get("skills", ""), "totalHours": v.get("total_hours", 0) or 0,
            "creditBalance": v.get("credit_balance", 0) or 0,
            "similarity": round(sim, 4), "hoursScore": 0.0, "creditScore": 0.0,
            "finalScore": round(sim, 4),
            "matchedSkills": [s for s in vol_skills if s in req_set], "rank": 0,
        })

    results.sort(key=lambda x: x["finalScore"], reverse=True)
    for i, r in enumerate(results[:top_k]):
        r["rank"] = i + 1
    return results[:top_k]
