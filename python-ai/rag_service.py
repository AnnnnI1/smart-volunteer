"""
RAG 活动发布智能助手服务

功能：
  - 活动合规问答（RAG 检索 + DeepSeek 生成）
  - 活动内容诊断（表单 + 规则知识库）
  - 活动草稿生成（一句话意图 → 完整活动描述）

技术栈：
  - sentence-transformers（text2vec-base-chinese）做 Embedding 向量化
  - sklearn.neighbors.NearestNeighbors 做向量检索（Python 3.8 兼容，纯 numpy/scipy）
  - DeepSeek API 做生成式回答

依赖：
  - sentence-transformers（已在 recommend_service.py 中验证）
  - scikit-learn（已在 requirements.txt 中，NearstNeighbors 实现向量检索）
  - httpx（已在其他模块中广泛使用）
"""
import os
import json
import logging
import pathlib
import re
import numpy as np
from typing import Dict, Any, List, Optional

logger = logging.getLogger("rag-service")

DEEPSEEK_API_KEY = os.getenv("DEEPSEEK_API_KEY", "")
DEEPSEEK_API_URL = "https://api.deepseek.com/chat/completions"
DEEPSEEK_MODEL  = os.getenv("DEEPSEEK_MODEL", "deepseek-chat")

# 向量持久化路径（NearestNeighbors 不内置持久化，手动序列化）
VECTORS_PATH = os.path.join(os.path.dirname(__file__), "knowledge_vectors.npy")
META_PATH    = os.path.join(os.path.dirname(__file__), "knowledge_vectors_meta.json")
KNOWLEDGE_DIR = os.path.join(os.path.dirname(__file__), "knowledge")


# ─────────────────────────── DeepSeek 调用 ─────────────────────────────────

def _call_deepseek(
    system_prompt: str,
    user_prompt: str,
    timeout: int = 30
) -> str:
    """
    调用 DeepSeek API。失败时返回空字符串，调用方负责降级处理。
    """
    if not DEEPSEEK_API_KEY:
        logger.warning("DEEPSEEK_API_KEY 未配置，RAG 生成降级返回")
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
        "max_tokens": 800
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


def _extract_json(text: str) -> Dict[str, Any]:
    """从包含 JSON 的文本中提取 JSON 对象"""
    match = re.search(r'\{[\s\S]*\}', text)
    if match:
        try:
            return json.loads(match.group())
        except json.JSONDecodeError:
            pass
    return {}


# ─────────────────────────── Transformer 模型 ──────────────────────────────

_transformer_model = None


def _get_transformer():
    """懒加载 sentence-transformers 模型（全局单例，避免重复加载）"""
    global _transformer_model
    if _transformer_model is None:
        try:
            from sentence_transformers import SentenceTransformer
            logger.info("正在加载 sentence-transformers 模型 (text2vec-base-chinese)...")
            _transformer_model = SentenceTransformer("shibing624/text2vec-base-chinese")
            logger.info("Transformer 模型加载完成")
        except Exception as e:
            logger.error(f"模型加载失败: {e}")
            raise RuntimeError(f"sentence-transformers 模型加载失败: {e}")
    return _transformer_model


# ─────────────────────────── NearestNeighbors 索引 ────────────────────────

_nn_index    = None   # sklearn.neighbors.NearestNeighbors
_nn_vectors  = None   # numpy.ndarray，向量矩阵
_nn_metadata = []      # List[Dict]，对应每个向量


def _get_index():
    """
    获取 NearestNeighbors 索引、向量矩阵和元数据（全局单例）。
    启动时尝试从磁盘加载已有索引。
    """
    global _nn_index, _nn_vectors, _nn_metadata

    if _nn_index is not None:
        return _nn_index, _nn_vectors, _nn_metadata

    if os.path.exists(VECTORS_PATH) and os.path.exists(META_PATH):
        try:
            _nn_vectors = np.load(VECTORS_PATH)
            with open(META_PATH, "r", encoding="utf-8") as f:
                _nn_metadata = json.load(f)
            if _nn_vectors.shape[0] > 0:
                from sklearn.neighbors import NearestNeighbors
                _nn_index = NearestNeighbors(
                    n_neighbors=min(10, _nn_vectors.shape[0]),
                    metric="cosine",
                    algorithm="brute"
                )
                _nn_index.fit(_nn_vectors)
                logger.info(f"从磁盘加载向量索引，共 {_nn_vectors.shape[0]} 条向量")
                return _nn_index, _nn_vectors, _nn_metadata
        except Exception as e:
            logger.warning(f"加载向量索引失败，将重新构建: {e}")

    _nn_index    = None
    _nn_vectors  = None
    _nn_metadata = []
    return _nn_index, _nn_vectors, _nn_metadata


