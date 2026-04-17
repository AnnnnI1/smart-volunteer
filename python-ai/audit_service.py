"""
AI 审核服务：组织者入驻尽调 & 活动发布风控

依赖 DeepSeek API 进行内容分析和风险评估。
"""
import os
import json
import logging
from typing import Dict, Any

logger = logging.getLogger("audit-service")

DEEPSEEK_API_KEY = os.getenv("DEEPSEEK_API_KEY", "")
DEEPSEEK_API_URL = "https://api.deepseek.com/chat/completions"

# DeepSeek 模型名称
DEEPSEEK_MODEL = os.getenv("DEEPSEEK_MODEL", "deepseek-chat")


def _call_deepseek(system_prompt: str, user_prompt: str, timeout: int = 15) -> str:
    """
    调用 DeepSeek API。

    失败时返回空字符串，调用方负责降级处理。
    """
    if not DEEPSEEK_API_KEY:
        logger.warning("DEEPSEEK_API_KEY 未配置，审核服务降级返回空报告")
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
        "temperature": 0.1,
        "max_tokens": 600
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


# ─────────────────────────── 组织者入驻尽调 ───────────────────────────────────

def audit_organizer(data: Dict[str, Any]) -> Dict[str, Any]:
    """
    组织者入驻 AI 尽调

    分析志愿者的历史履约数据和申请理由，输出维度分析和结论。

    Args:
        data: 包含 user_id, username, nickname, apply_reason,
              total_hours, total_activities, signup_count, cancel_count,
              skills, credit_balance

    Returns:
        {
            "user_id": int,
            "username": str,
            "audit_status": 0,           # 0=待审（AI不改变业务状态）
            "audit_time": str,
            "dimensions_analysis": {
                "content_compliance": {   # 内容合规
                    "score": float,       # 0~100
                    "analysis": str,
                    "conclusion": str    # 合规/存疑/不合规
                },
                "history_fulfillment": { # 历史履约
                    "score": float,
                    "analysis": str,
                    "conclusion": str
                },
                "qualification_assessment": { # 资质评估
                    "score": float,
                    "analysis": str,
                    "conclusion": str
                }
            },
            "ai_conclusion": str,      # 一句话建议
            "overall_risk_level": str,  # 低/中/高
            "raw_response": str        # DeepSeek 原始回复（用于人工复核）
        }
    """
    import datetime

    user_id        = data.get("user_id", 0)
    username       = data.get("username", "")
    nickname       = data.get("nickname") or data.get("real_name") or username
    apply_reason   = data.get("apply_reason", "")
    total_hours    = data.get("total_hours", 0) or 0
    total_acts     = data.get("total_activities", 0) or 0
    signup_count   = data.get("signup_count", 0) or 0
    cancel_count   = data.get("cancel_count", 0) or 0
    skills         = data.get("skills") or ""
    credit_balance = data.get("credit_balance", 0) or 0

    cancel_rate = round(cancel_count / signup_count, 4) if signup_count > 0 else 0.0

    # 组装 Prompt
    system_prompt = (
        "你是一位专业的志愿服务机构风控审核专家。你的职责是对申请成为组织者的志愿者进行客观公正的资质评估。\n"
        "你必须严格按以下 JSON Schema 输出，不要添加任何额外文字（只输出 JSON）：\n"
        "{\n"
        '  "dimensions_analysis": {\n'
        '    "content_compliance": {"score": <0~100数字>, "analysis": "<分析说明>", "conclusion": "<合规|存疑|不合规>"},\n'
        '    "history_fulfillment": {"score": <0~100数字>, "analysis": "<分析说明>", "conclusion": "<优秀|良好|一般|较差>"},\n'
        '    "qualification_assessment": {"score": <0~100数字>, "analysis": "<分析说明>", "conclusion": "<完全符合|基本符合|部分符合|不符合>}\n'
        "  },\n"
        '  "ai_conclusion": "<一句话建议，如：推荐通过，建议关注其历史取消率>",\n'
        '  "overall_risk_level": "<低|中|高>"\n'
        "}\n"
        "注意：score 保留整数，analysis 最多40字，conclusion 不能为空字符串。"
    )

    user_prompt = (
        f"请对以下志愿者申请成为组织者进行尽调审核：\n\n"
        f"用户ID：{user_id}\n"
        f"用户名：{username}\n"
        f"昵称：{nickname}\n"
        f"申请理由：{apply_reason}\n"
        f"总服务时长：{total_hours} 小时\n"
        f"累计报名活动：{signup_count} 次\n"
        f"取消报名：{cancel_count} 次（取消率 {(cancel_rate * 100):.1f}%）\n"
        f"技能标签：{skills or '未填写'}\n"
        f"积分余额：{credit_balance}\n\n"
        f"请从以下三个维度评估：\n"
        f"1. 内容合规：申请理由是否合规、真实、无违规内容；\n"
        f"2. 历史履约：历史取消率是否偏高、是否有失信行为；\n"
        f"3. 资质评估：技能、时长、积分等是否具备基本组织能力。\n\n"
        f"输出 JSON（不要其他文字）："
    )

    raw = _call_deepseek(system_prompt, user_prompt)

    # 降级：当 API 不可用时
    if not raw:
        return _fallback_organizer_audit(data)

    # 解析 JSON
    try:
        parsed = json.loads(raw)
    except json.JSONDecodeError:
        logger.warning(f"DeepSeek 返回格式异常，尝试正则提取: {raw[:200]}")
        parsed = _extract_json(raw)
        if not parsed:
            return _fallback_organizer_audit(data, raw)

    return {
        "user_id": user_id,
        "username": username,
        "audit_status": 0,
        "audit_time": datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        "dimensions_analysis": parsed.get("dimensions_analysis", {}),
        "ai_conclusion": parsed.get("ai_conclusion", "AI分析服务暂时不可用，请人工审核"),
        "overall_risk_level": parsed.get("overall_risk_level", "中"),
        "raw_response": raw
    }


