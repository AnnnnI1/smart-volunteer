# 智慧志愿 · 前端

> 基于 Vue 3 + Element Plus + Vite 构建的智能志愿者管理平台前端。

## 技术栈

- **框架**：Vue 3.5 (Composition API)
- **UI**：Element Plus 2.9
- **状态管理**：Pinia
- **路由**：Vue Router 4
- **图表**：ECharts 5
- **构建工具**：Vite 6
- **HTTP**：Axios

## 主要页面

| 路由 | 页面 | 说明 |
|------|------|------|
| `/homepage/dashboard` | 仪表盘 | 角色差异化首页（管理员/组织者/志愿者） |
| `/homepage/activities` | 活动大厅 | 报名、搜索、查看进度 |
| `/homepage/activity/:id` | 活动详情 | 报名/取消/签到 |
| `/homepage/profile` | 个人中心 | 技能、积分、服务时长 |
| `/homepage/admin/activities` | 活动管理 | CRUD、状态流转、签到码 |
| `/homepage/admin/ai` | AI 运营中心 | 流失预警、KNN推荐、活动诊断、积分管理 |
| `/homepage/admin/nl2sql` | 智能数据查询 | 自然语言→SQL |
| `/homepage/admin/users` | 用户管理 | 角色升降级 |

## 启动

```bash
npm install
npm run dev
```

## 代理配置

Vite 开发代理：`/api` → `http://localhost:9090`（网关）
