# LabFlow · 科研实验室智能协同管理平台

面向高校科研实验室的前后端分离协同管理系统（Vue 3 + Spring Boot + PostgreSQL）。
覆盖科研项目立项与结项、任务分派与验收、周报汇报、资源预约、成果知识管理与 AI 辅助科研。

> 当前状态：**第一阶段（可运行底座）开发中**。详细说明见 [`docs/`](docs/) 与本 README 后续章节。

## 仓库结构

```
backend/     Spring Boot 3 后端（DDD 模块化单体，Spring Security + JWT，JPA）
frontend/    Vue 3 + TypeScript 前端（Vite）
docs/        初步设计、数据库设计、接口文档、测试与 AI 使用记录
doc/         课程原始材料（本地保留，不纳入版本库）
```

## 快速开始

见 [README 的「本地运行」章节](#本地运行)（待第二阶段补全）。

## 文档索引

| 文档 | 内容 |
| --- | --- |
| [docs/design/00-初步设计总览.md](docs/design/00-初步设计总览.md) | 设计目标、范围、阶段划分 |
| [docs/design/01-架构设计.md](docs/design/01-架构设计.md) | 分层架构、模块边界、认证时序 |
| [docs/design/02-数据库设计.md](docs/design/02-数据库设计.md) | ER 关系、表字典、约束与索引 |
| [docs/design/03-权限设计.md](docs/design/03-权限设计.md) | 角色模型、权限矩阵、作用域校验 |
| [docs/design/04-接口设计.md](docs/design/04-接口设计.md) | REST API 清单、错误码、返回格式 |
| [docs/design/05-实施计划.md](docs/design/05-实施计划.md) | 三阶段路线与验收证据 |
