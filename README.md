<div align="center">

<h1>Smart Volunteer</h1>
<h3>智能志愿服务管理平台</h3>

<p>
  <img src="https://img.shields.io/badge/Vue-3.x-42b883?style=flat-square&logo=vue.js" />
  <img src="https://img.shields.io/badge/Spring_Boot-3.x-6db33f?style=flat-square&logo=springboot" />
  <img src="https://img.shields.io/badge/Python-FastAPI-009688?style=flat-square&logo=fastapi" />
  <img src="https://img.shields.io/badge/MySQL-8.0-4479a1?style=flat-square&logo=mysql" />
  <img src="https://img.shields.io/badge/Redis-7.x-dc382d?style=flat-square&logo=redis" />
  <img src="https://img.shields.io/badge/RocketMQ-4.9.8-d77310?style=flat-square" />
  <img src="https://img.shields.io/badge/Nacos-2.4.3-1677ff?style=flat-square" />
</p>

<p>面向高校 / 社区的 AI 驱动志愿服务管理系统，覆盖活动发布、报名签到、积分评价、智能推荐全链路。</p>

</div>

---

## 功能亮点

| 模块 | 描述 |
|------|------|
| RBAC 权限体系 | 管理员 / 组织者 / 志愿者三级角色，JWT 鉴权，Gateway 统一注入 |
| 活动全生命周期 | 创建 → AI 风控审核 → 发布 → 报名 → 进行中 → 结束 → 积分结算 |
| 高并发报名 | Redis 原子扣减名额 + 本地消息表补偿 + 唯一索引兜底 |
| 动态签到码 | 60 秒滚动签到码，倒计时自动刷新，防截图作弊 |
| AI 活动风控 | 新建活动自动调用 DeepSeek 审核内容合规性，不可用时转人工 |
| KNN 志愿者匹配 | TF-IDF 余弦相似度 + 服务时长 + 积分 + 出勤率四维加权 |
| Feed 个性化推荐 | 行为向量 + 画像向量融合，DeepSeek 生成推荐语，瀑布流无限滚动 |
| NL2SQL 自然语言查询 | 管理员用自然语言查询数据库，DeepSeek 生成 SQL 并安全执行 |
| 积分评价体系 | RocketMQ 异步消费签到/完成事件，幂等积分发放 |
| AI 运营中心 | 活动诊断、流失预警、积分异常检测、运营概览 |
| 负反馈中心 | 举报管理、信用管理、惩罚记录、申诉处理、黑名单 |

---

## 系统架构

```
浏览器（Vue 3 前端 :5174）
       │  HTTP
       ▼
┌─────────────────────────────────┐
│  Gateway  :9090                 │
│  JWT 解析 → X-User-Id / Role   │
│  CORS + 路由转发                │
└──────────────┬──────────────────┘
               │
    ┌──────────┴──────────────────────────┐
    │        Nacos 注册中心 :8848          │
    └──┬──────┬──────┬──────┬────────────┘
       │      │      │      │
    :9091  :9092  :9093  :9095   :9094
    User  Activity  AI   Credit  Python AI
       │      │      │
       └──────┴──────┘
         RocketMQ :9876
```

---

## 技术栈

| 层级 | 技术选型 |
|------|---------|
| 前端 | Vue 3 + Vite + Element Plus + Pinia |
| 后端 | Spring Boot 3 + Spring Cloud Gateway + MyBatis-Plus |
| AI 服务 | Python FastAPI + sentence-transformers + DeepSeek API |
| 数据库 | MySQL 8.0 + Redis 7 |
| 消息队列 | RocketMQ 4.9.8 |
| 注册中心 | Nacos 2.4.3 |

---

## 项目结构

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
└── screenshots/                   # 页面截图
```

---

## 快速开始

### 方式一：Docker Compose（推荐）

无需安装 MySQL/Redis/Nacos/RocketMQ，一键启动全部服务：

```bash
# 1. 配置环境变量
cp .env.docker .env
# 编辑 .env，填入 DeepSeek API Key（其他可用默认值）

# 2. 编译后端（需要 Java 17 + Maven）
cd smart-volunteer-backend
mvn clean package -DskipTests
cd ..

# 3. 一键启动所有服务
docker-compose up -d

# 4. 查看日志
docker-compose logs -f

# 5. 停止所有服务
docker-compose down
```

首次启动后访问 `http://localhost`（前端），各服务端口：
- 前端 Nginx：80
- Gateway：9090
- Nacos 控制台：8848（账密 nacos/nacos）

### 方式二：手动启动

#### 环境依赖

- Java 17
- Python 3.8+
- MySQL 8.0
- Redis 7
- RocketMQ 4.9.8
- Nacos 2.4.3
- Node.js 18+

### 配置

每个微服务目录下提供了 `application-example.yml` 模板文件，复制并填入真实配置：

```bash
# 以 user 服务为例
cp smart-volunteer-backend/smart-volunteer-user/src/main/resources/application-example.yml \
   smart-volunteer-backend/smart-volunteer-user/src/main/resources/application.yml
# 编辑 application.yml，填入数据库密码等配置
```

Python AI 服务同理：

```bash
cp python-ai/.env.example python-ai/.env
# 编辑 .env，填入 DeepSeek API Key
```

### 需要配置的关键项

| 配置项 | 说明 |
|--------|------|
| `spring.datasource.password` | MySQL 数据库密码 |
| `deepseek.api.key` | DeepSeek API Key（AI 服务需要） |
| `aliyun.oss.*` | 阿里云 OSS 配置（用户服务，可选） |
| `jwt.secret` | JWT 签名密钥（所有服务保持一致） |