def _save_index():
    """将向量矩阵和元数据持久化到磁盘"""
    global _nn_vectors, _nn_metadata
    if _nn_vectors is None:
        return
    try:
        np.save(VECTORS_PATH, _nn_vectors)
        with open(META_PATH, "w", encoding="utf-8") as f:
            json.dump(_nn_metadata, f, ensure_ascii=False, indent=2)
        logger.info(f"向量索引已持久化: {VECTORS_PATH}")
    except Exception as e:
        logger.warning(f"向量索引持久化失败: {e}")


# ─────────────────────────── 文档解析 ────────────────────────────────────

def _load_markdown_files() -> List[Dict[str, Any]]:
    """
    读取 knowledge/ 目录下的所有 Markdown 文件，按 ## 标题切分为 chunks。
    每个 chunk 包含：text, title, file, section。
    """
    knowledge_path = pathlib.Path(KNOWLEDGE_DIR)
    if not knowledge_path.exists():
        logger.warning(f"知识库目录不存在: {KNOWLEDGE_DIR}")
        return []

    chunks = []
    for md_file in sorted(knowledge_path.glob("*.md")):
        try:
            content = md_file.read_text(encoding="utf-8")
        except Exception as e:
            logger.warning(f"读取知识库文件失败 {md_file}: {e}")
            continue

        file_name = md_file.name
        lines = content.splitlines()
        current_title = ""
        current_section = ""
        current_h1 = ""
        current_body_lines = []

        def _flush_chunk():
            nonlocal current_title, current_section, current_body_lines, current_h1
            if current_body_lines:
                text = "\n".join(current_body_lines).strip()
                if text:
                    chunks.append({
                        "text": text,
                        "title": current_title or current_h1 or file_name,
                        "file": file_name,
                        "section": current_section
                    })
            current_body_lines = []
            current_section = ""

        for line in lines:
            stripped = line.strip()
            if stripped.startswith("# ") and not stripped.startswith("## "):
                _flush_chunk()
                current_h1 = stripped[2:].strip()
                current_title = current_h1
            elif stripped.startswith("## "):
                _flush_chunk()
                current_section = stripped[3:].strip()
                current_title = current_section
            else:
                current_body_lines.append(line)

        _flush_chunk()

    logger.info(f"加载知识库文件 {len(set(c['file'] for c in chunks))} 个，切分 {len(chunks)} 个 chunks")
    return chunks


# ─────────────────────────── RAG 检索 ────────────────────────────────────

def _retrieve(query: str, top_k: int = 5) -> List[Dict[str, Any]]:
    """
    RAG 检索：根据 query 向量化后在 NearestNeighbors 索引中召回 top_k 相关规则片段。
    降级：索引为空时返回空列表。
    """
    try:
        index, vectors, metadata = _get_index()
        model = _get_transformer()

        if index is None or vectors is None or vectors.shape[0] == 0:
            logger.warning("向量索引为空，跳过检索")
            return []

        query_vec = model.encode([query], normalize_embeddings=True)
        k = min(top_k, vectors.shape[0])
        distances, indices = index.kneighbors(query_vec, n_neighbors=k)

        chunks = []
        for dist, idx in zip(distances[0], indices[0]):
            if idx < 0 or idx >= len(metadata):
                continue
            meta = metadata[idx]
            chunks.append({
                "content":  meta.get("text", ""),
                "title":    meta.get("title", ""),
                "file":     meta.get("file", ""),
                "section":  meta.get("section", ""),
                "distance": float(dist)
            })
        return chunks

    except Exception as e:
        logger.warning(f"向量检索失败，降级返回空结果: {e}")
        return []


# ─────────────────────────── 对话历史管理 ─────────────────────────────────

