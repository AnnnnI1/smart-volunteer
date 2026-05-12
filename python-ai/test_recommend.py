import requests
import json

url = "http://localhost:9094/ai/recommend"
data = {
    "user_id": 1,
    "user_skills": ["急救"],
    "user_profile": "有爱心",
    "required_skills": ["急救", "医疗"],
    "activity_title": "社区义诊",
    "activity_description": "为社区老人提供基础健康检查",
    "candidates": [
        {"user_id": 101, "realName": "张三", "skills": "急救,医疗", "total_hours": 100, "credit_balance": 500},
        {"user_id": 102, "realName": "李四", "skills": "教育", "total_hours": 50, "credit_balance": 200},
        {"user_id": 103, "realName": "王五", "skills": "急救", "total_hours": 80, "credit_balance": 300}
    ],
    "top_k": 3
}

print("=== 智能推荐测试 ===")
r = requests.post(url, json=data)
print(json.dumps(r.json(), indent=2, ensure_ascii=False))