def _fallback_organizer_audit(data: Dict[str, Any], raw: str = "") -> Dict[str, Any]:
    """降级：基于规则自动评估（API 不可用时使用）"""
    import datetime
    signup_count = data.get("signup_count", 0) or 0
    cancel_count = data.get("cancel_count", 0) or 0
    cancel_rate  = cancel_count / signup_count if signup_count > 0 else 0.0
    total_hours = data.get("total_hours", 0) or 0
    apply_reason= data.get("apply_reason", "")

    # 风险等级判定
    if cancel_rate > 0.4:
        risk = "高"
        fulfillment_conclusion = "较差"
        fulfillment_score = max(0, 80 - int(cancel_rate * 100))
    elif cancel_rate > 0.2:
        risk = "中"
        fulfillment_conclusion = "一般"
        fulfillment_score = max(0, 90 - int(cancel_rate * 80))
    else:
        risk = "低"
        fulfillment_conclusion = "良好"
        fulfillment_score = 100

    hour_score = min(100, total_hours * 5)
    if total_hours >= 20:
        hour_conclusion = "优秀"
    elif total_hours >= 5:
        hour_conclusion = "良好"
    else:
        hour_conclusion = "一般"

    reason_len = len(apply_reason) if apply_reason else 0
    reason_score = min(100, reason_len * 3)
    reason_conclusion = "合规" if reason_len >= 10 else "存疑"

    return {
        "user_id": data.get("user_id", 0),
        "username": data.get("username", ""),
        "audit_status": 0,
        "audit_time": datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        "dimensions_analysis": {
            "content_compliance": {
                "score": reason_score,
                "analysis": f"申请理由长度{reason_len}字，内容基础合规检查通过",
                "conclusion": reason_conclusion
            },
            "history_fulfillment": {
                "score": fulfillment_score,
                "analysis": f"历史取消率{(cancel_rate * 100):.1f}%，报名{signup_count}次，取消{cancel_count}次",
                "conclusion": fulfillment_conclusion
            },
            "qualification_assessment": {
                "score": hour_score,
                "analysis": f"服务时长{total_hours}小时，具备基础资质",
                "conclusion": hour_conclusion
            }
        },
        "ai_conclusion": f"风险{risk}级，建议{'通过' if risk == '低' else '人工重点审核'}",
        "overall_risk_level": risk,
        "raw_response": raw or "【降级模式】DeepSeek API 不可用，基于规则评估"
    }


def _extract_json(text: str) -> Dict[str, Any]:
    """从包含 JSON 的文本中提取 JSON 对象"""
    import re
    # 尝试找第一个 { ... } 块
    match = re.search(r'\{[\s\S]*\}', text)
    if match:
        try:
            return json.loads(match.group())
        except json.JSONDecodeError:
            pass
    return {}


