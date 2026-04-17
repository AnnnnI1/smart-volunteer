<div align="center">

<h1>🌟 Smart Volunteer</h1>
<p><b>智能志愿服务管理平台</b></p>

<p>
  <img src="https://img.shields.io/badge/Vue-3.x-42b883?style=flat-square&logo=vue.js" />
  <img src="https://img.shields.io/badge/Spring_Boot-3.x-6db33f?style=flat-square&logo=springboot" />
  <img src="https://img.shields.io/badge/Python-FastAPI-009688?style=flat-square&logo=fastapi" />
  <img src="https://img.shields.io/badge/MySQL-8.0-4479a1?style=flat-square&logo=mysql" />
  <img src="https://img.shields.io/badge/Redis-7.x-dc382d?style=flat-square&logo=redis" />
  <img src="https://img.shields.io/badge/RocketMQ-4.9.8-d77310?style=flat-square" />
  <img src="https://img.shields.io/badge/Nacos-2.4.3-1677ff?style=flat-square" />
</p>

<p>一个面向高校 / 社区的 AI 驱动志愿服务管理系统，覆盖活动发布、报名签到、积分评价、智能推荐全链路。</p>

</div>

---

## ✨ 功能亮点

| 模块 | 描述 |
|------|------|
| 🔐 RBAC 权限体系 | 管理员 / 组织者 / 志愿者三级角色，JWT 鉴权，Gateway 统一注入 |
| 📋 活动全生命周期 | 创建 → AI 风控审核 → 发布 → 报名 → 进行中 → 结束 |
| ⚡ 高并发报名 | Redis 原子扣减名额 + 本地消息表补偿 + 唯一索引兜底 |
| 🔑 动态签到码 | 60 秒滚动签到码，防止截图作弊，倒计时自动刷新 |
| 🤖 AI 活动风控 | 新建活动自动调用 DeepSeek 审核内容合规性，不可用时转人工 |
| 🎯 KNN 志愿者匹配 | TF-IDF 余弦相似度 + 服务时长 + 积分 + 出勤率四维加权 |
| 📰 Feed 流个性化推荐 | 行为向量 × 0.6 + 画像向量 × 0.4，DeepSeek 生成推荐语 |
| 💬 NL2SQL 自然语言查询 | 管理员用自然语言查询数据库，DeepSeek 生成 SQL 并执行 |
| 💰 积分评价体系 | RocketMQ 异步消费签到 / 完成事件，幂等积分发放 |

---

## 🏗️ 系统架构

```
浏览器（Vue 3 前端 :5174）
        │  HTTP
        ▼
┌─────────────────────────────────┐
│  Gateway  :9090                 │
│  JWT 解析 → X-User-Id / Role    │
│  CORS + 路由转发                │
└──────────────┬──────────────────┘
               │
    ┌──────────┴──────────────────────────┐
    │        Nacos 注册中心 :8848          │
    └──┬──────┬──────┬──────┬────────────┘
       │      │      │      │
    :9091  :9092  :9093  :9095   :9094
    User  Activity  AI  Credit  Python AI
       │      │      │
       └──────┴──────┘
           RocketMQ :9876
```

---

## 🛠️ 技术栈

| 层级 | 技术选型 |
|------|---------|
| 前端 | Vue 3 + Vite + Element Plus + Pinia |
| 后端 | Spring Boot 3 + Spring Cloud + MyBatis-Plus |
| AI 服务 | Python FastAPI + sentence-transformers + DeepSeek API |
| 数据库 | MySQL 8 + Redis 7 |
| 消息队列 | RocketMQ 4.9.8 |
| 注册中心 | Nacos 2.4.3 |

---

## 📁 项目结构

```
smart-volunteer/
├── smart-volunteer-backend/       # Java 微服务
│   ├── smart-volunteer-gateway/   # 网关 :9090
│   ├── smart-volunteer-user/      # 用户服务 :9091
│   ├── smart-volunteer-activity/  # 活动服务 :9092
│   ├── smart-volunteer-ai/        # AI 服务 :9093
│   ├── smart-volunteer-credit/    # 积分服务 :9095
│   └── smart-volunteer-common/    # 公共模块
├── smart-volunteer-frontend/      # Vue 3 前端 :5174
├── python-ai/                     # Python AI 服务 :9094
├── sql/                           # 数据库脚本
├── docker/                        # Docker 配置
└── docker-compose.yml
```

---

## 🚀 快速开始

### 环境依赖

- Java 17
- Python 3.8+
- MySQL 8.0
- Redis 7
- RocketMQ 4.9.8
- Nacos 2.4.3
- Node.js 18+

### 配置敏感信息

各服务 `application.yml` 中需替换以下占位符：

```yaml
# 数据库
password: YOUR_DB_PASSWORD

# DeepSeek API（smart-volunteer-ai）
deepseek:
  api:
    key: YOUR_DEEPSEEK_API_KEY

# 阿里云 OSS（smart-volunteer-user，可选）
aliyun:
  oss:
    access-key-id: YOUR_ALIYUN_ACCESS_KEY_ID
    access-key-secret: YOUR_ALIYUN_ACCESS_KEY_SECRET
```

Python AI 服务复制 `.env.example` 为 `.env` 并填入 Key：

```bash
cp python-ai/.env.example python-ai/.env
```

### 启动顺序

```bash
# 1. 启动基础设施
# Nacos、Redis、RocketMQ（NameServer + Broker）

# 2. 启动 Java 微服务（任意顺序）
java -jar smart-volunteer-gateway.jar
java -jar smart-volunteer-user.jar
java -jar smart-volunteer-activity.jar
java -jar smart-volunteer-ai.jar
java -jar smart-volunteer-credit.jar

# 3. 启动 Python AI 服务
cd python-ai && python main.py

# 4. 启动前端
cd smart-volunteer-frontend
npm install && npm run dev
```

---

## 🎭 角色说明

| 角色 | 权限 |
|------|------|
| 管理员（role=0） | 用户管理、活动审核、NL2SQL 查询、KNN 匹配、风控日志 |
| 组织者（role=2） | 发布活动、管理报名名单、生成签到码、手动签到 |
| 志愿者（role=1） | 浏览活动、报名/取消、签到、查看积分、个性化推荐 |

> 注册默认为志愿者，组织者资格由管理员升级授予。

---

## 📸 页面预览

| 页面 | 说明 |
|------|------|
| 登录页 | 双栏布局，左侧品牌区 + 右侧表单 |
| 活动大厅 | 活动列表、筛选、报名 |
| 活动详情 | 活动信息、报名/签到操作 |
| 为我推荐 | 瀑布流 Feed，AI 生成推荐语 |
| 个人中心 | 资料编辑、积分流水 |
| 活动管理 | 组织者/管理员后台，含审核、签到码 |
| 用户管理 | 管理员查看用户、升降级 |
| NL2SQL | 自然语言查询数据库 |
| KNN 匹配 | 为活动智能推荐最合适的志愿者 |

---

## 📄 License

MIT License © 2026 Annnnl1
