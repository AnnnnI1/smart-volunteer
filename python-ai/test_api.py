import requests
import json

base_url = "http://localhost:9094"

# 1. 测试健康检查
print("=== 1. 健康检查 ===")
r = requests.get(f"{base_url}/health")
print(json.dumps(r.json(), indent=2, ensure_ascii=False))

# 2. 测试 KNN 志愿者匹配
print("\n=== 2. KNN 志愿者匹配 ===")
knn_data = {
    "requiredSkills": ["急救", "医疗"],
    "topK": 3,
    "volunteers": [
        {"userId": 1, "realName": "张三", "skills": "急救,医疗", "total_hours": 100, "credit_balance": 500, "attendance_rate": 0.9},
        {"userId": 2, "realName": "李四", "skills": "教育,辅导", "total_hours": 50, "credit_balance": 200, "attendance_rate": 0.8},
        {"userId": 3, "realName": "王五", "skills": "急救,交通引导", "total_hours": 80, "credit_balance": 300, "attendance_rate": 0.85}
    ]
}
r = requests.post(f"{base_url}/ml/knn", json=knn_data)
print(json.dumps(r.json(), indent=2, ensure_ascii=False))

# 3. 测试 Churn 流失预警
print("\n=== 3. Churn 流失预警 ===")
churn_data = {
    "volunteers": [
        {"user_id": 1, "nickname": "张三", "signup_count": 10, "cancel_count": 1, "total_hours": 100, "inactive_days": 30},
        {"user_id": 2, "nickname": "李四", "signup_count": 5, "cancel_count": 3, "total_hours": 20, "inactive_days": 60}
    ]
}
r = requests.post(f"{base_url}/ml/churn", json=churn_data)
print(json.dumps(r.json(), indent=2, ensure_ascii=False))

# 4. 测试组织者审核
print("\n=== 4. 组织者审核 ===")
organizer_data = {
    "user_id": 1,
    "username": "testuser",
    "nickname": "张三",
    "apply_reason": "希望组织更多公益活动，帮助社区发展",
    "total_hours": 100,
    "total_activities": 20,
    "signup_count": 50,
    "cancel_count": 2,
    "skills": "急救,医疗",
    "credit_balance": 500
}
r = requests.post(f"{base_url}/ai/audit/organizer", json=organizer_data)
print(json.dumps(r.json(), indent=2, ensure_ascii=False))

# 5. 测试活动审核
print("\n=== 5. 活动审核 ===")
activity_data = {
    "activity_id": 1,
    "title": "社区志愿服务",
    "description": "组织社区居民参与环境清洁活动",
    "organizer_name": "张三"
}
r = requests.post(f"{base_url}/ai/audit/activity", json=activity_data)
print(json.dumps(r.json(), indent=2, ensure_ascii=False))

print("\n=== 所有测试完成 ===")
