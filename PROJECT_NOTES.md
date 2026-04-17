# Smart Volunteer 项目技术文档

> 最后更新：2026-04-11（本次：动态签到码防作弊机制 + 登录页 UI 重设计）

---

## 目录

1. [项目概述](#1-项目概述)
2. [整体架构](#2-整体架构)
3. [微服务详解](#3-微服务详解)
   - [Gateway（9090）](#31-gateway9090)
   - [User Service（9091）](#32-user-service9091)
   - [Activity Service（9092）](#33-activity-service9092)
   - [AI Service（9093）](#34-ai-service9093)
   - [Credit Service（9095）](#35-credit-service9095)
   - [Python AI（9094）](#36-python-ai9094)
4. [前端架构](#4-前端架构)
5. [数据库设计](#5-数据库设计)
6. [角色与权限（RBAC）](#6-角色与权限rbac)
7. [核心业务流程](#7-核心业务流程)
8. [本次改动记录](#8-本次改动记录)
9. [环境与启动方式](#9-环境与启动方式)
10. [测试账号](#10-测试账号)

---

## 1. 项目概述

**Smart Volunteer** 是一个面向高校/社区的智能志愿服务管理平台，核心目标是：

- 志愿者可以浏览、报名、签到活动，累积积分
- 组织者发布活动，管理报名名单，手动/扫码签到
- 管理员审批组织者资格、风控活动内容、数据查询
- AI 能力贯穿全链路：组织者资质尽调、活动内容风控、志愿者推荐匹配、NL2SQL 自然语言查询

**技术栈**：

| 层级 | 技术 |
|------|------|
| 前端 | Vue 3 + Vite + Element Plus + Pinia |
| 后端 | Spring Boot 3 + MyBatis-Plus + Spring Cloud Nacos |
| AI 服务 | Python FastAPI + sentence-transformers + DeepSeek API |
| 数据库 | MySQL 8 + Redis 7 |
| 消息队列 | RocketMQ 4.9.8 |
| 注册中心 | Nacos 2.4.3 |

---

## 2. 整体架构

```
浏览器（Vue 3 前端 :5174）
        │  HTTP /api/...
        ▼
┌─────────────────────────────────┐
│  Gateway  :9090                 │
│  - JWT 解析 → X-User-Id/Role    │
│  - CORS 白名单（5173/5174）     │
│  - 路由转发                     │
└──────┬──────────────────────────┘
       │
  ┌────┴────────────────────────────────────┐
  │  Nacos 注册中心 :8848                   │
  └────┬──────┬──────┬──────┬──────────────┘
       │      │      │      │
  :9091  :9092  :9093  :9095    :9094（Python，不注册 Nacos）
  User  Act   AI   Credit   Python AI
   │      │      │
   └──────┴──────┘
        RocketMQ :9876
        (activity-checkin-topic, activity-complete-topic)
```

**请求路由规则**：

| 前缀 | 目标服务 | 端口 |
|------|---------|------|
| `/user/**` | User Service | 9091 |
| `/activity/**` | Activity Service | 9092 |
| `/ai/**` | AI Service | 9093 |
| `/credit/**` | Credit Service | 9095 |

---

## 3. 微服务详解

### 3.1 Gateway（9090）

**核心过滤器**：`AuthGlobalFilter.java`

- 白名单（不校验 Token）：`/user/login`、`/user/register`
- 从 `Authorization: Bearer {jwt}` 中解析 JWT
- 将 `userId` 和 `role` 分别注入下游请求头 `X-User-Id` / `X-User-Role`
- 校验失败直接返回 401

```yaml
# application.yml - CORS 配置同时允许两个前端端口
allowedOrigins:
  - http://localhost:5173
  - http://localhost:5174
```

---

### 3.2 User Service（9091）

**模块路径**：`smart-volunteer-user`

#### 主要实体

| 表 | 实体类 | 说明 |
|----|--------|------|
| `users` | `User.java` | 用户基本信息，含 `role`/`status`/`applyOrganizer`/`auditStatus`/`aiAuditReport` |
| `vol_profile` | `VolProfile.java` | 个人档案，含 `skills`（逗号分隔）、`totalHours`、`realName`、`bio` |

#### API 端点

```
POST  /user/login                      登录，返回 JWT + UserInfoVo
POST  /user/register                   注册（默认 role=1 志愿者）
GET   /user/me                         获取当前用户信息
PUT   /user/update-info                修改昵称/邮箱/手机号
PUT   /user/change-password            修改密码（需验证旧密码）
POST  /user/upload-avatar              上传头像（Aliyun OSS）
GET   /user/profile                    获取我的档案
PUT   /user/profile                    更新我的档案（含技能）
GET   /user/profile/{userId}           查看他人档案（公开）
POST  /user/apply-organizer            志愿者申请成为组织者（触发 AI 尽调）
GET   /user/admin/list                 管理员：查询用户列表
PUT   /user/admin/{id}/role            管理员：升/降级用户角色
POST  /user/admin/{id}/audit-organizer 管理员：审批组织者申请
```

#### 组织者申请 AI 尽调流程

```
志愿者提交申请（含申请理由）
    │
    ▼
UserServiceImpl.applyOrganizer()
    │  查询: totalHours, signupCount, cancelCount, creditBalance, skills
    │
    ▼
callAiOrganizerAudit() ──HTTP POST──► Python :9094/ai/audit/organizer
    │                                    │
    │  ◄─── JSON { code:200, data:{       │
    │          dimensions_analysis:{      │
    │            content_compliance,      │  DeepSeek 三维度评分
    │            history_fulfillment,     │
    │            qualification_assessment │
    │          },                         │
    │          overall_risk_level,        │
    │          ai_conclusion              │
    │       }}                            │
    │
    ▼
user.setAiAuditReport(json)
user.setAuditStatus(0)  ← 待审核
user.setApplyOrganizer(1)
userMapper.updateById(user)
```

#### VO 说明

- **`UserInfoVo`**：登录返回给前端（不含敏感字段）
- **`UserAdminVo`**：管理员列表 VO，含统计字段：
  ```java
  // 申请相关
  String applyReason;
  Integer auditStatus;     // 0=待审 1=已通过 2=已驳回
  String aiAuditReport;   // JSON 字符串，含三维度评分
  // 统计
  Integer creditBalance;
  Integer signupCount;
  Integer cancelCount;
  Integer totalHours;
  ```

---

### 3.3 Activity Service（9092）

**模块路径**：`smart-volunteer-activity`

#### 主要实体

| 表 | 实体类 | 说明 |
|----|--------|------|
| `vol_activity` | `VolActivity.java` | 活动，含 `organizerId`/`requiredSkills`/`auditStatus` |
| `vol_registration` | `VolRegistration.java` | 报名记录，含 `checkinCode`（SHA-256 个人签到码）|
| `vol_checkin_code` | `VolCheckinCode.java` | 活动管理员生成的6位签到码 |
| `vol_local_message` | `VolLocalMessage.java` | 本地消息表（补偿任务） |
| `vol_activity_audit_log` | `VolActivityAuditLog.java` | AI 审核日志 |

#### 活动状态流转

```
0（未开始）→ 1（报名中）→ 2（进行中）→ 3（已结束）
              ↑ 仅此阶段可报名/取消
```

#### 报名状态

| status | 含义 |
|--------|------|
| 0 | 已报名（正常） |
| 1 | 已取消 |
| 2 | 已签到 ✓ |
| 4 | 缺席（活动结束时自动标记 status=0→4） |

#### 高并发报名机制

```
前端点击"立即报名"
    │
    ▼
RegistrationController.register()
    │
    ├─①─ Redis DECR activity:quota:{id}  ← 原子扣减
    │       若结果 < 0，INCR 回退，返回"名额已满"
    │
    ├─②─ INSERT vol_registration（唯一索引兜底）
    │       若已存在则回滚，返回"已报名"
    │
    └─③─ INSERT vol_local_message（异步事件）
              │
              ▼（每3秒 @Scheduled 补偿任务扫描）
         发送 RocketMQ 消息 → Credit Service
```

#### 签到码体系（反作弊）

**个人专属签到码**（注册时生成，存 `vol_registration.checkin_code`）：

```java
// RegistrationServiceImpl.java
String raw = userId + ":" + activityId + ":" + System.nanoTime();
String code = DigestUtils.sha256Hex(raw).substring(0, 10).toUpperCase();
```

**活动公共签到码**（管理员生成，存 `vol_checkin_code`）：

- 6位随机数字，活动维度唯一
- 工作人员在现场出示，志愿者扫码/输入

**签到接口**：

```
POST /activity/{id}/checkin-code          生成公共签到码（管理员/组织者）
POST /activity/{id}/checkin               志愿者提交签到（校验公共码）
POST /activity/checkin-by-code            志愿者用个人码签到（前端实际使用）
POST /activity/{id}/manual-checkin/{uid}  手动签到（管理员/组织者）
```

#### 发布活动 AI 风控

```java
// ActivityServiceImpl.addActivity()
// 活动创建后同步调用 Python AI 风控
POST Python:9094/ai/audit/activity {
    activity_id, title, description, organizer_name
}
// 结果写入 vol_activity_audit_log
// passed=false 时活动 auditStatus=2（风控拦截）
```

---

### 3.4 AI Service（9093）

**模块路径**：`smart-volunteer-ai`

#### KNN 志愿者匹配

```
前端 POST /ai/knn/match { requiredSkills, topK }
    │
    ▼
KnnServiceImpl.matchVolunteers()
    ├─ 查 vol_profile（技能档案）
    ├─ 查 vol_credit_balance（积分）
    ├─ 查 vol_registration（出勤率）
    │
    ▼
POST Python:9094/ml/knn { requiredSkills, volunteers[], topK }
    │
    ▼ 返回排名列表，含 finalScore/matchedSkills
```

#### 活动推荐（志愿者用）

```
GET /ai/knn/recommend?topK=10
    │
    ▼
KnnServiceImpl.recommendActivities()
    ├─ 查当前用户技能
    ├─ 查所有 status IN(0,1) 且有 required_skills 的活动
    ├─ 把"活动所需技能"当作候选技能，复用 /ml/knn
    │
    ▼ 返回 ActivityRecommendVO[]（含 similarity、matchedSkills）
```

#### 双阶段推荐引擎（管理员用，为活动找最佳志愿者）

```
GET /ai/knn/hybrid-recommend?userId=&activityId=
    │
    ▼
KnnServiceImpl.hybridRecommend()
    ├─ 查活动详情 + 用户档案 + 所有候选志愿者
    │
    ▼
POST Python:9094/ai/recommend { user_skills, candidates[], activity_title, ... }
    │  Stage1: sentence-transformers 向量召回 Top-3
    │  Stage2: DeepSeek Agent 精排 → ai_reasoning
    ▼
返回 { candidates[], finalRecommendation.aiReasoning, stage }
```

#### Feed 流个性化推荐（志愿者「为我推荐」页面）

```
GET /ai/knn/feed?page=1&pageSize=6&statusFilter=
    │
    ▼
KnnServiceImpl.feedRecommend()
    ├─ 查 vol_profile（用户技能画像）
    ├─ 查 vol_registration + vol_activity（行为历史，最多20条）
    │    行为权重：completed(1.0) > checked_in(0.7) > registered(0.3)
    ├─ 查 vol_registration（已报名 ID 集合，用于过滤）
    ├─ 构建活动池（audit_status=1, status IN(0,1) 或指定，最多200条）
    │
    ▼
POST Python:9094/ai/feed {
    user_id, user_skills[], user_profile,
    history_activities[{title, description, behavior_type}],
    activity_pool[], page, page_size
}
    │  向量融合：behavior_vec×0.6 + profile_vec×0.4
    │  回退链：behavior+profile → profile_only → platform热词fallback
    │  余弦相似度全局排序 → 分页
    │  DeepSeek 批量生成 ≤30字 推荐语（每页一次 API 调用）
    ▼
返回 {
    total, has_more, items[{
        id, title, feed_score, ai_reason,
        required_skills, status, start_time,
        remain_quota, organizer_name
    }],
    vector_mode   // behavior+profile | profile_only | fallback
}
```

```
POST /ai/nl2sql/query { question: "查询本月报名人数最多的活动" }
    │
    ▼
DeepSeek API（含 5 张表 Schema 的 System Prompt）
    │  生成 SELECT SQL
    ▼
安全校验（仅允许 SELECT，追加 LIMIT 200）
    │
JdbcTemplate 执行
    ▼
返回 { sql, rows[], columns[] }
```

---

### 3.5 Credit Service（9095）

**模块路径**：`smart-volunteer-credit`

#### 积分规则

| 事件 | 积分 | type | 触发时机 |
|------|------|------|---------|
| 签到成功 | +10 | 5 | `activity-checkin-topic` MQ |
| 活动完成 | +50 | 2 | `activity-complete-topic` MQ，仅 status=2（已签到）者 |

#### 幂等保护

```java
// 唯一索引：(user_id, activity_id, type)
// 同一 userId+activityId+type 组合只处理一次
```

#### API 端点

```
GET  /credit/balance            查询当前用户积分余额
GET  /credit/records            查询积分流水（分页）
POST /credit/admin/adjust       管理员手动调整积分 { userId, delta, reason }
```

---

### 3.6 Python AI（9094）

**文件路径**：`D:/Project/smart-volunteer/python-ai/`

#### 启动命令

```bash
nohup /d/python/python.exe main.py > /tmp/python-ai.log 2>&1 &
```

#### 端点一览

| 路由 | 功能 | 调用方 |
|------|------|--------|
| `POST /ml/knn` | TF-IDF + 余弦相似度匹配 | Java AI Service |
| `POST /ml/churn` | 志愿者流失预测 | Java AI Service |
| `POST /ai/audit/organizer` | 组织者资质 AI 尽调（DeepSeek） | Java User Service |
| `POST /ai/audit/activity` | 活动内容风控（DeepSeek） | Java Activity Service |
| `POST /ai/recommend` | 双阶段推荐（Transformer + DeepSeek） | Java AI Service |
| `POST /ai/feed` | Feed流个性化推荐（向量融合 + DeepSeek批量推荐语） | Java AI Service |

#### KNN 评分维度（4维加权）

```python
final_score = (
    tfidf_cosine_similarity * 0.55    # TF-IDF 余弦相似度（主维度）
    + hours_bonus        * 0.10       # 服务时长奖励（max +0.10）
    + credit_bonus       * 0.15       # 积分信誉奖励（max +0.15）
    + attendance_bonus   * 0.20       # 出勤率奖励（max +0.20）
)
# 冷启动平滑：新志愿者使用 50% 全局均值 prior
```

#### 双阶段推荐引擎

```python
# recommend_service.py
async def hybrid_recommend(data):
    # Stage 1: sentence-transformers 向量召回
    model = SentenceTransformer('shibing624/text2vec-base-chinese')
    top3 = _vector_recall(candidates, required_skills, top_k=3)

    # Stage 2: DeepSeek Agent 精排
    ai_reasoning = _deepseek_rerank(top3, activity_info, user_profile)

    return { candidates: top3, final_recommendation: { ai_reasoning }, stage: 'hybrid' }

# 降级方案：当模型不可用时使用余弦相似度排序
def _fallback_ranking(candidates, required_skills):
    ...
```

---

## 4. 前端架构

**路径**：`smart-volunteer-frontend/`
**端口**：5174（或 5173）
**Vite Proxy**：`/api → http://localhost:9090`（自动去掉 `/api` 前缀）

### 页面清单

| 路由 | 文件 | 角色 | 说明 |
|------|------|------|------|
| `/login` | `Login.vue` | 所有 | 登录/注册 |
| `/homepage/dashboard` | `Dashboard.vue` | 所有 | 首页数据看板 |
| `/homepage/activities` | `ActivityList.vue` | 所有 | 活动大厅（筛选/搜索） |
| `/homepage/activity/:id` | `ActivityDetail.vue` | 所有 | 活动详情+报名+签到 |
| `/homepage/profile` | `Profile.vue` | 所有 | 个人中心（技能/积分/头像） |
| `/homepage/recommend` | `RecommendPage.vue` + `FeedCard.vue` | 志愿者 | AI Feed流个性化推荐（瀑布流+打字机效果） |
| `/homepage/admin/activities` | `ActivityManage.vue` | 组织者/管理员 | 我的活动管理（签到码/名单） |
| `/homepage/admin/users` | `UserManage.vue` | 管理员 | 用户管理+组织者审批 |
| `/homepage/admin/nl2sql` | `NL2Sql.vue` | 管理员 | 自然语言数据查询 |
| `/homepage/admin/knn` | `KnnMatch.vue` | 管理员 | KNN 志愿者匹配 |
| `/homepage/admin/risk` | `RiskPage.vue` | 管理员 | 流失风险预警 |
| `/homepage/admin/audit` | `ActivityAudit.vue` | 管理员 | 活动 AI 风控审核日志 |
| `/homepage/admin/ai` | `AdminAI.vue` | 管理员 | 综合 AI 能力仪表板 |

### API 文件

```
src/api/
├── user.js       # 用户相关（登录/注册/角色管理/组织者申请审批）
├── activity.js   # 活动 CRUD + 报名 + 签到
├── profile.js    # 个人档案
├── credit.js     # 积分余额/流水/调整
└── ai.js         # KNN/推荐/NL2SQL/风险/双阶段推荐/Feed流推荐
```

### 状态管理（Pinia）

```js
// src/utils/auth.js
const useAuthStore = defineStore('auth', {
  state: () => ({ userToken: '', userInfo: null }),
  // userInfo.id, userInfo.role, userInfo.nickname, ...
})
```

### 菜单权限

```
role=0（管理员）：全部菜单
role=1（志愿者）：首页 / 活动大厅 / 个人中心 / 为我推荐（Feed流）
role=2（组织者）：志愿者全部菜单 + 我的活动管理
```

---

## 5. 数据库设计

**库名**：`smart_volunteer`
**连接**：`userstest` / `123456`

### 核心表结构

#### `users`（用户表）

```sql
id              BIGINT PK AUTO_INCREMENT
username        VARCHAR(50) UNIQUE NOT NULL
nickname        VARCHAR(50)
password_hash   VARCHAR(64)              -- MD5 加盐
email           VARCHAR(100) UNIQUE
phone           VARCHAR(20) UNIQUE
avatar          VARCHAR(500)             -- OSS URL
role            TINYINT DEFAULT 1        -- 0管理员 1志愿者 2组织者
status          TINYINT DEFAULT 1        -- 1正常 0禁用
apply_organizer TINYINT DEFAULT 0        -- 1=申请中
apply_reason    TEXT
audit_status    TINYINT                  -- 0待审 1通过 2驳回
ai_audit_report TEXT                     -- JSON 三维度评分
created_at      DATETIME
```

#### `vol_activity`（活动表）

```sql
id              BIGINT PK AUTO_INCREMENT
title           VARCHAR(200) NOT NULL
description     TEXT
required_skills VARCHAR(500)             -- 逗号分隔
status          TINYINT DEFAULT 0        -- 0未开始 1报名中 2进行中 3已结束
audit_status    TINYINT DEFAULT 0        -- 0待审 1通过 2风控拦截
total_quota     INT
joined_quota    INT DEFAULT 0
organizer_id    BIGINT                   -- 创建者（组织者/管理员）
start_time      DATETIME
end_time        DATETIME
create_time     DATETIME
```

#### `vol_registration`（报名记录表）

```sql
id              BIGINT PK AUTO_INCREMENT
user_id         BIGINT NOT NULL
activity_id     BIGINT NOT NULL
status          TINYINT DEFAULT 0        -- 0已报名 1已取消 2已签到 4缺席
checkin_code    VARCHAR(20)              -- 个人专属签到码（SHA-256 前10位大写）
created_at      DATETIME
UNIQUE KEY uk_user_activity (user_id, activity_id)
```

#### `vol_checkin_code`（活动公共签到码）

```sql
id              BIGINT PK AUTO_INCREMENT
activity_id     BIGINT UNIQUE
code            VARCHAR(10)              -- 6位随机数字
is_active       TINYINT DEFAULT 1
create_time     DATETIME
```

#### `vol_credit_balance`（积分余额）

```sql
user_id         BIGINT PK
balance         INT DEFAULT 0
```

#### `vol_credit_record`（积分流水）

```sql
id              BIGINT PK AUTO_INCREMENT
user_id         BIGINT
activity_id     BIGINT
delta           INT                      -- 正/负
type            TINYINT                  -- 2=活动完成 5=签到
remark          VARCHAR(200)
created_at      DATETIME
UNIQUE KEY uk_user_act_type (user_id, activity_id, type)
```

#### `vol_activity_audit_log`（AI 风控日志）

```sql
id              BIGINT PK AUTO_INCREMENT
activity_id     BIGINT
audit_result    TEXT                     -- AI 返回完整 JSON
risk_tags       VARCHAR(500)             -- 逗号分隔风险标签
passed          TINYINT                  -- 1通过 0拦截
create_time     DATETIME
```

---

## 6. 角色与权限（RBAC）

### 角色定义

| role 值 | 角色 | 注册方式 |
|---------|------|---------|
| 0 | 管理员 | 数据库直接设置 |
| 1 | 志愿者 | 注册接口默认 |
| 2 | 组织者 | 管理员审批后升级 |

### 权限矩阵

| 操作 | 管理员(0) | 组织者(2) | 志愿者(1) |
|------|:---------:|:---------:|:---------:|
| 报名活动 | ✓ | ✓ | ✓ |
| 个人签到 | ✓ | ✓ | ✓ |
| 创建活动 | ✓ | ✓ | ✗ |
| 管理自己的活动 | ✓ | ✓（仅自己） | ✗ |
| 查看报名名单 | ✓ | ✓（仅自己） | ✗ |
| 手动签到 | ✓ | ✓（仅自己） | ✗ |
| 管理所有活动 | ✓ | ✗ | ✗ |
| 用户管理/升降级 | ✓ | ✗ | ✗ |
| AI 工具（NL2SQL/KNN等） | ✓ | ✗ | ✗ |

### JWT 流程

```
登录 → JwtUtil.createJWT(userId, Map.of("role", role))
      → Gateway 解析 → 注入 X-User-Id + X-User-Role
      → 下游服务直接读 Header，无需再查 DB
```

---

## 7. 核心业务流程

### 7.1 志愿者报名全流程

```
1. 浏览活动大厅（ActivityList.vue）
2. 点击活动 → ActivityDetail.vue
3. 点击"立即报名"→ ElMessageBox 确认弹窗
4. POST /activity/registration/{id}
   ├── Redis DECR quota（原子）
   ├── INSERT vol_registration（唯一索引兜底）
   └── INSERT vol_local_message（异步积分事件）
5. 报名成功，前端刷新页面显示"已报名"状态
```

### 7.2 签到流程（反作弊）

**志愿者端（ActivityDetail.vue）**：

```
活动进行中（status=2）+ 已报名（myReg.status=0）
    │
    ▼
展示个人专属签到码（myReg.checkinCode，36px 大字）
    │
志愿者向工作人员出示签到码
工作人员在 ActivityManage.vue 输入码
    │
    ▼（或志愿者点击"提交签到"按钮）
POST /activity/checkin-by-code { code: myReg.checkinCode }
    │
    ▼
签到成功 → ElMessage.success
2.5秒后查询积分变化 → ElNotification 展示积分到账通知
```

**管理员端（ActivityManage.vue）**：

```
生成6位公共签到码 → 展示给现场工作人员
手动签到：搜索用户 → POST /activity/{id}/manual-checkin/{userId}
```

### 7.3 积分结算流程

```
签到成功
    │
    ▼（@Scheduled 每3秒扫本地消息表）
RocketMQ: activity-checkin-topic
    │
    ▼
CreditConsumer.onCheckin()
    ├── 幂等校验：(userId, activityId, type=5) 是否已处理
    └── INSERT vol_credit_record + UPDATE vol_credit_balance += 10

活动状态改为"已结束"（status=0→3）
    │
    ▼
扫描所有 status=2（已签到）的报名记录
RocketMQ: activity-complete-topic
    │
    ▼
CreditConsumer.onComplete()
    ├── 幂等校验：(userId, activityId, type=2)
    └── INSERT vol_credit_record + UPDATE vol_credit_balance += 50
```

### 7.4 组织者申请审批流程

```
志愿者 → Profile.vue → 点击"申请成为组织者"→ 填写申请理由
    │
    ▼
POST /user/apply-organizer { applyReason }
    │
    ▼（UserServiceImpl.applyOrganizer）
查询用户历史数据（时长/报名/积分/技能）
    │
    ▼
POST Python:9094/ai/audit/organizer
    │  DeepSeek 三维度评分：内容合规/历史履约/资质评估
    ▼
user.aiAuditReport = JSON 报告
user.auditStatus = 0（待审核）
    │
    ▼（管理员 UserManage.vue → 待升级申请 Tab）
展开行查看 AI 尽调报告三维度进度条
    │
    ├─ 点击"✓ 通过" → user.role=2, auditStatus=1
    └─ 点击"✗ 驳回" → 弹窗填写原因 → auditStatus=2
```

---

## 8. 本次改动记录

> 本次会话（2026-04-11）完成了以下改动：动态签到码防作弊机制（60秒自动刷新）+ 登录页 UI 全面重设计（统一蓝色主题）。

---

### 改动 A：动态签到码防作弊机制

**背景**：原签到逻辑为"志愿者持个人码 → 组织者输入确认"，逻辑反向且未防作弊。改为"组织者展示60秒动态活动码 → 志愿者自行输入签到"。

#### A-1 数据库

```sql
ALTER TABLE vol_checkin_code ADD COLUMN expire_at DATETIME NOT NULL AFTER is_active;
```

#### A-2 后端：`VolCheckinCode.java`

新增字段：
```java
/** 签到码过期时间（60秒后过期） */
private LocalDateTime expireAt;
```

#### A-3 后端：`VolCheckinCodeMapper.java`

`selectActive` 查询增加过期时间过滤：
```java
@Select("SELECT * FROM vol_checkin_code WHERE activity_id=#{id} AND is_active=1 AND expire_at > NOW() LIMIT 1")
VolCheckinCode selectActive(@Param("id") Long activityId);
```

#### A-4 后端：`RegistrationServiceImpl.java`

`generateCheckinCode()` 方法更新：
- 生成6位随机数字码，设置 60秒 `expireAt`
- 返回 `{code, expireAt}` Map

`checkin()` 方法更新：
- 错误提示改为：`"签到码错误或已失效，请向组织者获取最新签到码"`

#### A-5 前端：`ActivityManage.vue`（组织者端）

签到码对话框重设计：
- 展示96px大字6位签到码，蓝底白字样式
- 60秒倒计时进度条（≤20秒变橙色，≤10秒变红色）
- 倒计时归零自动调用 `refreshCheckinCode()` 刷新
- "立即刷新"按钮手动触发
- `onUnmounted` 时 `clearInterval` 防内存泄漏

关键逻辑：
```js
const startCheckinTimer = () => {
  checkinTimer = setInterval(async () => {
    countdown.value--
    if (countdown.value <= 0) {
      await refreshCheckinCode()
    }
  }, 1000)
}
```

#### A-6 前端：`ActivityDetail.vue`（志愿者端）

签到区改为输入框模式：
- 活动进行中（status=2）且已报名（myReg.status=0）时显示6位签到码输入框
- 输入满6位后"提交签到"按钮激活
- 签到成功后2.5秒轮询积分变化，弹出积分到账通知

---

### 改动 B：登录页 UI 全面重设计

**文件**：`smart-volunteer-frontend/src/pages/Login.vue`

**设计理念**：参考 Notion / Linear / Vercel 等主流 SaaS 产品风格，无导航栏（登录页是转化漏斗，导航会分散注意力）。

#### 布局结构
- 左侧（52%）：深色品牌区 `#0a0f1e`
- 右侧（48%）：纯白表单区

#### 左侧视觉设计
- 3层径向光晕（蓝色系，`#409EFF` / `#2563eb` / `#60a5fa`）
- 细网格纹理遮罩（`40px × 40px`）
- 64px 渐变大标题"让公益更智能"（白→浅蓝→蓝）
- 数据统计卡片：10,000+ 注册志愿者 / 500+ 公益活动 / 98% 好评率
- 内容整体**水平居中**（`align-items: center; text-align: center`）

#### 右侧表单设计
- 50px 高输入框，圆角 12px
- 聚焦时蓝色描边 `#409EFF`
- 登录/注册按钮：蓝色 `#409EFF`，hover 阴影加深
- 移除所有第三方登录按钮（微信/QQ）
- 移除注册时的"组织者"角色选项

#### 颜色统一（绿→蓝）

| 位置 | 原色 | 新色 |
|------|------|------|
| 光晕 g1 | `rgba(22,163,74,0.35)` | `rgba(64,158,255,0.35)` |
| 光晕 g2 | `rgba(16,185,129,0.25)` | `rgba(37,99,235,0.25)` |
| 光晕 g3 | `rgba(52,211,153,0.12)` | `rgba(96,165,250,0.12)` |
| 标题渐变 | `#86efac → #4ade80` | `#93c5fd → #60a5fa` |
| 数字卡片 | `#4ade80` | `#60a5fa` |
| 输入框焦点 | `#16a34a` | `#409EFF` |
| 提交按钮 | `#16a34a` | `#409EFF` |
| 按钮阴影 | `rgba(22,163,74,...)` | `rgba(64,158,255,...)` |
| 切换链接 | `#16a34a` | `#409EFF` |
| SVG 描边 | `#16a34a` | `#409EFF` |

---

> 本次会话（2026-04-10）完成了以下改动，涵盖 Feed 流推荐全栈实现、AdminAI 重设计、DeepSeek 精排优化及演示数据补充。

---

### 改动 9：AdminAI.vue AI精推卡片重设计

**文件**：`smart-volunteer-frontend/src/pages/admin/AdminAI.vue`

**问题**：
1. AI精推只有首推候选人有"邀请"按钮，其他候选人卡片无法操作
2. AI只为首推生成分析，其他候选人没有推荐理由
3. 页面 3 列布局的中间列是 KNN人才Top5，与 AI精推功能重复

**改动内容**：
1. **删除** KNN人才Top5 卡片（中间列），改为 2 列布局（活动基本信息 + 报名转化分析）
2. AI精推卡片改为：所有候选人均渲染为独立卡片，每张卡片各自有"邀请"按钮
3. AI首推候选人用紫色 border + `AI首推` tag 高亮区分
4. 删除 `knnMatch` 导入、`knnRes` 并行请求、`knnList` 数据字段
5. 候选人列宽动态计算：`Math.floor(24 / Math.min(candidates.length, 5))`

---

### 改动 10：优化双阶段推荐 DeepSeek 精排权重

**文件**：`python-ai/recommend_service.py`

**问题**：Transformer 语义匹配度（similarity_score）与 DeepSeek 选人逻辑相互独立——DeepSeek 倾向于优先选服务时长长、积分高的候选人，即使其语义相似度远低于其他候选人，导致"匹配度高的不被推荐"。

**修复**：在 `_deepseek_rerank()` 的 system_prompt 中加入：
```
【评分权重说明】
- 语义匹配度（40%）：sentence-transformers 向量模型计算
- 服务时长（25%）
- 出勤率（20%）
- 积分（15%）
【选人规则】若语义匹配度明显领先（差距>10%），即使时长或积分略低，也应优先推荐匹配度高的人
```

同步将 `_call_deepseek` 默认超时从 20s 提升至 30s。

---

### 改动 11：Feed流个性化推荐全栈实现

**功能定位**：基于动态隐式反馈的 AI 个性化活动推荐 Feed 流（「为我推荐」页面）

**技术架构**：Transformer 语义向量推荐引擎（召回排序）+ DeepSeek LLM（推荐语生成）

#### Python AI（`python-ai/feed_service.py`，新建）

核心函数：
- `_build_user_vector()`：行为历史向量 × 0.6 + 技能画像向量 × 0.4 融合
  - 行为权重：completed(1.0) > checked_in(0.7) > registered(0.3)
- `_score_activities()`：活动池全量余弦相似度排序
- `_call_deepseek_batch()`：一次 API 调用为整页批量生成 ≤30字推荐语
- `feed_recommend()`：三级降级链：behavior+profile → profile_only → 平台热词fallback

`python-ai/main.py` 新增：
```python
class HistoryActivity(BaseModel):
    title: str = ""
    description: str = ""
    behavior_type: str = "registered"  # completed | checked_in | registered

class FeedRequest(BaseModel):
    user_id: int
    user_skills: List[str] = []
    user_profile: str = ""
    history_activities: List[HistoryActivity] = []
    activity_pool: List[Any]
    page: int = 1
    page_size: int = 6

POST /ai/feed  →  feed_recommend(request)
```

#### Java AI Service（`KnnServiceImpl.java`）

新增 `feedRecommend()` 方法：
1. 查 `vol_profile` → `skills`（作为画像文本）
2. 查 `vol_registration` JOIN `vol_activity` → 行为历史（含 behavior_type 判断逻辑，最多20条）
3. 查已报名活动 ID 集合（过滤用）
4. 构建活动池（`audit_status=1`，`status IN(0,1)` 或按 statusFilter，最多200条，LEFT JOIN users 取组织者名）
5. WebClient 调用 Python `/ai/feed`（超时 60s）
6. 透传 Python 返回结果

`KnnController.java` 新增端点：
```java
GET /ai/knn/feed?page=1&pageSize=6&statusFilter=
```

`KnnService.java` 新增接口方法：
```java
ResponseResult feedRecommend(Long userId, int page, int pageSize, Integer statusFilter);
```

#### 前端（新建 + 重写）

**`src/api/ai.js`** 新增：
```javascript
export function feedRecommend(page = 1, pageSize = 6, statusFilter = undefined) {
  const params = { page, pageSize }
  if (statusFilter !== undefined && statusFilter !== null) params.statusFilter = statusFilter
  return request({ url: '/ai/knn/feed', method: 'get', params })
}
```

**`src/pages/volunteer/FeedCard.vue`**（新建）：
- 展示：标题、状态 tag、技能 tags、日期/剩余名额、AI匹配进度条、推荐语
- 打字机效果：`setInterval` 逐字追加，`onMounted` + `watch` 触发

**`src/pages/volunteer/RecommendPage.vue`**（完整重写）：
- 2列瀑布流（leftCol = 偶数索引，rightCol = 奇数索引）
- `v-infinite-scroll="loadMore"` 触底加载
- 静默预加载：用户浏览第N页时后台预取第N+1页，触底时直接从 buffer 读取
- 筛选：全部 / 报名中(status=1) / 未开始(status=0)
- vector_mode badge 展示推荐模式：行为+画像向量 / 画像向量 / 热门推荐

---

### 改动 12：修复 Bug - vol_profile 表无 profile_text 列

**文件**：`smart-volunteer-ai/.../service/impl/KnnServiceImpl.java`

**问题**：`feedRecommend()` 初始实现查询 `SELECT skills, profile_text FROM vol_profile`，但 `vol_profile` 表实际只有 `skills`/`real_name`/`total_hours` 三列，`profile_text` 列不存在，导致 `BadSqlGrammarException` → 接口 500。

**修复**：改为只查 `skills`，以 skills 文本同时作为 user_profile 传给 Python：
```java
// 修复前
"SELECT skills, profile_text FROM vol_profile WHERE user_id = ?"
// 修复后
"SELECT skills FROM vol_profile WHERE user_id = ?"
String userProfile = userSkillsStr;  // 用 skills 作为画像描述
```

---

### 改动 13：补充演示活动数据

**数据库操作**：向 `vol_activity` 表批量插入 10 条演示活动（`audit_status=1`，`status` 含 0/1 两种），涵盖：

| 活动名称 | 状态 |
|----------|------|
| 城市马拉松赛事志愿服务 | 报名中(1) |
| 儿童科学启蒙夏令营 | 报名中(1) |
| 老年人数字生活帮扶 | 未开始(0) |
| 湿地生态保护调查 | 未开始(0) |
| 无家可归者关爱巡访 | 报名中(1) |
| 大学生职业发展公益讲堂 | 未开始(0) |
| 社区垃圾分类督导 | 报名中(1) |
| 残疾人运动会助威团 | 未开始(0) |
| 社区医疗义诊服务 | 报名中(1) |
| 传统文化节庆活动 | 未开始(0) |

补充后可推荐活动池：**17 条**，支持 Feed 流 3 页分页加载（每页 6 条）。

---

### 改动 1：修复 ActivityController 编译错误

**文件**：`smart-volunteer-activity/.../controller/ActivityController.java`

**问题**：`listAuditLogs()` 方法使用了 `AppHttpCodeEnum.NO_OPERATOR_AUTH`，但缺少对应 import，导致编译失败，管理员访问 `/homepage/admin/audit`（活动风控审核页）时返回 400。

**修复**：
```java
// 添加 import
import com.volunteer.common.enums.AppHttpCodeEnum;
```

---

### 改动 2：修复 VolActivityAuditLog 编译错误

**文件**：`smart-volunteer-activity/.../entity/VolActivityAuditLog.java`

**问题**：`@TableField(fill = FieldFill.INSERT)` 注解中 `FieldFill` 未 import，导致编译报错。

**修复**：
```java
import com.baomidou.mybatisplus.annotation.FieldFill;
```

---

### 改动 3：完善 UserAdminVo + UserServiceImpl.listUsers()

**文件**：
- `smart-volunteer-user/.../vo/UserAdminVo.java`
- `smart-volunteer-user/.../service/impl/UserServiceImpl.java`

**问题**：`UserAdminVo` 缺少组织者申请相关字段（`applyReason`/`auditStatus`/`aiAuditReport`）和用户统计字段（积分/报名次数/服务时长），导致 `UserManage.vue` 的 AI 尽调报告展开行始终显示"报告解析中..."。

**修复 UserAdminVo**：新增字段：

```java
private String applyReason;
private Integer auditStatus;
private String aiAuditReport;

private Integer creditBalance;
private Integer signupCount;
private Integer cancelCount;
private Integer totalHours;
```

**修复 listUsers()**：在 stream().map() 中逐一查询并填充统计字段：

```java
.map(u -> {
    UserAdminVo vo = BeanCopyUtils.copyBean(u, UserAdminVo.class);
    Long uid = u.getId();
    vo.setCreditBalance(userMapper.selectCreditBalance(uid) 或 0);
    vo.setSignupCount(userMapper.countSignupByUserId(uid) 或 0);
    vo.setCancelCount(userMapper.countCancelByUserId(uid) 或 0);
    VolProfile profile = profileMapper.selectById(uid);
    vo.setTotalHours(profile.getTotalHours() 或 0);
    return vo;
})
```

---

### 改动 4：UserManage.vue 全部用户 Tab 升级

**文件**：`smart-volunteer-frontend/src/pages/admin/UserManage.vue`

**问题**：全部用户 Tab 只展示了基础列（ID/昵称/用户名/角色/状态），缺乏详细统计信息。

**改动内容**：

1. **新增主表列**：积分（橙色加粗）、报名/取消（蓝/红双色）、时长h（绿色加粗）
2. **新增展开行**（`type="expand"`）：
   - 📧 邮箱、📱 手机号
   - ⭐ 积分余额（橙色）
   - ✅ 报名次数（蓝色）
   - ❌ 取消次数（红色）
   - 🕐 服务时长（绿色）
   - 📊 出勤率（自动计算：(报名-取消)/报名）
3. **新增 CSS**：`.stat-item` / `.stat-label` / `.stat-value`

---

### 改动 5：ActivityDetail.vue 签到 UI 重构（反作弊）

**文件**：`smart-volunteer-frontend/src/pages/activity/ActivityDetail.vue`

**问题**：原设计使用全局公共码（用户手动输入6位数字），无法防止作弊。

**重构方案**：改为展示用户个人专属签到码（SHA-256 哈希，注册时生成），由工作人员扫码或输入，而非用户手填。

**具体变更**：

1. **import 变更**：`submitCheckin` → `checkinByCode`
2. **删除** `checkinCode = ref('')`
3. **模板重构**：

```html
<!-- 原：输入框 -->
<el-input v-model="checkinCode" placeholder="请输入6位签到码" />
<el-button @click="handleCheckin">提交签到</el-button>

<!-- 改后：展示个人码 -->
<div style="font-size:36px;font-weight:bold;letter-spacing:6px;color:#409EFF;
            background:#ecf5ff;border-radius:8px;padding:12px 0;">
  {{ myReg.checkinCode || '——' }}
</div>
<div>工作人员扫码或输入此码为您签到</div>
<el-button @click="handleCheckin">提交签到</el-button>
```

4. **handleCheckin 逻辑**：
   - 从 `myReg.value.checkinCode` 获取码（而非表单输入）
   - 签到前查询积分余额 `balanceBefore`
   - 签到成功后 2.5 秒查询 `balanceAfter`
   - 若 `gained > 0` → `ElNotification` 弹出积分到账通知

---

### 改动 6：RecommendPage.vue AI 推荐卡片修复

**文件**：`smart-volunteer-frontend/src/pages/volunteer/RecommendPage.vue`

**问题**：`aiRecommendation = ref(null)` 永远不被赋值，导致页面底部的 AI 推荐理由卡片（`v-if="aiRecommendation"`）永远不渲染，UI 上的 DeepSeek 卡片白费。

**修复**：在 `recommendActivities()` 返回活动列表后，根据 KNN 结果合成推荐摘要对象：

```javascript
const res = await recommendActivities(10)
list.value = res.data || []

if (list.value.length > 0) {
  const top = list.value[0]
  const matched = top.matchedSkills || []
  const pct = (top.similarity * 100).toFixed(1) + '%'
  const skillWord = mySkills.value.length > 1
    ? `${mySkills.value.join('、')} 等 ${mySkills.value.length} 项技能`
    : `${mySkills.value[0]} 技能`

  aiRecommendation.value = {
    aiReasoning: `根据你的 ${skillWord}，系统从 ${list.value.length} 个招募中活动中为你精选了最匹配的活动。`
      + (matched.length
        ? `最佳匹配活动「${top.title}」与你共有 ${matched.length} 项技能重叠（${matched.join('、')}），综合匹配度 ${pct}。`
        : `最佳匹配活动「${top.title}」综合相似度为 ${pct}。`)
      + ` 建议优先报名排名靠前的活动，更能发挥你的专业优势。`,
    topActivity: top.title,
    matchRate: pct,
    matchedSkillsStr: matched.length ? matched.join('、') : null,
    total: list.value.length
  }
}
```

**卡片 UI 同步更新**：
- 标签从 `DeepSeek` → `KNN 向量匹配`（更准确反映实际引擎）
- 展示字段：`最佳匹配`/`技能契合度`/`命中技能`（替换原来的推荐志愿者字段）
- 底部说明：`TF-IDF 余弦相似度 + 技能向量 KNN 召回，共推荐 N 个匹配活动`

---

### 改动 7：修复活动AI风控日志 400 异常（Map.of 不允许 null）

**文件**：`smart-volunteer-activity/.../service/impl/ActivityServiceImpl.java`

**问题**：`listAuditLogs()` 中使用 `Map.of(...)` 构建响应，当 `riskTags` 或 `auditResult` 字段为 `null` 时抛出 `NullPointerException`，导致接口返回 400。

**根本原因**：Java `Map.of()` 是不可变 Map，**不允许 null 值**，数据库中早期记录的风险标签字段可能为空。

**修复**：改用 `HashMap` 并对 null 字段兜底为空字符串：
```java
// 修复前（null 会抛 NPE）
Map.of("riskTags", log.getRiskTags(), ...)

// 修复后
HashMap<String, Object> m = new HashMap<>();
m.put("riskTags", log.getRiskTags() != null ? log.getRiskTags() : "");
m.put("auditResult", log.getAuditResult() != null ? log.getAuditResult() : "");
```

**附加修复**：`smart-volunteer-activity/pom.xml` 补充了缺失的 `spring-boot-starter-webflux` 依赖（`WebClient` 调用 Python AI 服务所需）。

---

### 改动 8：修复用户管理待升级申请不显示 + 表格尺寸优化

**文件**：`smart-volunteer-frontend/src/pages/admin/UserManage.vue`

**问题 1 - 待升级申请不显示**：
- 数据库中早期申请记录的 `auditStatus` 字段为 `NULL`（该字段是后来才添加的）
- 前端过滤条件 `.filter(u => u.auditStatus === 0)` 严格要求等于 0，`null` 被排除在外
- 审核按钮条件 `v-if="row.auditStatus === 0"` 同样无法匹配 `null`，导致无操作按钮

**修复**：过滤和按钮条件均兼容 `null`：
```javascript
// 过滤：auditStatus=0 或 null/undefined 均视为"待审核"
.filter(u => u.auditStatus === 0 || u.auditStatus === null || u.auditStatus === undefined)
// 操作按钮同理
v-if="row.auditStatus === 0 || row.auditStatus === null || row.auditStatus === undefined"
```

**问题 2 - 表格区域太小**：
- `el-card` 没有撑满可用高度，内容区域仅按内容自适应

**修复**：
- 卡片加 `style="height:100%"` + CSS `:deep(.el-card__body) { flex: 1; overflow: auto }`
- 表格加 `:max-height="tableMaxHeight"`（动态计算为视口高度 - 固定元素高度）
- 外层 `<div>` 也设为 `height:100%`

---

## 9. 环境与启动方式

### 依赖服务启动顺序

```bash
# 1. Nacos（路径含中文，必须用 Python subprocess）
python3 -c "import subprocess; subprocess.Popen(['cmd','/c','startup.cmd','-m','standalone'], cwd='C:/Users/Annnn/Desktop/杂/nacos-server-2.4.3/nacos/bin')"

# 2. Redis
Start-Process -FilePath 'D:\Redis\redis-server.exe' -WindowStyle Hidden

# 3. RocketMQ NameServer（Java 17）
ROCKETMQ_HOME="D:/rocketMQ/..." nohup "$JAVA17" -cp "$ROCKETMQ/lib/*" org.apache.rocketmq.namesrv.NamesrvStartup > /tmp/namesrv.log &

# 4. RocketMQ Broker（必须 JDK8，Java17 code=-3 直接退出）
# broker.conf 必须加：brokerIP1=127.0.0.1 + namesrvAddr=127.0.0.1:9876

# 5. Java 微服务（Java 17 绝对路径）
JAVA="C:/Users/Annnn/.jdks/corretto-17.0.13/bin/java"
nohup "$JAVA" -jar smart-volunteer-gateway.jar   > /tmp/gateway.log   &
nohup "$JAVA" -jar smart-volunteer-user.jar      > /tmp/user.log      &
nohup "$JAVA" -jar smart-volunteer-activity.jar  > /tmp/activity.log  &
nohup "$JAVA" -jar smart-volunteer-ai.jar        > /tmp/ai.log        &
nohup "$JAVA" -jar smart-volunteer-credit.jar    > /tmp/credit.log    &

# 6. Python AI
nohup /d/python/python.exe D:/Project/smart-volunteer/python-ai/main.py > /tmp/python-ai.log &

# 7. 前端
cd D:/Project/smart-volunteer/smart-volunteer-frontend
npm run dev
```

### 快捷脚本

#### 一键启动 / 关闭（推荐）

两个脚本位于 `D:/Project/`，用 Python 实现，跨服务编排：

```bash
# 停止所有服务（按反向依赖顺序：Gateway→User→Activity→AI→Credit→RocketMQ→Nacos→Redis）
cd D:/Project && python stop-all.py

# 启动所有服务（Redis→Nacos→RocketMQ→User→Activity→Credit→AI→Gateway→Python AI→前端）
cd D:/Project && python start-all.py
```

`start-all.py` 配置路径（如需修改）：

| 变量 | 值 |
|------|-----|
| `JAVA17` | `C:\Users\Annnn\.jdks\corretto-17.0.13\bin\java.exe` |
| `JAVA8` | `D:\jdk\jdk-1.8\bin\java.exe`（Broker 专用）|
| `PYTHON` | `D:\python\python.exe` |
| `REDIS` | `D:\Redis\redis-server.exe` |
| `NACOS` | `C:\Users\Annnn\Desktop\杂\nacos-server-2.4.3\nacos\bin\startup.cmd` |
| `ROCKETMQ` | `D:\rocketMQ\rocketmq-all-4.9.8-bin-release` |
| `LOG_DIR` | `C:\Users\Annnn\sv-logs`（各服务日志输出目录）|

两个脚本特性：
- **stop-all.py**：通过 `netstat -ano` 按端口查找 PID 并 `taskkill`，等待端口释放后打印汇总状态
- **start-all.py**：先检测端口是否已占用（跳过已运行服务），逐步等待各服务就绪后再启动下一个，最后打印全服务状态表

#### Activity 服务一键重启（含 Nacos/Redis 自动检测）：

```bash
python3 D:/Project/smart-volunteer/benchmark/scripts/restart_activity.py
```

### Maven 编译

```bash
JAVA_HOME="C:/Users/Annnn/.jdks/corretto-17.0.13" \
  mvn package -pl smart-volunteer-user -am -DskipTests
# 注意：编译前必须停止占用 JAR 的 Java 进程
```

### 常见问题

| 问题 | 原因 | 解决 |
|------|------|------|
| RocketMQ Broker 无法连接 | 检测到 VPN 旧 IP | broker.conf 加 `brokerIP1=127.0.0.1` |
| Git Bash 中文参数乱码 | Shell 编码问题 | 改用 Python urllib 发请求 |
| Nacos 启动失败 | 路径含中文 | 用 Python subprocess 启动 |
| `JAVA_HOME=xxx mvn` 后 java 命令失败 | Shell 变量展开用旧值 | java 命令改用绝对路径 |

---

## 10. 测试账号

| 账号 | 密码 | 角色 | 备注 |
|------|------|------|------|
| admin | （见DB） | 管理员(0) | 可直接改密 |
| orgtest1 | org123456 | 组织者(2) | 已通过审批 |
| xiaoyu | 123456 | 志愿者(1) | 有技能档案 |
| john_doe | test123456 | 志愿者(1) | 测试用 |
| jane_smith | test123456 | 志愿者(1) | 测试用 |
| liuyixuan | test123456 | 志愿者(1) | 测试用 |
| voltest1 | test123456 | 志愿者(1) | 测试用 |

### 演示数据

| 活动ID | 名称 | 状态 |
|--------|------|------|
| 19 | 社区义务教学 | 报名中（5人已报） |
| 20 | 马拉松医疗保障 | 进行中 |
| 21 | 环保植树 | 已结束 |
| 22-28 | 各类活动 | 未开始/报名中 |
| 10009-10018 | 新增演示活动（10条） | 未开始/报名中 |

> 可推荐活动池（audit_status=1, status IN(0,1)）共 **17 条**

---

*文档由 Claude Code 整理生成，基于 2026-04-10 代码库快照。*
