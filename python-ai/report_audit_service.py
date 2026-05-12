"""
举报仲裁 AI 分析服务（report_audit_service.py）

提供举报内容的风险分析、违规类型判定、惩罚建议等功能。
供 Java 后端通过 POST /ai/analyze-report 调用。

功能：
  - 举报内容风险分析
  - 违规类型判定（AR-01~AR-06, VR-01~VR-06）
  - 证据强度评估
  - 惩罚建议生成（积分扣除/封禁/限制发起活动/降级）
  - DeepSeek API 调用（含降级方案）
"""

import os
import json
import logging
import re
from typing import Optional, Dict, Any, List

logger = logging.getLogger("report_audit")

# ── DeepSeek API ────────────────────────────────────────────────────────────

DEEPSEEK_API_URL = "https://api.deepseek.com/v1/chat/completions"
DEEPSEEK_API_KEY = os.environ.get("DEEPSEEK_API_KEY", "")
DEEPSEEK_MODEL = os.environ.get("DEEPSEEK_MODEL", "deepseek-chat")


def _call_deepseek(system_prompt: str, user_prompt: str, timeout: int = 30) -> Optional[str]:
    """调用 DeepSeek API，返回 JSON 字符串。失败返回 None。"""
    if not DEEPSEEK_API_KEY:
        logger.warning("DeepSeek API Key 未配置，跳过 AI 调用")
        return None

    import httpx
    try:
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
            "max_tokens": 800
        }
        with httpx.Client(timeout=timeout) as client:
            resp = client.post(DEEPSEEK_API_URL, json=payload, headers=headers)
            resp.raise_for_status()
            data = resp.json()
            return data["choices"][0]["message"]["content"]
    except Exception as e:
        logger.warning(f"DeepSeek API 调用失败: {e}")
        return None


def _extract_json(text: str) -> Optional[Dict]:
    """从文本中提取 JSON 块"""
    text = text.strip()
    # 尝试直接解析
    try:
        return json.loads(text)
    except Exception:
        pass
    # 尝试从 ```json ... ``` 中提取
    match = re.search(r"```(?:json)?\s*(.*?)```", text, re.DOTALL)
    if match:
        try:
            return json.loads(match.group(1).strip())
        except Exception:
            pass
    # 尝试从头部的 { 到最后的 }
    start = text.find("{")
    end = text.rfind("}")
    if start != -1 and end != -1 and end > start:
        try:
            return json.loads(text[start:end + 1])
        except Exception:
            pass
    return None


# ── 举报类型元数据 ────────────────────────────────────────────────────────────

REPORT_TYPE_META = {
    # 活动举报（AR）
    "AR-01": {
        "name": "活动内容与实际不符",
        "severity_score": 6,
        "typical_penalty_range": [50, 150],
        "typical_ban_days": 0,
        "activity_limit": False,
        "demotion": False,
    },
    "AR-02": {
        "name": "存在安全隐患未告知",
        "severity_score": 9,
        "typical_penalty_range": [100, 300],
        "typical_ban_days": 30,
        "activity_limit": True,
        "demotion": True,
    },
    "AR-03": {
        "name": "组织者不当行为",
        "severity_score": 8,
        "typical_penalty_range": [100, 200],
        "typical_ban_days": 30,
        "activity_limit": True,
        "demotion": True,
    },
    "AR-04": {
        "name": "违规收集信息",
        "severity_score": 9,
        "typical_penalty_range": [150, 300],
        "typical_ban_days": 90,
        "activity_limit": True,
        "demotion": True,
    },
    "AR-05": {
        "name": "虚假宣传",
        "severity_score": 7,
        "typical_penalty_range": [50, 150],
        "typical_ban_days": 0,
        "activity_limit": False,
        "demotion": False,
    },
    "AR-06": {
        "name": "其他违规",
        "severity_score": 4,
        "typical_penalty_range": [20, 80],
        "typical_ban_days": 0,
        "activity_limit": False,
        "demotion": False,
    },
    # 志愿者违规举报（VR）
    "VR-01": {
        "name": "无故缺席",
        "severity_score": 5,
        "typical_penalty_range": [20, 50],
        "typical_ban_days": 0,
        "activity_limit": False,
        "demotion": False,
    },
    "VR-02": {
        "name": "故意破坏活动秩序",
        "severity_score": 8,
        "typical_penalty_range": [100, 300],
        "typical_ban_days": 30,
        "activity_limit": False,
        "demotion": False,
    },
    "VR-03": {
        "name": "言语攻击/骚扰他人",
        "severity_score": 9,
        "typical_penalty_range": [200, 500],
        "typical_ban_days": 90,
        "activity_limit": False,
        "demotion": False,
    },
    "VR-04": {
        "name": "损坏公物/他人财物",
        "severity_score": 7,
        "typical_penalty_range": [50, 200],
        "typical_ban_days": 14,
        "activity_limit": False,
        "demotion": False,
    },
    "VR-05": {
        "name": "违反活动纪律规定",
        "severity_score": 4,
        "typical_penalty_range": [10, 50],
        "typical_ban_days": 0,
        "activity_limit": False,
        "demotion": False,
    },
    "VR-06": {
        "name": "其他违规行为",
        "severity_score": 3,
        "typical_penalty_range": [10, 30],
        "typical_ban_days": 0,
        "activity_limit": False,
        "demotion": False,
    },
}


