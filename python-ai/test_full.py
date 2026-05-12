"""Full integration test for Smart Volunteer 2.0 - Correct Request Formats"""
import requests
import json

BASE = "http://localhost:9094"

def test(name, method="get", path="/health", data=None, params=None):
    url = f"{BASE}{path}"
    try:
        if method == "get":
            r = requests.get(url, params=params, timeout=10)
        else:
            r = requests.post(url, json=data, timeout=30)
        try:
            result = r.json()
        except:
            result = r.text[:200]
        print(f"[{'OK' if r.status_code == 200 else 'FAIL'}] {name} | {r.status_code}")
        print(f"    -> {json.dumps(result, ensure_ascii=False)[:300]}")
        return r.status_code == 200, result
    except Exception as e:
        print(f"[FAIL] {name} | Error: {e}")
        return False, None

print("=" * 60)
print("Smart Volunteer 2.0 - Full Integration Test v2")
print("=" * 60)

print("\n=== Phase 1: Basic Services ===")
test("Health Check", path="/health")

print("\n=== Phase 2: Python AI Endpoints ===")

# KNN - requires requiredSkills (capital S), volunteers (not candidates)
# VolunteerProfile: userId, realName, skills, total_hours, credit_balance, attendance_rate
test("KNN Match", "post", "/ml/knn", {
    "requiredSkills": ["急救", "医疗"],
    "topK": 3,
    "volunteers": [
        {"userId": 1, "realName": "张三", "skills": "急救,医疗", "total_hours": 100, "credit_balance": 500, "attendance_rate": 1.0},
        {"userId": 2, "realName": "李四", "skills": "教育", "total_hours": 30, "credit_balance": 200, "attendance_rate": 0.6},
        {"userId": 3, "realName": "王五", "skills": "急救", "total_hours": 20, "credit_balance": 100, "attendance_rate": 0.8}
    ]
})

# Churn - requires volunteers (List of VolunteerStats), NOT individual fields
test("Churn Predict", "post", "/ml/churn", {
    "volunteers": [
        {"user_id": 1, "nickname": "张三", "signup_count": 10, "cancel_count": 3, "total_hours": 80, "inactive_days": 45},
        {"user_id": 2, "nickname": "李四", "signup_count": 5, "cancel_count": 0, "total_hours": 30, "inactive_days": 7}
    ]
})

# Organizer Audit - requires user_id, username, apply_reason, etc.
# Optional: nickname, skills, credit_balance, total_hours, total_activities, signup_count, cancel_count
test("Organizer Audit", "post", "/ai/audit/organizer", {
    "user_id": 1,
    "username": "testuser",
    "apply_reason": "希望组织更多社区志愿服务活动",
    "total_hours": 50,
    "total_activities": 5,
    "signup_count": 10,
    "cancel_count": 1,
    "skills": "急救,医疗",
    "credit_balance": 500
})

# Activity Audit - requires activity_id, title, description
test("Activity Audit", "post", "/ai/audit/activity", {
    "activity_id": 1,
    "title": "社区环保清洁志愿活动",
    "description": "组织志愿者清理社区公园垃圾，保护环境，共建美好家园"
})

# Hybrid Recommend - requires user_id, required_skills, candidates, activity_title
# candidates can be dict or VolunteerProfile
test("Hybrid Recommend", "post", "/ai/recommend", {
    "user_id": 1,
    "required_skills": ["急救", "医疗"],
    "activity_title": "社区健康义诊活动",
    "activity_description": "为社区老人提供免费健康检查和咨询服务",
    "candidates": [
        {"user_id": 1, "realName": "张三", "skills": "急救,医疗", "total_hours": 100, "cancel_count": 0, "attendance_rate": 1.0},
        {"user_id": 2, "realName": "李四", "skills": "教育", "total_hours": 30, "cancel_count": 2, "attendance_rate": 0.6},
        {"userId": 3, "realName": "王五", "skills": "急救", "total_hours": 20, "cancel_count": 1, "attendance_rate": 0.8}
    ],
    "top_k": 3
})

print("\n" + "=" * 60)
print("All tests completed!")
print("=" * 60)