def _build_history_prompt(history: List[Dict[str, str]]) -> str:
    """将对话历史构建为 DeepSeek 可见的上下文字符串"""
    if not history:
        return ""
    parts = []
    for msg in history[-6:]:#只取最后 6 条消息（history[-6:]），避免上下文过长消耗 token。
        role = "用户" if msg.get("role") == "user" else "助手"
        parts.append(f"{role}：{msg.get('content', '')}")
    return "\n".join(parts)


# ─────────────────────────── 核心功能函数 ────────────────────────────────

def build_index() -> Dict[str, Any]:
    """
    构建 RAG 知识库索引：读取 Markdown → 切分 chunks → 向量化 → 写入 NearestNeighbors。
    幂等：每次调用清空索引后重建，然后持久化到磁盘。
    """
    try:
        from sklearn.neighbors import NearestNeighbors
        model = _get_transformer()

        raw_chunks = _load_markdown_files()
        if not raw_chunks:
            logger.warning("知识库为空，跳过索引构建")
            return {"status": "skipped", "message": "知识库为空"}

        texts = [c["text"] for c in raw_chunks]
        vectors = model.encode(texts, normalize_embeddings=True).astype('float32')

        dim = vectors.shape[1]
        k = min(10, vectors.shape[0])
        index = NearestNeighbors(n_neighbors=k, metric="cosine", algorithm="brute")
        index.fit(vectors)

        metadata = [
            {
                "text":    c["text"],
                "title":   c["title"],
                "file":    c["file"],
                "section": c["section"]
            }
            for c in raw_chunks
        ]

        global _nn_index, _nn_vectors, _nn_metadata
        _nn_index    = index
        _nn_vectors  = vectors
        _nn_metadata = metadata

        _save_index()

        logger.info(f"RAG 索引构建完成，共写入 {len(raw_chunks)} 个 chunks，维度 {dim}")
        return {"status": "success", "chunks": len(raw_chunks)}

    except Exception as e:
        logger.error(f"RAG 索引构建失败: {e}", exc_info=True)
        return {"status": "error", "message": str(e)}


def chat(question: str, history: Optional[List[Dict[str, str]]] = None) -> Dict[str, Any]:
    """
    RAG 智能问答：检索相关规则片段后调用 DeepSeek 生成回答。
    """
    history = history or []

    retrieved = _retrieve(question, top_k=5)
    history_text = _build_history_prompt(history)

    if retrieved:
        context_parts = [
            f"【参考文档{i}】来源：{r['file']} - {r['title']}\n{r['content']}"
            for i, r in enumerate(retrieved, 1)
        ]
        context = "\n\n".join(context_parts)
    else:
        context = "（未检索到相关规则文档，AI 回答仅供参考，请结合实际情况判断）"

    system_prompt = (
        "你是智能志愿者管理平台的'活动发布合规助手'。\n"
        "你的任务是：基于给定的知识库内容，回答组织者关于志愿活动发布规范、安全要求、平台审核标准的问题。\n\n"
        "约束：\n"
        "1. 只基于知识库内容和平台规则回答，不得编造法律条文。\n"
        "2. 若知识库无明确依据，说'知识库中暂无明确规定，建议联系平台管理员确认'。\n"
        "3. 面向活动组织者，语言清晰、建议具体可执行。\n"
        "4. 不给出绝对法律结论，只提示合规风险和修改建议。\n"
        "5. 回答结尾应列出参考来源（知识库文件名和章节标题）。\n"
        "6. 如涉及未成年人、户外活动、专业技能等特殊场景，应主动给出对应的安全要求提醒。\n"
    )

    user_prompt = (
        f"{history_text}\n"
        f"用户问题：{question}\n\n"
        f"参考知识库内容：\n{context}\n\n"
        f"请基于上述知识库内容回答用户问题。"
    )

    raw = _call_deepseek(system_prompt, user_prompt, timeout=30)

    if not raw:
        return {
            "answer": "抱歉，AI 服务暂时不可用。请联系平台管理员确认。当前可参考以下相关规则：\n\n" +
                      "\n\n".join([f"- {r['title']}（来源：{r['file']}）" for r in retrieved[:3]]),
            "sources": retrieved[:3]
        }

    return {"answer": raw, "sources": retrieved[:3]}


