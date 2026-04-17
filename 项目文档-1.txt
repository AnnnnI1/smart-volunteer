# Smart Volunteer 项目全景架构与 UML 设计文档

> 基于实际源码深度解析，精确到类名、方法名、数据库字段与中间件。
> 生成日期：2026-03-13

---

## 目录

1. [系统架构与全局逻辑](#一系统架构与全局逻辑)
2. [用例图与用例描述支撑](#二用例图与用例描述支撑)
3. [类图支撑数据](#三类图支撑数据)
4. [核心时序图逻辑拆解](#四核心时序图逻辑拆解)
5. [AI 算法活动图逻辑](#五ai-算法活动图逻辑)

---

## 一、系统架构与全局逻辑

### 1.1 整体技术栈

| 层次 | 技术组件 |
|------|---------|
| 前端 | Vue 3 + Vite + Element Plus，端口 5173/5174 |
| 网关 | Spring Cloud Gateway，端口 9090 |
| 微服务 | Spring Boot 3 + Spring Cloud Alibaba，端口 9091~9095 |
| AI 算法 | Python FastAPI，端口 9094 |
| 注册中心 | Nacos 2.4.3，端口 8848 |
| 消息队列 | Apache RocketMQ 4.9.8，NameServer:9876 / Broker:10911 |
| 缓存 | Redis，端口 6379 |
| 数据库 | MySQL，库名 smart_volunteer |

### 1.2 微服务拓扑图（文字版）

```
┌─────────────────────────────────────────────────────────────────┐
│                        前端 Vue3 (5173)                          │
│  Login / Dashboard / ActivityList / ActivityDetail / Profile    │
│  AdminAI / KnnMatch / ActivityManage / UserManage               │
└───────────────────────────┬─────────────────────────────────────┘
                            │  HTTP /api/** → proxy → 9090
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│               Gateway (9090)  AuthGlobalFilter                  │
│  • 白名单放行：/user/login、/user/register                        │
│  • JWT 解析 → 注入 X-User-Id、X-User-Role 请求头                 │
│  • Nacos 服务发现 → 路由转发                                      │
└──────┬────────┬────────┬────────┬──────────┬────────────────────┘
       │        │        │        │          │
  /user/**  /activity/**  /ai/**  /credit/**  (Nacos 服务发现)
       │        │        │        │
       ▼        ▼        ▼        ▼
  User(9091) Activity(9092) AI(9093) Credit(9095)
       │        │        │
       │        │        ├── HTTP → Python AI (9094)
       │        │        │        /ml/knn
       │        │        │        /ml/churn
       │        │        └── HTTP → DeepSeek API (外网)
       │        │
       │        ├── Redis (6379)  名额原子扣减 activity:quota:{id}
       │        ├── RocketMQ ──── activity-register-topic
       │        │                 activity-checkin-topic
       │        │                 activity-complete-topic
       │        └── MySQL smart_volunteer
       └────────────────────────────────
                     Nacos (8848) 服务注册与配置中心
```

### 1.3 Gateway 层：JWT 鉴权与 X-User-Id 透传

**核心类**：`com.volunteer.gateway.filter.AuthGlobalFilter`（实现 `GlobalFilter`，`getOrder()=-100` 最高优先级）

**完整流程**：

```
前端请求 Authorization: Bearer <JWT>
         │
         ▼  AuthGlobalFilter.filter()
    路径在白名单? ──是──▶ chain.filter() 直接放行
         │否
         ▼
    提取 token = header.substring(7)
         │
         ▼  JwtUtil.parseJWT(token)
    解析 claims.getSubject()  → userId (String)
    解析 claims.get("role")   → roleStr (String)
         │
         ▼
    request.mutate()
        .header("X-User-Id",   userId)
        .header("X-User-Role", roleStr)
    chain.filter(mutatedExchange)
         │
         ▼ 下游服务
    @RequestHeader("X-User-Id")   String userId
    @RequestHeader("X-User-Role") String userRole
```

**白名单**：`/user/login`、`/user/register`（`List.of()` 硬编码于 `AuthGlobalFilter.WHITE_LIST`）

**JWT 创建**（User 服务）：
```java
// JwtUtil.createJWT(userId, Map.of("role", user.getRole()))
// claims 携带 role，Gateway 解析后透传给所有下游
```

### 1.4 Nacos 注册中心作用

- 各服务启动时向 Nacos（8848）注册，Gateway 通过 Nacos 做服务发现
- 配置管理：各服务的 `application.yml` 中 `spring.cloud.nacos.discovery.server-addr=127.0.0.1:8848`
- Gateway 路由规则基于服务名（`lb://smart-volunteer-user` 等）负载均衡

### 1.5 RocketMQ 异步解耦逻辑

系统中共有 **3 个 Topic**：

| Topic | 生产者 | 消费者 | 业务语义 |
|-------|--------|--------|---------|
| `activity-register-topic` | `RegistrationServiceImpl.register()` | `ActivityRegisterConsumer` | 报名成功后异步更新 `joined_quota` |
| `activity-checkin-topic` | `RegistrationServiceImpl.checkin()` / `manualCheckin()` | `CreditConsumer` | 签到成功后发放 +10 积分（type=5） |
| `activity-complete-topic` | `ActivityServiceImpl.updateStatus()` newStatus=3 | `CreditConsumer` | 活动结束后为 status=2 者发放 +50 积分（type=2） |

**解耦价值**：报名写库（同步）与名额统计更新（异步）分离，签到写库与积分发放分离，任一环节故障不影响主流程。

---

## 二、用例图与用例描述支撑

### 2.1 角色定义

| 角色 | role 值 | 描述 |
|------|---------|------|
| 超级管理员 | 0 | 可操作所有数据，用户管理，积分调整 |
| 活动组织者 | 2 | 可创建/管理**自己发起**的活动（`organizer_id` 匹配） |
| 普通志愿者 | 1 | 可报名、签到、查看个人积分，不可创建活动 |

### 2.2 用例图（文字描述版）

```
┌─────────────────────────────────────────────────────────────┐
│                     Smart Volunteer 系统                     │
│                                                             │
│  ┌─ 超级管理员(role=0) ──────────────────────────────────┐  │
│  │  UC01 用户列表查询    GET /user/admin/list             │  │
│  │  UC02 角色升/降级     PUT /user/admin/{id}/role        │  │
│  │  UC03 创建活动        POST /activity                   │  │
│  │  UC04 管理所有活动    GET/PUT/DELETE /activity/{id}    │  │
│  │  UC05 生成签到码      POST /activity/{id}/checkin-code │  │
│  │  UC06 查看报名名单    GET /activity/{id}/registrations │  │
│  │  UC07 手动签到        POST /activity/{id}/manual-...   │  │
│  │  UC08 调整用户积分    POST /credit/admin/adjust        │  │
│  │  UC09 NL2SQL 查询     POST /ai/nl2sql/query            │  │
│  │  UC10 KNN 人才匹配    POST /ai/knn/match               │  │
│  │  UC11 流失预警        GET  /ai/risk/predict            │  │
│  └───────────────────────────────────────────────────────┘  │
│                                                             │
│  ┌─ 活动组织者(role=2) ──────────────────────────────────┐  │
│  │  (继承志愿者全部用例)                                   │  │
│  │  UC12 创建活动        POST /activity                   │  │
│  │  UC13 编辑自己的活动  PUT /activity/{id}               │  │
│  │  UC14 删除自己的活动  DELETE /activity/{id}            │  │
│  │  UC15 活动状态流转    PUT /activity/{id}/status/{s}    │  │
│  │  UC16 查看报名名单    GET /activity/{id}/registrations │  │
│  │  UC17 手动签到        POST /activity/{id}/manual-...   │  │
│  │  UC18 生成签到码      POST /activity/{id}/checkin-code │  │
│  │  UC19 查看我的活动    GET /activity/mine               │  │
│  └───────────────────────────────────────────────────────┘  │
│                                                             │
│  ┌─ 普通志愿者(role=1) ──────────────────────────────────┐  │
│  │  UC20 注册/登录       POST /user/register, /user/login │  │
│  │  UC21 修改个人信息    PUT /user/profile                │  │
│  │  UC22 查看活动列表    GET /activity/list               │  │
│  │  UC23 查看活动详情    GET /activity/{id}               │  │
│  │  UC24 报名活动        POST /activity/registration/{id} │  │
│  │  UC25 取消报名        DELETE /activity/registration/{id}│  │
│  │  UC26 现场签到        POST /activity/{id}/checkin      │  │
│  │  UC27 查看我的报名    GET /activity/registration/my    │  │
│  │  UC28 查看积分余额    GET /credit/balance              │  │
│  │  UC29 查看积分流水    GET /credit/records              │  │
│  └───────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

### 2.3 关键用例详述

#### UC24 报名活动（高并发场景）

- **前置条件**：用户已登录（JWT 有效），活动 `status=1`（报名中）
- **主流程**：
  1. 前端调用 `POST /api/activity/registration/{activityId}`，携带 JWT
  2. Gateway 解析 JWT → 注入 `X-User-Id`
  3. `RegistrationController.register()` → `RegistrationServiceImpl.register(userId, activityId)`
  4. Redis `DECR activity:quota:{id}` 原子扣减
  5. `@Transactional` 写 `vol_local_message` + `vol_registration`
  6. 发 MQ `activity-register-topic`
- **备选流程（名额不足）**：Redis DECR 后 remain<0，立即 INCR 回滚，返回 `ACTIVITY_FULL`
- **前端映射**：`ActivityDetail.vue` 报名按钮 → `axios.post('/api/activity/registration/{id}')`

#### UC26 现场签到

- **主流程**：
  1. 志愿者在 `ActivityDetail.vue` 输入 6 位签到码 → `POST /activity/{id}/checkin`
  2. `RegistrationServiceImpl.checkin()` 比对 `vol_checkin_code.code`（`is_active=1`）
  3. 更新 `vol_registration.status: 0→2`
  4. 发 MQ `activity-checkin-topic` → `CreditConsumer` 发 +10 分（type=5）
- **备选流程（手动签到）**：管理员通过 `ActivityManage.vue` 调用 `POST /activity/{id}/manual-checkin/{targetUserId}`

#### UC09 NL2SQL 查询

- **前端映射**：`AdminAI.vue` Tab "运营概览" → 自然语言输入框 → `POST /api/ai/nl2sql/query`
- **后端**：`NL2SqlController` → `NL2SqlServiceImpl` → DeepSeek API → SQL 提取 → `JdbcTemplate.queryForList()` → 返回结果集

---

## 三、类图支撑数据

### 3.1 核心业务实体类图

```
┌─────────────────────────────────────────────────────────────────┐
│  <<Entity>> VolActivity (@TableName="vol_activity")             │
├─────────────────────────────────────────────────────────────────┤
│  - id: Long  (@TableId AUTO)                                    │
│  - title: String                                                │
│  - description: String                                          │
│  - requiredSkills: String  (逗号分隔，如"医疗,急救")              │
│  - totalQuota: Integer                                          │
│  - joinedQuota: Integer                                         │
│  - status: Integer  (0未开始/1报名中/2进行中/3已结束)             │
│  - organizerId: Long  (关联 users.id)                           │
│  - startTime: LocalDateTime                                     │
│  - endTime: LocalDateTime                                       │
│  - createTime: LocalDateTime  (@FieldFill INSERT)               │
│  - updateTime: LocalDateTime  (@FieldFill INSERT_UPDATE)        │
└───────────────────────────┬─────────────────────────────────────┘
                            │ 1
                            │ organizerId → users.id
                            │ 一个组织者可发起多个活动
                            │ 1..N
┌───────────────────────────┴─────────────────────────────────────┐
│  <<Entity>> VolRegistration (@TableName="vol_registration")     │
├─────────────────────────────────────────────────────────────────┤
│  - id: Long                                                     │
│  - userId: Long  (关联 users.id)                                │
│  - activityId: Long  (关联 vol_activity.id)                     │
│  - status: Integer                                              │
│       0=已报名  1=已取消  2=已签到  4=缺席                        │
│  - createTime: LocalDateTime                                    │
│  [唯一索引] uk_user_activity(user_id, activity_id)               │
└─────────────────────────────────────────────────────────────────┘
        用户(1) ─── 报名(N) ─── 活动(1)  【多对多关联表】

┌─────────────────────────────────────────────────────────────────┐
│  <<Entity>> VolCheckinCode (@TableName="vol_checkin_code")      │
├─────────────────────────────────────────────────────────────────┤
│  - id: Long                                                     │
│  - activityId: Long  (关联 vol_activity.id)                     │
│  - code: VARCHAR(10)  (6位随机数字码)                            │
│  - isActive: Integer  (1=有效, 0=已废弃)                        │
│  - createTime: LocalDateTime                                    │
└─────────────────────────────────────────────────────────────────┘
       vol_activity(1) ─── vol_checkin_code(N)  一活动多码（历史）

┌─────────────────────────────────────────────────────────────────┐
│  <<Entity>> VolLocalMessage (@TableName="vol_local_message")    │
├─────────────────────────────────────────────────────────────────┤
│  - id: Long                                                     │
│  - messageId: String  (UUID，全局唯一幂等键)                      │
│  - businessType: String  (如 "ACTIVITY_REGISTER")               │
│  - content: String  (JSON，含 userId + activityId)              │
│  - status: Integer  (0=待处理 1=处理成功 2=处理失败)              │
│  - createTime: LocalDateTime                                    │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│  <<Entity>> VolCreditRecord (@TableName="vol_credit_record")    │
├─────────────────────────────────────────────────────────────────┤
│  - id: Long                                                     │
│  - userId: Long                                                 │
│  - activityId: Long  (管理员调整时为 NULL)                       │
│  - changeType: Integer                                          │
│       2=活动完成+50  4=管理员调整  5=现场签到+10                  │
│  - points: Integer  (变动积分值，正/负)                          │
│  - balanceAfter: Integer  (变更后余额快照)                       │
│  - remark: String                                               │
│  - createTime: LocalDateTime                                    │
│  [唯一索引] uk_user_activity_type(user_id, activity_id, change_type) │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│  <<Entity>> VolCreditBalance (@TableName="vol_credit_balance")  │
├─────────────────────────────────────────────────────────────────┤
│  - userId: Long  (@TableId，1:1 对应用户)                       │
│  - balance: Integer  (当前积分余额)                              │
│  - updateTime: LocalDateTime                                    │
└─────────────────────────────────────────────────────────────────┘
    users(1) ─── vol_credit_balance(1)  一对一

┌─────────────────────────────────────────────────────────────────┐
│  <<Entity>> VolDlq (@TableName="vol_dlq")                       │
├─────────────────────────────────────────────────────────────────┤
│  - id: Long                                                     │
│  - msgId: String  (RocketMQ 消息 ID)                            │
│  - topic: String                                                │
│  - body: String  (原始 JSON 消息体)                              │
│  - errorMsg: String  (截取前500字符)                             │
│  - reconsumeTimes: Integer                                      │
│  - status: Integer  (0=待处理 1=已手动处理)                      │
│  - createTime: LocalDateTime                                    │
└─────────────────────────────────────────────────────────────────┘
```

### 3.2 核心 Service 类方法列表

**ActivityServiceImpl** (extends ServiceImpl\<VolActivityMapper, VolActivity\>)
```
+ addActivity(dto: AddActivityDTO, userId: String, userRole: String): ResponseResult
    → setOrganizerId(Long.parseLong(userId))  ← 关键：写入创建者
+ updateActivity(id, dto, userId, userRole): ResponseResult
    → ownsActivity() 校验 organizer_id
+ deleteActivity(id, userId, userRole): ResponseResult
    → 状态锁(status==1||2 不可删) + 权限锁
+ updateStatus(id, newStatus, userId, userRole): ResponseResult
    → status=1: Redis SET activity:quota:{id}
    → status=3: markAbsent() + batchAddHours() + 发 MQ complete-topic
+ getMyActivities(userId, page, size): ResponseResult
    → WHERE organizer_id = userId
- hasManageRole(userRole): boolean  → role==0 || role==2
- isAdmin(userRole): boolean        → role==0
- ownsActivity(activity, userId): boolean → organizer_id == userId
```

**RegistrationServiceImpl** (extends ServiceImpl\<VolRegistrationMapper, VolRegistration\>)
```
+ register(userId, activityId): ResponseResult
    → Redis DECR → @Transactional(写本地消息表+报名表) → MQ 发送
+ cancel(userId, activityId): ResponseResult
    → status=1 + Redis INCR + DB decrementJoinedQuota
+ checkin(userId, activityId, code): ResponseResult
    → 校验 vol_checkin_code.code + status 0→2 + MQ checkin-topic
+ generateCheckinCode(userId, activityId): ResponseResult
    → deactivateAll(activityId) + 生成6位随机码 + INSERT
+ manualCheckin(activityId, targetUserId, userId, userRole): ResponseResult
    → 权限校验 + status 0→2 + MQ checkin-topic
+ getActivityRegistrations(activityId, userId, userRole): ResponseResult
    → selectWithUserInfo() JOIN users
+ myRegistrations(userId, page, size): ResponseResult
```

**CreditServiceImpl**
```
+ changePoints(userId, activityId, delta, type, remark): boolean
    → type≠4 幂等检查(existsRecord) → upsertBalance() → INSERT record
+ awardAllForActivity(activityId): void
    → selectUserIdsByActivity(WHERE status=2) → changePoints(+50, type=2)
+ getBalance(userId): ResponseResult
+ getRecords(userId, page, size): ResponseResult
+ adminAdjust(userId, delta, remark): ResponseResult
    → changePoints(type=4，不幂等)
```

### 3.3 VO 类清单

| VO 类 | 所在服务 | 主要字段 |
|-------|---------|---------|
| `ActivityVO` | activity | id, title, remainQuota(Redis实时), status, organizerName, statusDesc |
| `RegistrationVO` | activity | userId, status, username, nickname, phone |
| `MyRegistrationVO` | activity | activityId, activityTitle, status, createTime |
| `KnnResultVO` | ai | userId, realName, skills, finalScore, attendanceRate, rank |
| `ActivityRecommendVO` | ai | activityId, title, similarity, remainQuota, rank |
| `RiskVO` | ai | userId, riskLevel, riskColor, riskScore, riskFactors |
| `NL2SqlVO` | ai | sql, data, total |

---

## 四、核心时序图逻辑拆解

### 4.1 高并发抢报"四层防御链路"

```
前端 ActivityDetail.vue
    │ POST /api/activity/registration/{activityId}
    │ Header: Authorization: Bearer <JWT>
    ▼
Gateway AuthGlobalFilter
    │ JwtUtil.parseJWT(token) → userId, roleStr
    │ mutate().header("X-User-Id", userId)
    │ 路由 → activity:9092
    ▼
RegistrationController.register(
    @RequestHeader("X-User-Id") userId,
    @PathVariable activityId)
    │
    ▼
RegistrationServiceImpl.register(userId, activityId)
    │
    │ ── 【第一层：业务前置校验】 ──────────────────────────────
    │ activityMapper.selectById(activityId)
    │ if activity.status != 1 → return ACTIVITY_NOT_OPEN
    │
    │ ── 【第二层：Redis 原子扣减（防超发核心）】 ──────────────
    │ redisCache.decrement("activity:quota:" + activityId)
    │   └─ 底层执行: DECR key（原子操作，单线程，不会超卖）
    │ if remain < 0:
    │     redisCache.increment(quotaKey)  ← 立即回滚
    │     return ACTIVITY_FULL            ← 快速失败，不触碰DB
    │
    │ ── 【第三层：@Transactional 本地事务（两写同生共死）】 ───
    │ @Transactional begin
    │   ├─ localMessageMapper.insert(VolLocalMessage{
    │   │      messageId: UUID,
    │   │      businessType: "ACTIVITY_REGISTER",
    │   │      content: {userId, activityId},
    │   │      status: 0
    │   │  })
    │   │
    │   └─ registrationMapper.reactivateCancelled(userId, activityId)
    │        UPDATE vol_registration SET status=0
    │        WHERE user_id=? AND activity_id=? AND status=1
    │        → affected=0 时走 INSERT 新记录
    │          VolRegistration{userId, activityId, status=0}
    │          registrationMapper.insert(registration)
    │
    │ ── 【第四层：联合唯一索引物理防重（数据库兜底）】 ──────────
    │  catch DuplicateKeyException:
    │      (uk_user_activity 触发)
    │      redisCache.increment(quotaKey)  ← 归还名额
    │      return ALREADY_REGISTERED
    │
    │ @Transactional commit (两张表原子提交)
    │
    │ ── 【MQ 异步解耦：joined_quota 最终一致性更新】 ──────────
    │ defaultMQProducer.send(
    │     new Message("activity-register-topic",
    │         {activityId, userId, messageId}))
    │       │
    │       ▼ (异步)
    │  ActivityRegisterConsumer.onMessage()
    │       ├─ activityMapper.incrementJoinedQuota(activityId)
    │       │    UPDATE vol_activity SET joined_quota=joined_quota+1 WHERE id=?
    │       └─ localMessageMapper.update(status=1 WHERE messageId=?)
    │
    │ ── 【兜底补偿：MQ 失败时的最终一致性保障】 ──────────────
    │  LocalMessageCompensateTask（@Scheduled fixedDelay=30000）
    │       SELECT * FROM vol_local_message
    │       WHERE status=0 AND businessType='ACTIVITY_REGISTER' LIMIT 50
    │       → 对每条: incrementJoinedQuota() → status=1
    │
    ▼
    return okResult()
```

**四层防御总结**：

| 层次 | 机制 | 类/方法 | 防御对象 |
|------|------|---------|---------|
| 第一层 | 状态前置校验 | `register()` 冒头 | 非报名阶段活动 |
| 第二层 | Redis DECR 原子扣减 | `redisCache.decrement()` | 并发超卖 |
| 第三层 | `@Transactional` 两写同步 | `localMessageMapper.insert` + `registrationMapper.insert` | 消息丢失导致数据不一致 |
| 第四层 | 唯一索引 `uk_user_activity` | MySQL 物理兜底 | 极端并发下重复报名 |

---

### 4.2 动态签到与积分异步发放闭环

#### 4.2.1 签到码生成（管理员/组织者）

```
ActivityManage.vue
    │ POST /api/activity/{id}/checkin-code
    │ Header: X-User-Role=0或2
    ▼
RegistrationController.generateCheckinCode()
    │ role != 0 && role != 2 → 403
    ▼
RegistrationServiceImpl.generateCheckinCode(userId, activityId)
    │ activityMapper.selectById → activity.status != 2 → 报错
    │ checkinCodeMapper.deactivateAll(activityId)
    │   UPDATE vol_checkin_code SET is_active=0 WHERE activity_id=?
    │ String code = String.format("%06d", new Random().nextInt(1000000))
    │ checkinCodeMapper.insert(VolCheckinCode{activityId, code, isActive=1})
    ▼
返回 6 位签到码（前端以 96px 大字展示）
```

#### 4.2.2 志愿者自助签到流程

```
ActivityDetail.vue（输入6位签到码）
    │ POST /api/activity/{id}/checkin  body: {code: "123456"}
    ▼
RegistrationController.checkin(userId, activityId, code)
    ▼
RegistrationServiceImpl.checkin(userId, activityId, code)
    │
    │ 1. 活动状态校验：activity.status != 2 → 报错
    │
    │ 2. 签到码校验：
    │    checkinCodeMapper.selectActive(activityId)
    │    → SELECT * FROM vol_checkin_code
    │         WHERE activity_id=? AND is_active=1 LIMIT 1
    │    if activeCode == null || !code.equals(activeCode.getCode()) → 报错
    │
    │ 3. 查有效报名记录：
    │    WHERE user_id=userId AND activity_id=activityId AND status=0
    │    if null → 报错（未报名或已签到）
    │
    │ 4. @Transactional 更新状态：
    │    reg.setStatus(2)  ← 0(已报名) → 2(已签到)
    │    registrationMapper.updateById(reg)
    │
    │ 5. 发 MQ：
    │    defaultMQProducer.send(
    │        new Message("activity-checkin-topic", {userId, activityId}))
    │          │
    │          ▼ CreditConsumer 消费
    │    creditService.changePoints(userId, activityId, +10, type=5, "现场签到奖励")
    │          │
    │          ├─ 幂等检查：existsRecord(userId, activityId, 5) == 0？
    │          ├─ balanceMapper.upsertBalance(userId, +10)
    │          └─ recordMapper.insert(VolCreditRecord{type=5, points=10})
    ▼
返回成功
```

#### 4.2.3 活动结束 → status 状态演变 → 积分发放闭环

```
ActivityManage.vue
    │ PUT /api/activity/{id}/status/3
    ▼
ActivityServiceImpl.updateStatus(id, newStatus=3, userId, userRole)
    │
    │ 1. 权限校验（isAdmin || ownsActivity）
    │
    │ 2. 状态单向校验：newStatus(3) > activity.status(2) ✓
    │
    │ 3. activity.setStatus(3); activityMapper.updateById(activity)
    │
    │ 4. redisCache.delete("activity:quota:" + id)  ← 清理名额缓存
    │
    │ 5. registrationMapper.markAbsent(id)
    │    UPDATE vol_registration SET status=4
    │    WHERE activity_id=? AND status=0
    │    ← 已报名未签到(0) → 缺席(4)
    │    ← 已签到(2) 保持不变 ← KNN 出勤率计算基准
    │
    │    vol_registration.status 状态全貌：
    │    0(已报名) ──签到──▶ 2(已签到)
    │    0(已报名) ──活动结束──▶ 4(缺席)
    │    0(已报名) ──取消──▶ 1(已取消)
    │
    │ 6. 服务时长累加：
    │    List<Long> checkedInUserIds = registrationMapper.selectCheckedInUserIds(id)
    │    long hours = ChronoUnit.HOURS.between(startTime, endTime)
    │    profileMapper.batchAddHours(checkedInUserIds, hoursToAdd)
    │    UPDATE vol_profile SET total_hours=total_hours+? WHERE user_id IN(...)
    │
    │ 7. 发 MQ 活动完成：
    │    defaultMQProducer.send(
    │        new Message("activity-complete-topic", {activityId}))
    │          │
    │          ▼ CreditConsumer 消费
    │    creditService.awardAllForActivity(activityId)
    │          │
    │          ├─ registrationMapper.selectUserIdsByActivity(activityId)
    │          │    SELECT user_id FROM vol_registration
    │          │    WHERE activity_id=? AND status=2   ← 只取已签到者！
    │          │    ← status=4(缺席) 完全不在结果集中，不发分
    │          │
    │          └─ for each uid:
    │               changePoints(uid, activityId, +50, type=2, "活动完成奖励")
    │                   ├─ 幂等检查：existsRecord(uid, activityId, 2)==0？
    │                   ├─ upsertBalance(uid, +50)
    │                   └─ insert(VolCreditRecord{type=2, points=50})
    ▼
```

#### 4.2.4 CreditConsumer MQ 消费重试与 DLQ 死信兜底

```
CreditConsumer（订阅 activity-checkin-topic + activity-complete-topic）
    │
    │ for each MessageExt msg in msgs:
    │     try:
    │         body = new String(msg.getBody(), UTF_8)
    │         payload = objectMapper.readValue(body, Map.class)
    │
    │         if "activity-checkin-topic":
    │             creditService.changePoints(userId, activityId, 10, 5, ...)
    │
    │         if "activity-complete-topic":
    │             creditService.awardAllForActivity(activityId)
    │
    │     catch Exception e:
    │         log.error("积分消费异常 msgId={} 重试={}", ...)
    │
    │         if msg.getReconsumeTimes() >= MAX_RETRY(3):
    │             ── 【轻量级死信兜底：写 vol_dlq 表】 ────────
    │             saveToDlq(msg, e)
    │             │  VolDlq{msgId, topic, body, errorMsg(前500字), status=0}
    │             │  dlqMapper.insert(dlq)
    │             └─ log.warn("消息已转入DLQ补偿表")
    │             return CONSUME_SUCCESS  ← 不再重试，人工介入
    │         else:
    │             return RECONSUME_LATER  ← RocketMQ 自动重试
```

---

## 五、AI 算法活动图逻辑

### 5.1 KNN 志愿者匹配算法完整执行流

#### 5.1.1 Java 端数据准备（KnnServiceImpl）

```
POST /ai/knn/match
    Body: {requiredSkills: ["医疗","急救"], topK: 5}
    ▼
KnnController.match(dto, userId, userRole)
    → role != 0 → 403
    ▼
KnnServiceImpl.match(dto)
    │
    │ 1. 查询候选志愿者基础数据（SQL JOIN）
    │    SELECT u.id, u.nickname as realName,
    │           p.skills, p.total_hours,
    │           b.balance as creditBalance
    │    FROM users u
    │    LEFT JOIN vol_profile p ON u.id = p.user_id
    │    LEFT JOIN vol_credit_balance b ON u.id = b.user_id
    │    WHERE u.role = 1  （只取志愿者）
    │
    │ 2. 为每个志愿者计算出勤率：
    │    SELECT user_id,
    │           COUNT(*) as total,
    │           SUM(CASE WHEN status=4 THEN 1 ELSE 0 END) as absent
    │    FROM vol_registration
    │    GROUP BY user_id
    │    attendanceRate = (total - absent) / total
    │    ← status=4(缺席) 记录直接拉低出勤率
    │
    │ 3. 组装请求体，HTTP POST → Python /ml/knn
    │    {
    │      requiredSkills: ["医疗","急救"],
    │      volunteers: [{
    │          userId, realName, skills("医疗,急救,教学"),
    │          totalHours(120), creditBalance(240), attendanceRate(0.85)
    │      }, ...],
    │      topK: 5
    │    }
    │
    │ 4. 降级方案（Python 不可用时）：
    │    本地 Java 计算 0/1 向量余弦相似度（简化版）
    ▼
Python FastAPI /ml/knn
（见 5.1.2）
    │
    ▼
返回 KnnResultVO 列表（含 finalScore, rank, matchedSkills）
```

#### 5.1.2 Python KNN 算法核心执行流（knn_service.py）

```
def knn_match(required_skills, volunteers, top_k):
    │
    │ ── 【Step 1：技能 TF-IDF 余弦相似度】 ──────────────────
    │
    │ 构建语料库 corpus：
    │     query_text = " ".join(required_skills)  ← 活动所需技能
    │     for vol in volunteers:
    │         doc = vol.skills.replace(",", " ")  ← 志愿者技能列表
    │     corpus = [query_text] + [vol_docs...]
    │
    │ TfidfVectorizer().fit_transform(corpus)
    │     → 生成 TF-IDF 矩阵（IDF 抑制高频通用词，TF 体现本文档词频）
    │
    │ cosine_similarity(query_vec, vol_vecs)
    │     → skill_score ∈ [0.0, 1.0]
    │     （完全匹配→1.0，无交集→0.0）
    │
    │ ── 【Step 2：服务时长奖励分】 ──────────────────────────
    │
    │ max_hours = max(vol.totalHours for all volunteers)
    │ hours_score = min(vol.totalHours / max_hours, 1.0) * 0.10
    │     → 奖励上限 +0.10（鼓励服务经验丰富的志愿者）
    │
    │ ── 【Step 3：积分信誉加成】 ─────────────────────────────
    │
    │ max_credit = max(vol.creditBalance for all volunteers)
    │ credit_score = min(vol.creditBalance / max_credit, 1.0) * 0.15
    │     → 奖励上限 +0.15（高积分=高信誉=优先匹配）
    │
    │ ── 【Step 4：出勤率惩罚/奖励权重（反作弊核心）】 ──────────
    │
    │ attendance_score = vol.attendanceRate * 0.20
    │     → 出勤率 1.0 → +0.20（满分）
    │     → 出勤率 0.0 → +0.00（全缺席，严重惩罚）
    │     → 出勤率 0.5 → +0.10
    │     ← status=4 缺席记录在 Java 端已计入分母
    │
    │ ── 【Step 5：四维加权融合】 ───────────────────────────
    │
    │ final_score = (skill_score         × 0.55)
    │             + (hours_score         × 0.10)  (已归一化)
    │             + (credit_score        × 0.15)  (已归一化)
    │             + (attendance_score    × 0.20)
    │
    │   权重比例：技能匹配(55%) + 出勤率(20%) + 信誉积分(15%) + 时长(10%)
    │
    │ ── 【Step 6：排序与 Top-K 截取】 ──────────────────────
    │
    │ sorted by final_score DESC → 取前 top_k 条
    │ 附加 rank(排名) + matchedSkills(命中技能列表)
    │
    ▼
返回：[{userId, realName, skills, finalScore, attendanceRate,
        hoursScore, creditScore, attendanceScore, rank, matchedSkills}]
```

**四维权重汇总表**：

| 维度 | 权重 | 计算逻辑 | 数据来源 |
|------|------|---------|---------|
| 技能 TF-IDF 余弦相似度 | 55% | `cosine_similarity(TF-IDF向量)` | `vol_profile.skills` |
| 出勤率惩罚权重 | 20% | `attendanceRate × 0.20` | `vol_registration WHERE status=4` |
| 积分信誉加成 | 15% | `(balance/max) × 0.15` | `vol_credit_balance.balance` |
| 服务时长奖励 | 10% | `(hours/max) × 0.10` | `vol_profile.total_hours` |

### 5.2 志愿者流失风险预警（churn_service.py）

```
GET /ai/risk/predict
    ▼
RiskController → RiskServiceImpl
    │
    │ 1. SQL 聚合查询每个志愿者行为数据：
    │    SELECT u.id, u.nickname,
    │           COUNT(r.id) as signupCount,
    │           SUM(CASE WHEN r.status=1 THEN 1 ELSE 0 END) as cancelCount,
    │           p.total_hours,
    │           DATEDIFF(NOW(), MAX(r.create_time)) as inactiveDays
    │    FROM users u
    │    LEFT JOIN vol_registration r ON u.id=r.user_id
    │    LEFT JOIN vol_profile p ON u.id=p.user_id
    │    WHERE u.role=1 GROUP BY u.id
    │
    │ 2. HTTP POST → Python /ml/churn
    │    [{userId, signupCount, cancelCount, totalHours, inactiveDays}]
    │
    │ Python churn_service.py:
    │
    │    cancel_rate = cancelCount / max(signupCount, 1)
    │
    │    inactive_norm = min(inactiveDays / 90, 1.0)  ← 90天满分
    │    hours_norm    = 1.0 - min(totalHours / 200, 1.0)  ← 时长越多风险越低
    │    signup_norm   = 1.0 - min(signupCount / 20, 1.0)  ← 报名越多风险越低
    │
    │    risk_score = (cancel_rate   × 0.40)
    │               + (inactive_norm × 0.35)
    │               + (hours_norm    × 0.15)
    │               + (signup_norm   × 0.10)
    │
    │    风险等级：
    │        score ≥ 0.65 → riskLevel="高"  riskColor="danger"
    │        score ≥ 0.35 → riskLevel="中"  riskColor="warning"
    │        score <  0.35 → riskLevel="低"  riskColor="success"
    │
    │    riskFactors: List[str]（如"取消率过高(75%)","超过60天未活跃"）
    │
    │ 3. 结果排序（risk_score DESC），返回 RiskVO 列表
    │    前端 AdminAI.vue "流失预警" Tab 展示
    ▼
```

### 5.3 NL2SQL 自然语言查询流程（Java 端）

```
AdminAI.vue（自然语言输入框）
    │ POST /api/ai/nl2sql/query  body: {query: "查询过去一周报名人数最多的活动"}
    ▼
NL2SqlController.query(dto)
    ▼
NL2SqlServiceImpl.query(dto.getQuery())
    │
    │ 1. 构建 System Prompt（含5张表Schema）：
    │    "你是SQL专家，数据库表结构如下：
    │     users(id, username, nickname, role, phone)
    │     vol_activity(id, title, required_skills, total_quota, joined_quota,
    │                   status, organizer_id, start_time, end_time)
    │     vol_registration(id, user_id, activity_id, status, create_time)
    │     vol_profile(user_id, skills, total_hours, ...)
    │     vol_checkin_code(id, activity_id, code, is_active)
    │     只生成SELECT语句，禁止UPDATE/DELETE/INSERT"
    │
    │ 2. 调用 DeepSeek API（HTTP POST）：
    │    model: "deepseek-chat"
    │    messages: [system, {role:user, content: dto.getQuery()}]
    │    API Key: sk-635682ea7b7847b280e8f5b7e71b8820
    │
    │ 3. 提取 SQL（支持 Markdown ```sql 代码块）：
    │    Pattern.compile("```sql\\s*([\\s\\S]*?)```", CASE_INSENSITIVE)
    │    → 未命中时直接截取第一个 SELECT...
    │
    │ 4. 安全校验：
    │    if !sql.trim().toUpperCase().startsWith("SELECT") → 拒绝
    │    if !sql.contains("LIMIT") → sql += " LIMIT 200"  (自动追加)
    │
    │ 5. 执行查询：
    │    jdbcTemplate.queryForList(sql)
    │    → List<Map<String, Object>> data
    │
    │ 6. 返回 NL2SqlVO{sql, data, total=data.size()}
    │
    │ 超时处理：
    │    catch SocketTimeoutException → "AI服务响应超时"
    │    catch Exception → "查询执行失败: " + e.getMessage()
    ▼
前端渲染 SQL + 动态表格（列名由第一行 Map.keySet() 推断）
```

---

## 附录：数据库表全量字段

### users
```sql
id BIGINT PK AUTO_INCREMENT
username VARCHAR(50) UNIQUE
password VARCHAR(100)  -- BCrypt 加密
nickname VARCHAR(50)
phone VARCHAR(20)
role TINYINT  -- 0管理员 1志愿者 2组织者
create_time DATETIME
```

### vol_activity
```sql
id BIGINT PK
title VARCHAR(100)
description TEXT
required_skills VARCHAR(255)  -- 逗号分隔
total_quota INT
joined_quota INT DEFAULT 0
status TINYINT  -- 0未开始 1报名中 2进行中 3已结束
organizer_id BIGINT  -- FK→users.id，NULL表示管理员创建
start_time DATETIME
end_time DATETIME
create_time DATETIME
update_time DATETIME
```

### vol_registration
```sql
id BIGINT PK
user_id BIGINT
activity_id BIGINT
status TINYINT  -- 0已报名 1已取消 2已签到 4缺席
create_time DATETIME
UNIQUE KEY uk_user_activity(user_id, activity_id)
```

### vol_profile
```sql
user_id BIGINT PK
real_name VARCHAR(50)
bio TEXT
skills VARCHAR(255)  -- 逗号分隔
total_hours INT DEFAULT 0
avatar_url VARCHAR(500)
```

### vol_checkin_code
```sql
id BIGINT PK
activity_id BIGINT
code VARCHAR(10)  -- 6位数字
is_active TINYINT  -- 1有效 0废弃
create_time DATETIME
```

### vol_credit_balance
```sql
user_id BIGINT PK
balance INT DEFAULT 0
update_time DATETIME
```

### vol_credit_record
```sql
id BIGINT PK
user_id BIGINT
activity_id BIGINT  -- 管理员调整时为NULL
change_type TINYINT  -- 2完成+50 4管理员调整 5签到+10
points INT
balance_after INT
remark VARCHAR(128)
create_time DATETIME
UNIQUE KEY uk_user_activity_type(user_id, activity_id, change_type)
```

### vol_local_message
```sql
id BIGINT PK
message_id VARCHAR(64) UNIQUE  -- UUID幂等键
business_type VARCHAR(50)  -- ACTIVITY_REGISTER
content TEXT  -- JSON
status TINYINT  -- 0待处理 1成功 2失败
create_time DATETIME
```

### vol_dlq
```sql
id BIGINT PK
msg_id VARCHAR(100)
topic VARCHAR(100)
body TEXT
error_msg VARCHAR(500)
reconsume_times INT
status TINYINT  -- 0待处理 1已手动处理
create_time DATETIME
```

---

*文档基于实际源码生成，所有类名、方法名、SQL 字段均与代码库一一对应。*