# ── 核心分析函数 ────────────────────────────────────────────────────────────

def analyze_report(data: Dict[str, Any]) -> Dict[str, Any]:
    """
    举报仲裁分析主入口。

    入参 data:
      report_type       - "AR" 或 "VR"
      category_code     - 如 "AR-01", "VR-02"
      description       - 举报说明
      evidence_urls     - 凭证 URL 列表
      reported_user_id  - 被举报人 ID
      activity_id      - 关联活动 ID
      reporter_id      - 举报人 ID
      history_summary   - 被举报人历史记录 {
          total_reports: int,
          total_violations: int,
          credit_score: int,
          organizer_level: int,
      }

    返回结构化结果：
      risk_level       - "high" | "medium" | "low"
      violation_types  - ["AR-01", ...]
      evidence_strength - 0.0 ~ 1.0
      mitigating_factors - ["初犯", ...]
      aggravating_factors - ["涉及未成年人安全", ...]
      suggested_penalty - {...}
      confidence        - 0.0 ~ 1.0
      reasoning         - 分析依据说明
    """
    report_type = data.get("report_type", "")
    category_code = data.get("category_code", "")
    description = data.get("description", "")
    evidence_urls = data.get("evidence_urls") or []
    history = data.get("history_summary") or {}

    # 1. 基础元数据
    meta = REPORT_TYPE_META.get(category_code, REPORT_TYPE_META.get("AR-06"))
    base_severity = meta["severity_score"]

    # 2. 历史记录调整
    total_reports = history.get("total_reports", 0)
    total_violations = history.get("total_violations", 0)
    credit_score = history.get("credit_score", 70)
    organizer_level = history.get("organizer_level", 0)

    # 3. DeepSeek AI 分析
    ai_result = _analyze_with_deepseek(report_type, category_code, description, history)

    if ai_result:
        return ai_result

    # 4. 降级方案：基于规则自动判定
    return _rule_based_analysis(category_code, description, evidence_urls, history)


def _analyze_with_deepseek(report_type: str, category_code: str,
                            description: str, history: Dict) -> Optional[Dict]:
    """调用 DeepSeek 进行结构化分析，失败返回 None 触发降级"""

    system_prompt = f"""你是一个志愿服务平台的违规仲裁 AI 助手。
你的任务是分析举报内容，输出结构化的风险评估和惩罚建议。

【举报类型体系】
活动举报(AR)：AR-01活动内容与实际不符, AR-02存在安全隐患未告知,
             AR-03组织者不当行为, AR-04违规收集信息,
             AR-05虚假宣传, AR-06其他违规
志愿者违规(VR)：VR-01无故缺席, VR-02故意破坏活动秩序,
              VR-03言语攻击/骚扰他人, VR-04损坏公物/他人财物,
              VR-05违反活动纪律规定, VR-06其他违规行为

【惩罚梯度参考】
- 积分扣除：AR类 50~300分，VR类 10~500分
- 封禁时长：0~90天（严重情节可永久封禁）
- 限制发起活动：临时(30天)/永久/无
- 组织者降级：金牌→铜牌→新晋

【输出格式】
请直接输出 JSON，不要有其他文字：
{{
  "risk_level": "high|medium|low",
  "violation_types": ["AR-01"],
  "evidence_strength": 0.0~1.0,
  "mitigating_factors": ["因素1", ...],
  "aggravating_factors": ["因素1", ...],
  "suggested_penalty": {{
    "credit_deduct": 50~500,
    "ban_days": 0~365,
    "activity_limit": "temporary|permanent|none",
    "demotion": true|false,
    "notification_to_user": "惩罚通知内容（30字内）"
  }},
  "confidence": 0.0~1.0,
  "reasoning": "分析依据说明（50字内）"
}}"""

    history_context = f"""
被举报人历史记录：
- 累计被举报次数：{history.get('total_reports', 0)}
- 累计违规次数：{history.get('total_violations', 0)}
- 信用分：{history.get('credit_score', 70)}
- 组织者等级：{history.get('organizer_level', 0)}（0=新晋,1=铜牌,2=银牌,3=金牌）
"""

    user_prompt = f"""举报类型：{report_type}，违规编号：{category_code}
举报说明：{description or '无'}
{history_context}

请分析举报内容，输出 JSON 格式的风险评估和惩罚建议。"""

    response = _call_deepseek(system_prompt, user_prompt, timeout=30)
    if not response:
        return None

    parsed = _extract_json(response)
    if not parsed:
        logger.warning(f"DeepSeek 返回无法解析: {response[:200]}")
        return None

    # 验证字段完整性
    required_fields = ["risk_level", "violation_types", "evidence_strength",
                       "suggested_penalty", "confidence", "reasoning"]
    for field in required_fields:
        if field not in parsed:
            logger.warning(f"DeepSeek 返回缺少字段 {field}")
            return None

    return parsed