def diagnose_activity(form_data: Dict[str, Any]) -> Dict[str, Any]:
    """
    活动内容 AI 诊断：结合表单内容和规则知识，输出结构化风险报告。
    """
    title        = form_data.get("title") or ""
    description  = form_data.get("description") or ""
    location     = form_data.get("location") or ""
    outdoor      = form_data.get("outdoor", False)
    minors       = form_data.get("involves_minors", False)
    professional = form_data.get("requires_professional_skill", False)
    risk_note    = form_data.get("risk_note") or ""
    total_quota  = form_data.get("total_quota", 0)
    skills       = form_data.get("required_skills") or ""
    start_time   = form_data.get("start_time") or ""
    end_time     = form_data.get("end_time") or ""

    query_for_rules = (
        f"{title} {description} "
        f"{'户外' if outdoor else ''} {'未成年人' if minors else ''} "
        f"{'专业技能' if professional else ''} 风险 规范"
    )
    retrieved = _retrieve(query_for_rules, top_k=4)

    if retrieved:
        context_parts = [
            f"【参考】{r['file']} - {r['title']}\n{r['content'][:300]}"
            for r in retrieved
        ]
        context = "\n\n".join(context_parts)
    else:
        context = "（未检索到特定规则，请结合平台通用规范判断）"

    system_prompt = (
        "你是活动发布风控预检助手。请根据活动表单内容和检索到的规则知识，对活动发布内容进行诊断。\n\n"
        "你必须严格输出 JSON，不要输出任何 Markdown 格式或其他文字。\n\n"
        "诊断维度：\n"
        "1. 标题是否清晰、是否含夸大或敏感内容\n"
        "2. 描述是否完整（是否说明活动内容、时间、地点、风险）\n"
        "3. 活动是否与志愿者年龄、技能、身体状况相适应\n"
        "4. 是否涉及未成年人、户外、高风险、专业服务等特殊场景\n"
        "5. 是否存在营利性、收费、隐私泄露、虚假宣传风险\n"
        "6. 是否建议补充安全保障、培训、保险或应急联系人\n\n"
        "JSON Schema：\n"
        "{\n"
        '  "overallScore": <0~100整数>,\n'
        '  "riskLevel": "<低|中|高>",\n'
        '  "canSubmit": <true|false>,\n'
        '  "summary": "<一句话总结>",\n'
        '  "risks": [\n'
        '    {\n'
        '      "level": "<低|中|高>",\n'
        '      "field": "<出问题的字段名>",\n'
        '      "reason": "<风险原因>",\n'
        '      "ruleReference": "<依据的规则来源>",\n'
        '      "suggestion": "<修改建议>"\n'
        '    }\n'
        '  ],\n'
        '  "suggestions": ["<建议1>", "<建议2>"]\n'
        "}\n"
        "注意：risks 最多 5 条，suggestions 最多 5 条，reason 每条不超过 50 字。"
    )

    user_prompt = (
        f"请对以下志愿活动进行诊断：\n\n"
        f"活动标题：{title}\n"
        f"活动描述：{description}\n"
        f"活动地点：{location or '未填写'}\n"
        f"所需技能：{skills or '未填写'}\n"
        f"总名额：{total_quota}\n"
        f"开始时间：{start_time or '未填写'}\n"
        f"结束时间：{end_time or '未填写'}\n"
        f"是否户外：{'是' if outdoor else '否'}\n"
        f"是否涉及未成年人：{'是' if minors else '否'}\n"
        f"是否需专业技能：{'是' if professional else '否'}\n"
        f"风险备注：{risk_note or '未填写'}\n\n"
        f"参考规则知识库：\n{context}\n\n"
        f"输出 JSON（只输出 JSON，不要其他文字）："
    )

    raw = _call_deepseek(system_prompt, user_prompt, timeout=30)

    if raw:
        try:
            result = json.loads(raw)
            return {
                "overallScore": result.get("overallScore", 60),
                "riskLevel": result.get("riskLevel", "中"),
                "canSubmit": result.get("canSubmit", True),
                "summary": result.get("summary", "请参考以下诊断建议"),
                "risks": result.get("risks") or [],
                "suggestions": result.get("suggestions") or []
            }
        except json.JSONDecodeError:
            parsed = _extract_json(raw)
            if parsed:
                return {
                    "overallScore": parsed.get("overallScore", 60),
                    "riskLevel": parsed.get("riskLevel", "中"),
                    "canSubmit": parsed.get("canSubmit", True),
                    "summary": parsed.get("summary", "请参考以下诊断建议"),
                    "risks": parsed.get("risks") or [],
                    "suggestions": parsed.get("suggestions") or []
                }

    return _local_diagnose(form_data, retrieved)


