"""
志愿者流失预警模型（轻量级评分模型）

流失定义：
  - 取消率 > 60%（高取消行为）
  - 或超过 45 天无活动（长期不活跃）
  - 且总报名次数 < 3（参与深度不足）

风险评分公式（加权线性模型，模拟逻辑回归输出）：
  risk = 0.40 × cancel_rate
       + 0.35 × min(inactive_days / 60, 1.0)
       + 0.15 × max(0, 1 - total_hours / 30)
       + 0.10 × max(0, 1 - signup_count / 5)

风险等级：
  高风险：risk ≥ 0.65
  中风险：0.35 ≤ risk < 0.65
  低风险：risk < 0.35
"""
from typing import List, Dict


def predict_churn(volunteers: List[Dict]) -> List[Dict]:
    """
    批量预测志愿者流失风险

    Args:
        volunteers: 每项含 user_id, nickname, signup_count,
                    cancel_count, total_hours, inactive_days

    Returns:
        按风险评分降序排列的预警列表
    """
    results = []
    for vol in volunteers:
        signup_count  = vol.get("signup_count", 0) or 0
        cancel_count  = vol.get("cancel_count", 0) or 0
        total_hours   = vol.get("total_hours", 0) or 0
        inactive_days = vol.get("inactive_days", 0) or 0

        # ── 各维度风险因子（均归一化到 [0,1]）────────────────────────
        cancel_rate   = cancel_count / signup_count if signup_count > 0 else 0.0
        inactivity    = min(inactive_days / 60.0, 1.0)   # 60天视为完全不活跃
        low_hours     = max(0.0, 1 - total_hours / 30.0)  # <30h视为参与不深
        low_signup    = max(0.0, 1 - signup_count / 5.0)  # <5次视为浅度用户

        # ── 加权求和（论文中可描述为简化逻辑回归）───────────────────────
        risk_score = (
            0.40 * cancel_rate +
            0.35 * inactivity  +
            0.15 * low_hours   +
            0.10 * low_signup
        )
        risk_score = round(min(risk_score, 1.0), 4)

        # ── 风险等级 ──────────────────────────────────────────────────
        if risk_score >= 0.65:
            risk_level = "高"
            risk_color = "danger"
        elif risk_score >= 0.35:
            risk_level = "中"
            risk_color = "warning"
        else:
            risk_level = "低"
            risk_color = "success"

        # ── 风险因素说明（用于前端展示）─────────────────────────────
        factors = []
        if cancel_rate >= 0.5:
            factors.append(f"取消率 {round(cancel_rate*100)}% 偏高")
        if inactive_days >= 30:
            factors.append(f"已 {inactive_days} 天未参与")
        if total_hours < 10:
            factors.append(f"服务时长仅 {total_hours} 小时")
        if signup_count < 3:
            factors.append(f"总报名仅 {signup_count} 次")
        if not factors:
            factors.append("整体表现良好")

        results.append({
            "userId":       vol["user_id"],
            "nickname":     vol.get("nickname", f"用户{vol['user_id']}"),
            "signupCount":  signup_count,
            "cancelCount":  cancel_count,
            "cancelRate":   round(cancel_rate, 4),
            "totalHours":   total_hours,
            "inactiveDays": inactive_days,
            "riskScore":    risk_score,
            "riskLevel":    risk_level,
            "riskColor":    risk_color,
            "riskFactors":  factors,
        })

    # 按风险评分降序
    results.sort(key=lambda x: x["riskScore"], reverse=True)
    return results