def _rule_based_analysis(category_code: str, description: str,
                          evidence_urls: List[str], history: Dict) -> Dict[str, Any]:
    """规则降级方案：基于类型元数据和历史记录自动生成分析结果"""

    meta = REPORT_TYPE_META.get(category_code, REPORT_TYPE_META.get("AR-06"))
    base_severity = meta["severity_score"]

    # 历史调整
    total_reports = history.get("total_reports", 0)
    total_violations = history.get("total_violations", 0)
    credit_score = history.get("credit_score", 70)

    severity = base_severity
    mitigating_factors = []
    aggravating_factors = []

    if total_violations == 0:
        mitigating_factors.append("初犯")
    else:
        aggravating_factors.append(f"有违规历史（{total_violations}次）")

    if credit_score < 40:
        aggravating_factors.append("信用分较低（<40）")
    elif credit_score >= 80:
        mitigating_factors.append("信用分优秀（≥80）")

    if not evidence_urls:
        mitigating_factors.append("举报人未提供凭证")
    else:
        aggravating_factors.append(f"提供了{len(evidence_urls)}张凭证截图")

    if len(description) < 30:
        mitigating_factors.append("举报说明内容较少")

    # 关键词判断加重因素
    serious_keywords = ["未成年人", "老人", "小孩", "孩子", "危险", "受伤", "性骚扰",
                        "歧视", "暴力", "金钱", "收费", "诈骗", "传销"]
    for kw in serious_keywords:
        if kw in description:
            aggravating_factors.append(f"涉及敏感内容：{kw}")
            severity = min(10, severity + 1)

    # 证据强度
    evidence_strength = min(1.0, len(evidence_urls) * 0.3 + (0.5 if len(description) > 50 else 0.2))

    # 风险等级
    risk_level = "high" if severity >= 8 else "medium" if severity >= 5 else "low"

    # 惩罚建议
    base_range = meta["typical_penalty_range"]
    credit_deduct = base_range[0] + (base_range[1] - base_range[0]) * (severity / 10)

    # 有历史加重
    if total_violations > 0:
        credit_deduct = min(base_range[1], int(credit_deduct * 1.3))
    if total_reports > 3:
        credit_deduct = min(base_range[1], int(credit_deduct * 1.5))
        aggravating_factors.append(f"被多次举报（{total_reports}次）")

    credit_deduct = int(credit_deduct)
    ban_days = meta["typical_ban_days"]
    if total_violations > 2:
        ban_days = min(90, ban_days * 2)

    activity_limit = "temporary" if meta["activity_limit"] else "none"
    demotion = meta["demotion"] and total_violations > 0

    notification_msg = f"您因\"{meta['name']}\"被扣除{credit_deduct}积分"
    if ban_days > 0:
        notification_msg += f"，禁止参与活动{ban_days}天"
    if demotion:
        notification_msg += "，组织者等级已调整"

    return {
        "risk_level": risk_level,
        "violation_types": [category_code],
        "evidence_strength": round(evidence_strength, 2),
        "mitigating_factors": mitigating_factors,
        "aggravating_factors": aggravating_factors,
        "suggested_penalty": {
            "credit_deduct": credit_deduct,
            "ban_days": ban_days,
            "activity_limit": activity_limit,
            "demotion": demotion,
            "notification_to_user": notification_msg
        },
        "confidence": 0.75,
        "reasoning": f"基于{REPORT_TYPE_META.get(category_code, {}).get('name', '未知违规')}类型自动判定，" \
                     f"基础严重度{base_severity}，结合历史记录综合评估"
    }