def _local_diagnose(form_data: Dict[str, Any], retrieved: List[Dict]) -> Dict[str, Any]:
    """本地规则引擎降级诊断（当 DeepSeek 不可用时）"""
    risks = []
    title        = form_data.get("title") or ""
    description  = form_data.get("description") or ""
    location     = form_data.get("location") or ""
    outdoor      = form_data.get("outdoor", False)
    minors       = form_data.get("involves_minors", False)
    professional = form_data.get("requires_professional_skill", False)
    risk_note    = form_data.get("risk_note") or ""

    if len(title) < 5:
        risks.append({"level": "中", "field": "title",
                      "reason": "活动标题过短，可能信息量不足",
                      "ruleReference": "activity_platform_rules.md - 活动标题规范",
                      "suggestion": "建议将标题补充至 10-30 字"})
    elif len(title) > 50:
        risks.append({"level": "低", "field": "title",
                      "reason": "活动标题过长，建议精简",
                      "ruleReference": "activity_platform_rules.md - 活动标题规范",
                      "suggestion": "将标题控制在 30 字以内"})

    if len(description) < 50:
        risks.append({"level": "中", "field": "description",
                      "reason": "活动描述过于简单，内容不完整",
                      "ruleReference": "activity_platform_rules.md - 活动描述规范",
                      "suggestion": "请补充活动背景、具体安排、志愿者职责等信息"})

    if not location and outdoor:
        risks.append({"level": "中", "field": "location",
                      "reason": "户外活动未填写具体活动地点",
                      "ruleReference": "activity_safety.md - 户外活动安全要求",
                      "suggestion": "户外活动必须明确活动地点和集合方式"})

    keywords_risk = ["户外", "徒步", "爬山", "急救", "医疗", "义诊", "高空", "水上"]
    has_risk_type = any(k in title or k in description for k in keywords_risk)
    if has_risk_type and not risk_note and not description:
        risks.append({"level": "中", "field": "risk_note",
                      "reason": "涉及风险类型的活动，但未填写风险说明",
                      "ruleReference": "activity_legal.md - 安全保障要求",
                      "suggestion": "请补充活动可能存在的风险及安全保障措施"})

    if minors:
        risks.append({"level": "中", "field": "involves_minors",
                      "reason": "涉及未成年人参与的活动，需要额外保护措施",
                      "ruleReference": "activity_safety.md - 涉及未成年人的活动要求",
                      "suggestion": "请补充监护人同意、成人陪同比例、安全保障等内容"})
        if "同意" not in description and "监护人" not in description:
            risks.append({"level": "高", "field": "description",
                          "reason": "涉及未成年人但未说明监护人知情同意安排",
                          "ruleReference": "activity_legal.md - 安全保障要求",
                          "suggestion": "必须说明取得监护人知情同意的具体方式"})

    if professional:
        risks.append({"level": "中", "field": "requires_professional_skill",
                      "reason": "涉及专业技能的活动，需要明确资质要求",
                      "ruleReference": "activity_legal.md - 培训与专业资质要求",
                      "suggestion": "请在描述中明确志愿者须具备的职业资格或培训经历"})

    if outdoor:
        if not description or ("地点" not in description and "集合" not in description):
            risks.append({"level": "中", "field": "description",
                          "reason": "户外活动未说明集合地点和路线安排",
                          "ruleReference": "activity_safety.md - 户外活动安全要求",
                          "suggestion": "请补充活动地点、集合方式、安全负责人联系方式"})
        if not risk_note and not description:
            risks.append({"level": "中", "field": "risk_note",
                          "reason": "户外活动缺少安全风险提示",
                          "ruleReference": "activity_safety.md - 户外活动安全要求",
                          "suggestion": "请补充天气风险、应急联系人等安全提示"})

    if not form_data.get("start_time") or not form_data.get("end_time"):
        risks.append({"level": "低", "field": "time",
                      "reason": "活动时间未填写完整",
                      "ruleReference": "activity_platform_rules.md - 活动时间规范",
                      "suggestion": "请明确填写活动开始和结束时间"})

    score       = 100 - len(risks) * 15
    high_risk   = any(r["level"] == "高" for r in risks)
    medium_risk = any(r["level"] == "中" for r in risks)

    if high_risk:
        risk_level = "高"; can_submit = False
    elif medium_risk:
        risk_level = "中"; can_submit = True
    else:
        risk_level = "低"; can_submit = True

    suggestions = list(dict.fromkeys([r["suggestion"] for r in risks if r["suggestion"]]))

    return {
        "overallScore": max(0, score),
        "riskLevel": risk_level,
        "canSubmit": can_submit,
        "summary": f"诊断完成，发现 {len(risks)} 个合规风险点，建议{'修改后提交' if can_submit else '修改后再提交'}",
        "risks": risks,
        "suggestions": suggestions[:5]
    }


