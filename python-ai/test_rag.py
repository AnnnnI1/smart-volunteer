"""
RAG 服务 NearestNeighbors 集成测试
测试向量模型加载、索引构建与检索
"""
import sys
import os

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

print("=" * 60)
print("RAG NearestNeighbors 集成测试")
print("=" * 60)

# 1. 测试依赖导入
print("\n[1] 测试依赖导入...")
try:
    import numpy as np
    print(f"    numpy {np.__version__}  OK")
except ImportError as e:
    print(f"    numpy 导入失败: {e}")
    sys.exit(1)

try:
    import sklearn
    from sklearn.neighbors import NearestNeighbors
    print(f"    scikit-learn {sklearn.__version__}  OK")
except ImportError as e:
    print(f"    scikit-learn 导入失败: {e}")
    sys.exit(1)

try:
    import sentence_transformers
    print(f"    sentence-transformers OK")
except ImportError as e:
    print(f"    sentence-transformers 导入失败: {e}")
    sys.exit(1)

try:
    import httpx
    print(f"    httpx OK")
except ImportError as e:
    print(f"    httpx 导入失败: {e}")
    sys.exit(1)

# 2. 测试 rag_service 模块导入
print("\n[2] 测试 rag_service 模块导入...")
try:
    import rag_service
    print("    rag_service 导入 OK")
except ImportError as e:
    print(f"    rag_service 导入失败: {e}")
    sys.exit(1)

# 3. 测试 Transformer 模型加载
print("\n[3] 测试 sentence-transformers 模型加载...")
try:
    model = rag_service._get_transformer()
    vec = model.encode(["你好，志愿者活动"], normalize_embeddings=True)
    print(f"    模型加载 OK，向量维度: {vec.shape[1]}")
except Exception as e:
    print(f"    模型加载失败: {e}")
    sys.exit(1)

# 4. 测试知识库文件加载
print("\n[4] 测试知识库文件加载...")
chunks = rag_service._load_markdown_files()
print(f"    共加载 {len(chunks)} 个 chunks")
for c in chunks[:3]:
    print(f"    - [{c['file']}] {c['title']} (正文 {len(c['text'])} 字)")

# 5. 测试向量索引构建与持久化
print("\n[5] 测试向量索引构建与持久化...")
try:
    result = rag_service.build_index()
    print(f"    build_index() 返回: {result}")
    if result.get("status") == "success":
        print(f"    索引构建 OK，共 {result.get('chunks')} 个 chunks")
    elif result.get("status") == "skipped":
        print(f"    索引构建跳过（知识库为空）: {result.get('message')}")
    else:
        print(f"    索引构建出错: {result.get('message')}")
except Exception as e:
    print(f"    索引构建失败: {e}")
    import traceback
    traceback.print_exc()
    sys.exit(1)

# 6. 测试向量检索
print("\n[6] 测试向量检索...")
test_queries = [
    "户外活动安全注意事项",
    "未成年人参与志愿活动的要求",
    "活动发布标题规范",
]
for q in test_queries:
    try:
        results = rag_service._retrieve(q, top_k=2)
        print(f"\n    查询: {q}")
        print(f"    召回 {len(results)} 条:")
        for r in results:
            print(f"      - [{r.get('file')}] {r.get('title')} (distance={r.get('distance', 0):.4f})")
    except Exception as e:
        print(f"    检索失败: {e}")

# 7. 测试持久化（重新加载）
print("\n[7] 测试索引持久化与重新加载...")
if os.path.exists(rag_service.VECTORS_PATH) and os.path.exists(rag_service.META_PATH):
    try:
        vectors = np.load(rag_service.VECTORS_PATH)
        with open(rag_service.META_PATH, "r", encoding="utf-8") as f:
            meta = json.load(f) if False else __import__("json").load(f)
        print(f"    向量文件: {rag_service.VECTORS_PATH}  大小: {vectors.shape}")
        print(f"    元数据文件: {rag_service.META_PATH}  条目: {len(meta)}")
    except Exception as e:
        print(f"    持久化文件读取失败: {e}")
else:
    print("    持久化文件不存在（首次构建）")

# 8. 测试 RAG chat（依赖 DeepSeek API）
print("\n[8] 测试 RAG chat（依赖 DeepSeek API）...")
try:
    answer = rag_service.chat("组织一次户外徒步活动需要注意什么？")
    print(f"    回答长度: {len(answer.get('answer', ''))} 字")
    print(f"    来源数量: {len(answer.get('sources', []))}")
    if answer.get("sources"):
        for s in answer["sources"]:
            print(f"      - {s.get('title')} (distance={s.get('distance', 0):.4f})")
except Exception as e:
    print(f"    chat 调用失败: {e}")

# 9. 测试 RAG 诊断（本地规则引擎，不依赖 DeepSeek）
print("\n[9] 测试 RAG 诊断（本地规则引擎，不依赖 DeepSeek）...")
try:
    diag_data = {
        "title": "社区环境清洁志愿活动",
        "description": "组织志愿者在社区公园进行环境清洁，捡拾垃圾。",
        "location": "XX 社区公园",
        "outdoor": True,
        "involves_minors": False,
        "requires_professional_skill": False,
        "risk_note": "无特殊风险",
        "total_quota": 20,
        "required_skills": "无",
        "start_time": "2025-01-01 09:00",
        "end_time": "2025-01-01 12:00",
    }
    result = rag_service.diagnose_activity(diag_data)
    print(f"    风险评分: {result.get('overallScore')}")
    print(f"    风险等级: {result.get('riskLevel')}")
    print(f"    可提交: {result.get('canSubmit')}")
    print(f"    摘要: {result.get('summary')}")
    print(f"    风险点数量: {len(result.get('risks', []))}")
    if result.get("risks"):
        for risk in result["risks"]:
            print(f"      - [{risk['level']}] {risk['field']}: {risk['reason']}")
except Exception as e:
    print(f"    diagnose 调用失败: {e}")

# 10. 测试未成年人高风险场景
print("\n[10] 测试未成年人高风险场景（本地诊断）...")
try:
    diag_data2 = {
        "title": "儿童陪伴",
        "description": "志愿者陪小朋友玩游戏",
        "location": "",
        "outdoor": False,
        "involves_minors": True,
        "requires_professional_skill": False,
        "risk_note": "",
        "total_quota": 5,
        "start_time": "",
        "end_time": "",
    }
    result2 = rag_service.diagnose_activity(diag_data2)
    print(f"    风险评分: {result2.get('overallScore')}")
    print(f"    风险等级: {result2.get('riskLevel')}")
    print(f"    可提交: {result2.get('canSubmit')}")
    print(f"    风险点数量: {len(result2.get('risks', []))}")
    for risk in result2.get("risks", []):
        print(f"      - [{risk['level']}] {risk['field']}: {risk['reason']}")
except Exception as e:
    print(f"    诊断失败: {e}")

print("\n" + "=" * 60)
print("测试完成")
print("=" * 60)