# ─────────────────────────── 活动发布风控 ────────────────────────────────────

def audit_activity(data: Dict[str, Any]) -> Dict[str, Any]:
    """
    活动发布 AI 风控初审

    使用 DeepSeek 检查活动标题和详情是否包含违规/涉黄/营销内容。

    Args:
        data: 包含 activity_id, title, description, organizer_name

    Returns:
        {
            "activity_id": int,
            "is_compliant": bool,
            "risk_level": str,       # 低/中/高
            "risk_reasons": List[str],
            "suggestion": str,
            "raw_response": str
        }
    """
    import datetime

    activity_id   = data.get("activity_id", 0)
    title         = data.get("title", "")
    description   = data.get("description", "") or ""
    organizer     = data.get("organizer_name") or "未知组织者"

    system_prompt = (
        "你是一位专业的内容风控审核专家。请对志愿活动的内容进行合规性检测。\n"
        "重点检测：涉黄、涉暴、涉政、营销广告、虚假信息、违法违规内容。\n"
        "严格按以下 JSON Schema 输出（只输出 JSON，不要其他文字）：\n"
        "{\n"
        '  "is_compliant": <true|false>,\n'
        '  "risk_level": "<低|中|高>",\n'
        '  "risk_reasons": ["<原因1>", "<原因2>"],\n'
        '  "suggestion": "<一句话建议>"\n'
        "}\n"
        "注意：risk_reasons 最多3条，每条不超过30字。"
    )

    user_prompt = (
        f"请审核以下志愿活动内容：\n\n"
        f"活动标题：{title}\n"
        f"活动详情：{description}\n"
        f"组织者：{organizer}\n\n"
        f"请判断是否合规，给出风险等级和具体风险原因。\n"
        f"输出 JSON（不要其他文字）："
    )

    raw = _call_deepseek(system_prompt, user_prompt, timeout=12)

    if not raw:
        return _fallback_activity_audit(data)

    try:
        parsed = json.loads(raw)
    except json.JSONDecodeError:
        logger.warning(f"活动风控返回格式异常: {raw[:200]}")
        parsed = _extract_json(raw)
        if not parsed:
            return _fallback_activity_audit(data, raw)

    return {
        "activity_id": activity_id,
        "is_compliant": parsed.get("is_compliant", True),
        "risk_level": parsed.get("risk_level", "低"),
        "risk_reasons": parsed.get("risk_reasons") or [],
        "suggestion": parsed.get("suggestion", "请人工复核"),
        "audit_time": datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        "raw_response": raw
    }


def _fallback_activity_audit(data: Dict[str, Any], raw: str = "") -> Dict[str, Any]:
    """降级：基于关键词规则的活动风控"""
    import re
    import datetime

    title       = data.get("title", "") or ""
    description = data.get("description", "") or ""
    combined    = title + " " + description

    # 高风险关键词（仅用于降级，实际情况由 DeepSeek 判断）
    high_risk_patterns = [
        r"色情|赌博|诈骗|传销|暴力|恐怖|money|贷款|一夜暴富|裸聊"
    ]
    medium_risk_patterns = [
        r"收费|付费|会员|VIP|提成|佣金|返利|投资|股票|虚拟货币"
    ]

    risk_reasons = []
    risk_level   = "低"
    is_compliant = True

    for pattern in high_risk_patterns:
        if re.search(pattern, combined, re.IGNORECASE):
            risk_reasons.append(f"检测到高风险词：{pattern}")
            risk_level = "高"
            is_compliant = False

    for pattern in medium_risk_patterns:
        if re.search(pattern, combined, re.IGNORECASE):
            risk_reasons.append(f"检测到可疑词：{pattern}")
            if risk_level != "高":
                risk_level = "中"

    if len(title) < 5:
        risk_reasons.append("活动标题过短，可能信息不足")
        if risk_level == "低":
            risk_level = "中"

    suggestion = "合规，建议通过" if is_compliant else f"不合规（{len(risk_reasons)}条风险原因），建议修改后重审"

    return {
        "activity_id": data.get("activity_id", 0),
        "is_compliant": is_compliant,
        "risk_level": risk_level,
        "risk_reasons": risk_reasons if risk_reasons else ["内容基础合规检查通过"],
        "suggestion": suggestion,
        "audit_time": datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        "raw_response": raw or "【降级模式】基于关键词规则检测"
    }