def generate_activity(intent: str, activity_type: str = "") -> Dict[str, Any]:
    """
    根据组织者的一句话意图，生成完整的活动发布草稿。
    """
    query = f"{intent} {activity_type} 志愿活动模板"
    retrieved = _retrieve(query, top_k=3)

    if retrieved:
        context_parts = [
            f"【参考模板】{r['file']} - {r['title']}\n{r['content'][:400]}"
            for r in retrieved
        ]
        context = "\n\n".join(context_parts)
    else:
        context = "（未找到相关模板，请根据常识生成活动草稿）"

    system_prompt = (
        "你是志愿活动策划助手，根据组织者提供的简短意图，生成一个完整的活动发布草稿。\n\n"
        "约束：\n"
        "1. 生成的内容须符合平台活动发布规范，合法合规。\n"
        "2. 活动为纯公益性质，不含任何商业推销内容。\n"
        "3. 包含活动背景、具体安排、志愿者职责、安全注意事项。\n"
        "4. 语言面向志愿者，清晰可执行。\n\n"
        "严格输出 JSON（只输出 JSON，不要其他文字）：\n"
        "{\n"
        '  "title": "<活动标题，10-20字，简洁明确>",\n'
        '  "description": "<完整活动描述，包含背景、内容、时间安排、志愿者要求、注意事项>",\n'
        '  "requiredSkills": "<所需技能，逗号分隔，如无可不填>",\n'
        '  "safetyNote": "<安全注意事项和风险提示>",\n'
        '  "tips": "<给组织者的发布建议，2-3条>"\n'
        "}"
    )

    user_prompt = (
        f"请根据以下意图生成活动发布草稿：\n\n"
        f"组织者意图：{intent}\n"
        f"活动类型：{activity_type or '通用类型'}\n\n"
        f"参考模板：\n{context}\n\n"
        f"输出 JSON（只输出 JSON）："
    )

    raw = _call_deepseek(system_prompt, user_prompt, timeout=30)

    if not raw:
        return {
            "title": f"【待完善】{intent}",
            "description": "AI 服务暂时不可用，请参考平台活动发布规范手动填写。",
            "requiredSkills": "", "safetyNote": "", "tips": "请参考知识库中的活动模板进行完善"
        }

    try:
        result = json.loads(raw)
        return {
            "title": result.get("title") or intent,
            "description": result.get("description") or "",
            "requiredSkills": result.get("requiredSkills") or "",
            "safetyNote": result.get("safetyNote") or "",
            "tips": result.get("tips") or ""
        }
    except json.JSONDecodeError:
        parsed = _extract_json(raw)
        if parsed:
            return {
                "title": parsed.get("title") or intent,
                "description": parsed.get("description") or "",
                "requiredSkills": parsed.get("requiredSkills") or "",
                "safetyNote": parsed.get("safetyNote") or "",
                "tips": parsed.get("tips") or ""
            }

    return {
        "title": f"【待完善】{intent}",
        "description": "AI 生成格式异常，请参考平台规范手动填写。",
        "requiredSkills": "", "safetyNote": "", "tips": "请参考知识库中的活动模板"
    }