### 启动

```bash
# 1. 启动基础设施（Nacos、Redis、RocketMQ NameServer + Broker）

# 2. 编译启动 Java 微服务
cd smart-volunteer-backend
mvn clean package -DskipTests
java -jar smart-volunteer-gateway/target/*.jar &
java -jar smart-volunteer-user/target/*.jar &
java -jar smart-volunteer-activity/target/*.jar &
java -jar smart-volunteer-ai/target/*.jar &
java -jar smart-volunteer-credit/target/*.jar &

# 3. 启动 Python AI
cd python-ai && python main.py &

# 4. 启动前端
cd smart-volunteer-frontend
npm install && npm run dev
```

---

## 角色说明

| 角色 | 权限 |
|------|------|
| 管理员（role=0） | 用户管理、活动审核、NL2SQL 查询、KNN 匹配、AI 运营中心、风控日志 |
| 组织者（role=2） | 发布活动、管理报名名单、生成签到码、手动签到、查看活动统计 |
| 志愿者（role=1） | 浏览活动、报名/取消、签到、查看积分、个性化推荐 |

> 注册默认为志愿者，组织者资格由管理员升级授予。

---

## 页面预览

### 登录 & 首页

<table>
  <tr>
    <td align="center"><b>登录界面</b></td>
    <td align="center"><b>志愿者首页</b></td>
  </tr>
  <tr>
    <td><img src="screenshots/登录界面.png" width="400" /></td>
    <td><img src="screenshots/志愿者首页.png" width="400" /></td>
  </tr>
  <tr>
    <td align="center"><b>组织者首页</b></td>
    <td align="center"><b>管理员首页</b></td>
  </tr>
  <tr>
    <td><img src="screenshots/组织者首页.png" width="400" /></td>
    <td><img src="screenshots/管理员首页.png" width="400" /></td>
  </tr>
</table>

### 活动

<table>
  <tr>
    <td align="center"><b>活动大厅</b></td>
    <td align="center"><b>活动管理</b></td>
  </tr>
  <tr>
    <td><img src="screenshots/活动大厅.png" width="400" /></td>
    <td><img src="screenshots/活动管理页面.png" width="400" /></td>
  </tr>
</table>

### 用户 & 个人

<table>
  <tr>
    <td align="center"><b>个人中心</b></td>
    <td align="center"><b>用户管理</b></td>
  </tr>
  <tr>
    <td><img src="screenshots/个人中心.png" width="400" /></td>
    <td><img src="screenshots/用户管理页面.png" width="400" /></td>
  </tr>
  <tr>
    <td align="center"><b>组织者申请管理</b></td>
    <td align="center"><b>组织者申请 AI 分析</b></td>
  </tr>
  <tr>
    <td><img src="screenshots/组织者申请管理界面.png" width="400" /></td>
    <td><img src="screenshots/组织者申请-AI分析.png" width="400" /></td>
  </tr>
</table>

### AI 智能功能

<table>
  <tr>
    <td align="center"><b>个性化推荐 Feed</b></td>
    <td align="center"><b>NL2SQL 自然语言查询</b></td>
  </tr>
  <tr>
    <td><img src="screenshots/为我推荐页面.png" width="400" /></td>
    <td><img src="screenshots/智能数据查询NL2SQL.png" width="400" /></td>
  </tr>
  <tr>
    <td align="center"><b>RAG 助手</b></td>
    <td align="center"><b>风控日志</b></td>
  </tr>
  <tr>
    <td><img src="screenshots/RAG助手页面.png" width="400" /></td>
    <td><img src="screenshots/风控日志页面.png" width="400" /></td>
  </tr>
</table>

### AI 运营中心

<table>
  <tr>
    <td align="center"><b>运营概览</b></td>
    <td align="center"><b>活动诊断</b></td>
  </tr>
  <tr>
    <td><img src="screenshots/AI运营中心-运营概览.png" width="400" /></td>
    <td><img src="screenshots/AI运营中心-活动诊断.png" width="400" /></td>
  </tr>
  <tr>
    <td align="center"><b>流失预警</b></td>
    <td align="center"><b>积分管理</b></td>
  </tr>
  <tr>
    <td><img src="screenshots/AI运营中心-流失预警.png" width="400" /></td>
    <td><img src="screenshots/AI运营中心-积分管理.png" width="400" /></td>
  </tr>
</table>

### 负反馈中心

<table>
  <tr>
    <td align="center"><b>举报管理</b></td>
    <td align="center"><b>信用管理</b></td>
  </tr>
  <tr>
    <td><img src="screenshots/负反馈中心-举报管理.png" width="400" /></td>
    <td><img src="screenshots/负反馈中心-信用管理.png" width="400" /></td>
  </tr>
  <tr>
    <td align="center"><b>惩罚记录</b></td>
    <td align="center"><b>申诉中心</b></td>
  </tr>
  <tr>
    <td><img src="screenshots/负反馈中心-惩罚记录.png" width="400" /></td>
    <td><img src="screenshots/负反馈中心-申诉中心.png" width="400" /></td>
  </tr>
  <tr>
    <td align="center"><b>黑名单</b></td>
  </tr>
  <tr>
    <td><img src="screenshots/负反馈中心-黑名单.png" width="400" /></td>
  </tr>
</table>

---

## License

MIT License © 2026 Annnnl1
