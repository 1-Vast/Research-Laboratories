# LabFlow · 科研实验室智能协同管理平台

面向高校科研实验室的前后端分离协同管理系统：科研项目立项与结项、任务分派与验收、周报汇报、资源预约、成果与知识管理，以及基于授权数据的 AI 辅助科研服务。

- 技术栈：**Vue 3 + TypeScript / Spring Boot 3.4 / Spring Security + JWT / PostgreSQL（本地 H2）**
- 当前进度：**第二阶段（科研业务闭环）已完成**——项目立项/结项、任务分派/交付/验收、周报三条工作流前后端打通，`mvn verify` 258 个用例全绿、行覆盖率 92.8%，真实 HTTP 端到端冒烟 72 项断言全通过
- 设计文档索引：[初步设计总览](docs/design/00-初步设计总览.md) · [架构设计](docs/design/01-架构设计.md) · [数据库设计](docs/design/02-数据库设计.md) · [权限设计](docs/design/03-权限设计.md) · [接口设计](docs/design/04-接口设计.md) · [实施计划](docs/design/05-实施计划.md)
- 演示与验证：[演示脚本](docs/演示脚本.md)（8 分钟走完全链路，含越权负例与讲解要点） · [测试说明](docs/testing.md)
- 进展记录：[log/](log/README.md)（按日期归档的开发日志与里程碑状态）

[![CI](https://github.com/1-Vast/Research-Laboratories/actions/workflows/ci.yml/badge.svg)](https://github.com/1-Vast/Research-Laboratories/actions/workflows/ci.yml)

![系统架构](docs/assets/architecture.png)

---

## 1. 项目简介

**题目**：科研实验室智能协同管理平台（LabFlow）
**一句话描述**：面向高校科研实验室，提供科研项目管理、团队任务协作、科研资源调度、研究进度跟踪、成果与知识管理及 AI 辅助科研服务，实现实验室科研活动的数字化、协同化与智能化管理。

**解决什么问题**

| 真实痛点 | 系统对应能力 |
| --- | --- |
| 项目推进分散在聊天记录、个人文档、表格中 | 项目立项/审批/结项、成员与进度 |
| 任务交付标准不一致、缺少验收轨迹 | 任务分派、提交快照、退回理由、验收记录 |
| GPU/工作站靠口头预约，时段重叠 | 资源登记、预约审批、半开区间冲突约束 |
| 论文笔记与实验结论无法检索复用 | 项目知识条目、成果附件与权限检索 |
| 人工汇总多人进展耗时 | 基于授权数据的 AI 周报草稿（人工确认后提交） |
| 跨项目共享账号导致数据越界 | 角色权限 + 实验室/项目作用域 + 数据归属校验 |

**目标用户**：高校科研实验室的系统管理员、实验室负责人（PI）、科研项目负责人、科研人员。

## 2. 团队与分工表

> ⚠️ 姓名、学号、贡献比例**必须由本人按真实情况填写**，分工需与 Git 提交记录一致。

| 姓名 | 学号 | 负责模块 | 主要文件/目录 | 贡献比例 |
| --- | --- | --- | --- | --- |
| 待填写 | 待填写 | 身份与授权（认证、RBAC）、工程规范、CI | `backend/src/main/java/com/labflow/identity/**`、`backend/src/main/resources/db/migration/V1__schema.sql`、`V2__seed_data.sql`、`.github/workflows/ci.yml` | 待填写 |
| 待填写 | 待填写 | 科研业务模块（项目/任务/周报）、前端业务页、端到端验证、文档校准 | `backend/src/main/java/com/labflow/research/**`、`backend/src/main/resources/db/migration/V3__research_core.sql`、`frontend/src/views/{Projects,ProjectDetail,Tasks,Reports}View.vue`、`frontend/src/api/research.ts`、`scripts/e2e_smoke.py`、`docs/**` | 待填写 |

> 若为 1 人组，请改为单人 100% 并注明；2 人组需保证每人都有后端业务代码与测试代码的提交。
>
> 当前提交分布（`git log --format='%ae' | sort | uniq -c`）：`13757415854@163.com` 30 次、`1978543960@qq.com` 9 次。**请按实际写比例**，不要为了好看凑成 50/50——评审会对着 `git shortlog -sn` 看。

## 3. 技术栈与实际版本

| 层次 | 技术 | 版本 |
| --- | --- | --- |
| 前端 | Vue | 3.5.13 |
|  | TypeScript / Vite / Vue Router / Axios | 5.7 / 6.4 / 4.5 / 1.7 |
| 后端 | JDK | 17（LTS） |
|  | Spring Boot / Spring Security | 3.4.13 / 6.4 |
|  | Spring Data JPA（Hibernate 6） | 3.4 |
|  | JJWT（HS256） | 0.12.6 |
|  | springdoc-openapi（Swagger UI） | 2.8.13 |
| 数据库 | PostgreSQL 14+（生产，已由集成测试验证） | 16（测试实例） |
|  | H2 2.x（本地开发，PostgreSQL 兼容模式） | 随 Boot 管理 |
| 迁移 | Flyway | 10.x |
| 测试 | JUnit 5 / Mockito / MockMvc / AssertJ / JaCoCo | 5.11 / 5.x / 6.x / 3.x / 0.8.12 |

> 版本以 `backend/pom.xml`、`frontend/package.json` 与构建日志为准。

## 4. 系统架构

- 架构图见文首图片，或 [`docs/design/01-架构设计.md`](docs/design/01-架构设计.md)。
- 后端为 **DDD 模块化单体**：`interfaces → application → domain`，`infrastructure` 实现领域端口；领域层不依赖 Spring MVC / JPA。
- 技术形态：`浏览器 → Vue 3 → (HTTP /api/v1) → Spring Boot（Security → Interfaces → Application → Domain → Infrastructure）→ PostgreSQL / H2`。
- 前端只负责交互，权限判断全部在后端。

### 4.1 后端包结构

```text
backend/src/main/java/com/labflow/
├── shared/            统一响应、异常、traceId、JWT、安全配置、审计、Clock
├── identity/          身份与授权
│   ├── domain/        聚合与仓储端口（User、Role、Permission、AuthSession、RefreshToken…）
│   ├── application/   用例编排（AuthService、UserAdminService、RoleAdminService、认证器）
│   ├── infrastructure/persistence/  JPA 实体、Spring Data 仓储、端口适配器
│   └── interfaces/rest/             Controller、DTO、VO、映射器
└── research/          科研业务（项目 / 任务 / 周报）
    ├── domain/        Project、Task、TaskSubmission、Report、ProjectReview 与状态机
    ├── application/   ProjectService、TaskService、ReportService、ProjectScopeService
    ├── infrastructure/persistence/  JPA 实体、复合主键、仓储适配器、只读目录适配器
    └── interfaces/rest/             ProjectController、TaskController、ReportController、LabController
```

`research` 不依赖 `identity` 的实体：判断"这个人是不是这个实验室的 PI / 成员"时，通过 `LabScopeRepository`、`UserDirectory` 这类端口读取，实现层用 `JdbcTemplate` 只查需要的两列，避免模块间实体耦合。

### 4.2 一次请求的认证时序

```mermaid
sequenceDiagram
    participant C as 浏览器
    participant F as JwtAuthenticationFilter
    participant A as AccessTokenAuthenticator
    participant DB as 数据库
    participant CT as Controller(@PreAuthorize)
    C->>F: GET /api/v1/admin/users + Bearer JWT
    F->>A: 解析并验证签名/issuer/exp
    A->>DB: 查 auth_sessions(sid) 是否吊销/过期
    A->>DB: 查 user + roles + permissions（enabled、auth_version）
    A-->>F: AuthPrincipal(userId, sessionId, authorities)
    F->>CT: 放行，写入 SecurityContext
    CT->>CT: hasAuthority('user:manage')
    CT-->>C: 200 或 403
```

要点：JWT **不携带权限**，只带 `sub/sid/av`；权限每次请求从数据库重载，因此改权限后下一次请求即生效；退出登录 / 禁用账号在服务端吊销会话，旧令牌立即 401。

## 5. RBAC 设计

### 5.1 角色列表

| 角色 | 代码 | 职责 |
| --- | --- | --- |
| 系统管理员 | `SYS_ADMIN` | 用户、角色、权限、实验室登记、PI 任命 |
| 实验室负责人 | `PI` | 审批立项/结项/预约，管理本实验室成员 |
| 科研项目负责人 | `PROJECT_LEADER` | 管理所负责项目的成员、任务、周报、成果审核 |
| 科研人员 | `RESEARCHER` | 承接任务、提交周报、申请资源、共享成果（注册默认角色） |

角色 = 能力，`lab_members` / `project_members` = 范围；两者分离。

### 5.2 角色 × 权限矩阵（节选，完整表见 [权限设计](docs/design/03-权限设计.md)）

| 权限码 | SYS_ADMIN | PI | PROJECT_LEADER | RESEARCHER |
| --- | :-: | :-: | :-: | :-: |
| `user:manage` / `role:manage` | ● | | | |
| `lab:create` / `lab:appoint` | ● | | | |
| `lab:read` | | ● | ● | ● |
| `lab:member:manage` | | ● | | |
| `project:create` / `project:update` / `project:delete` / `project:submit` | | | ● | |
| `project:read` | | ● | ● | ● |
| `project:approve` / `project:close-review` | | ● | | |
| `project:close-request` / `project:member:manage` | | | ● | |
| `task:create` / `task:update` / `task:delete` / `task:review` | | | ● | |
| `task:read` | | ● | ● | ● |
| `task:start` / `task:submit` | | | ● | ● |
| `report:create` / `report:update` / `report:delete` / `report:submit` | | | ● | ● |
| `report:read` | | ● | ● | ● |
| `report:review` | | ● | ● | |
| `resource:manage` / `booking:review` | | ● | | |
| `artifact:*` / `knowledge:*` | | 审核 | ● | ● |
| `audit:read` | ● | ● | | |

权限码共 **49 条**，全部入库（`permissions` 表），可在后台动态授予/撤销，不写死在代码里。其中 **26 条已被接口用 `@PreAuthorize` 强制校验**（其余为第二/三阶段预留，如 `artifact:*`、`booking:*`、`notification:*`、`ai:report:generate`）。

> **平台管理员不自动拥有科研数据权限**：`SYS_ADMIN` 只有 `user:manage`、`role:manage`、`lab:create`、`lab:appoint`、`audit:read`，没有 `project:read` / `task:read` / `report:read`。这是有意的——管账号的人不应该顺便能读科研正文。需要时由管理员把对应权限授予具体角色，走的是动态授权链路。

### 5.3 权限校验在代码中的位置

| 层 | 文件 | 作用 |
| --- | --- | --- |
| 过滤器 | `shared/security/JwtAuthenticationFilter`、`identity/application/AccessTokenAuthenticator` | 令牌 + 会话 + 账号状态校验，装载权限 |
| 方法级 | `interfaces/rest/AdminUserController`（`hasAuthority('user:manage')`）、`AdminRoleController`（`hasAuthority('role:manage')`） | 接口级强制校验，无权限 403 |
| 服务级 | `UserAdminService`、`RoleAdminService` | 最后管理员保护、内置角色保护、角色引用检查 |
| 领域级 | `User.disable/changePassword` 递增 `auth_version`；`Role.ensureDeletable` | 不变量由聚合维护 |

## 6. 数据库设计

- ER 图（Mermaid）与完整表字典：[`docs/design/02-数据库设计.md`](docs/design/02-数据库设计.md)
- 迁移脚本：`V1__schema.sql`（身份与授权 10 张表）、`V2__seed_data.sql`（种子数据）、`V3__research_core.sql`（科研业务 7 张表），Flyway 按版本号顺序执行
- 共 **17 张表**，课程要求 ≥8

**身份与授权（10 张）**

| 表 | 用途 |
| --- | --- |
| `users` | 用户与密码哈希、启用状态、授权版本 |
| `roles` | 角色（4 个内置角色，`protected` 防误删） |
| `permissions` | 权限目录（49 个接口操作码） |
| `user_roles` | 用户-角色多对多 |
| `role_permissions` | 角色-权限多对多（权限动态来源） |
| `auth_sessions` | 服务端会话（退出即吊销） |
| `refresh_tokens` | 刷新令牌哈希（一次性轮换、重放检测） |
| `labs` | 实验室 |
| `lab_members` | 实验室成员与 PI 关系（作用域来源） |
| `audit_events` | 脱敏操作审计（不含正文与令牌） |

**科研业务（7 张，V3）**

| 表 | 用途 | 关键约束 |
| --- | --- | --- |
| `projects` | 项目本体、状态、负责人、结项说明、`version` | `ck_projects_status` 限定 6 个状态；`lab_id` 外键 |
| `project_members` | 项目成员（LEADER / MEMBER） | 复合主键 `(project_id, user_id)` |
| `project_reviews` | 立项/结项审批轨迹，只增不改 | `review_type ∈ {INIT, CLOSE}` |
| `tasks` | 任务、受派人、状态、`version` | `assignee_id` 外键；状态检查约束 |
| `task_submissions` | 交付快照，退回重提不覆盖历史 | `uq_task_submissions_no (task_id, submission_no)` |
| `reports` | 周报正文、状态、审核人、`version` | `uq_reports_week (project_id, author_id, week_start)` |
| `report_tasks` | 周报关联任务 | 复合主键 `(report_id, task_id)` |

两处唯一约束是**业务规则的数据库兜底**，不是可选装饰：`uq_reports_week` 保证同一人在同一项目同一周只有一份周报（周起始已在服务层归一到周一，否则 `2026-11-25` 与 `2026-11-23` 会被当成两周）；`uq_task_submissions_no` 保证快照编号不重，退回重提必然产生新编号。

## 7. 功能清单

### 7.1 已实现

**身份与授权**

| 角色 | 功能 |
| --- | --- |
| 访客 | 注册（用户名唯一、密码强度后端校验、默认 `RESEARCHER`）、登录 |
| 所有登录用户 | 查看当前用户与权限、退出登录（服务端吊销）、刷新令牌轮换、**自助改密（吊销全部会话）** |
| 系统管理员 | 用户分页/搜索/排序、创建、编辑、启用/禁用、分配角色、逻辑删除；角色增删改查、分配权限；权限目录查询；审计日志查询 |
| 审计查看者（`audit:read`） | 管理员看全部脱敏记录；PI 仅看到与本人相关的记录 |
| 安全防护 | 登录失败限流（5 次 / 10 分钟 → 429）、Cookie 接口来源校验、越权与登录失败独立落库审计、数据库控制台仅 dev 放行 |

**科研业务（第二阶段）**

| 模块 | 功能 |
| --- | --- |
| 实验室 | `GET /labs` 只返回当前用户所属实验室，不暴露全平台组织架构 |
| 项目 | 草稿创建、编辑、删除（仅草稿可删）、提交立项、PI 审批/驳回（驳回必须填理由）、驳回后重新编辑再提交、申请结项、结项审批/退回、归档；成员增删；进度统计；审批轨迹查询；**当前用户能力查询**（`/permissions`，前端不自行推断 PI 关系） |
| 任务 | 项目内建任务并分派、编辑、删除（仅 `TODO` 可删）、受派人开始、提交交付、验收通过/退回、取消（必须填理由）、交付快照历史查询、「我的任务」聚合 |
| 周报 | 新建草稿、编辑、删除（仅草稿可删）、提交、审核通过/退回、关联本项目任务、同周唯一 |

**三条状态机（领域层强制，非法迁移一律 409 `INVALID_STATE_TRANSITION`）**

```text
项目  DRAFT ──submit──> PENDING ──approve──> ACTIVE ──close-request──> CLOSING ──close-approve──> ARCHIVED
         ↑                  │                   │                          │
         └──reopen──────────┘（驳回 REJECTED）   └── 仅 ACTIVE/CLOSING 可维护成员
                                                └── 存在未完成任务时不允许申请结项

任务  TODO ──start──> IN_PROGRESS ──submit──> SUBMITTED ──approve──> DONE
        │                    ↑                     │
        │                    └──────start────── RETURNED（退回必须填理由）
        └──cancel──> CANCELED（取消不计入进度）

周报  DRAFT ──submit──> SUBMITTED ──approve──> APPROVED
         ↑                   │
         └──update───────────┘（退回后回到 DRAFT，可改再提；提交后正文只读）
```

### 7.2 已知未完成（如实列出）

- 资源预约、成果附件、知识库、通知、AI 周报草稿属第三阶段，**当前仓库尚未实现**；对应权限码已在库中预留，但接口未建；
- 前端共 10 个页面（登录、注册、工作台、用户管理、角色权限、审计日志、项目列表、项目详情、任务、周报），**尚未做浏览器自动化 E2E**，本轮验证方式是构建 + 真实 HTTP 脚本；
- 未做 Docker Compose、Redis / MinIO / WebSocket（第三阶段）；
- 登录限流为进程内固定窗口（重启清零、多实例不共享），第三阶段换 Redis 计数；
- 并发控制用乐观锁（`version` 字段），高并发下的重试策略未实现，冲突直接返回 409 由前端提示刷新；
- 审计覆盖身份、授权与科研业务主操作（立项/审批/结项/任务流转/周报审核），资源与成果类操作待第三阶段补齐。

## 8. 接口文档

启动后端后：

- Swagger UI：`http://localhost:8080/swagger-ui.html`
- OpenAPI JSON：`http://localhost:8080/v3/api-docs`
- 接口清单与错误码：[`docs/design/04-接口设计.md`](docs/design/04-接口设计.md)

主要接口（共 **57 个**，完整字段见 Swagger）：

**认证与自助（6）**

| 方法 | 路径 | 权限 |
| --- | --- | --- |
| POST | `/api/v1/auth/register` | 匿名 |
| POST | `/api/v1/auth/login` | 匿名 |
| POST | `/api/v1/auth/refresh` | 刷新 Cookie |
| POST | `/api/v1/auth/logout` | 已认证 |
| GET | `/api/v1/me` | 已认证 |
| PUT | `/api/v1/me/password` | 已认证（改密后全部会话失效） |

**用户与角色（15）**

| 方法 | 路径 | 权限 |
| --- | --- | --- |
| GET/POST | `/api/v1/admin/users`（GET 支持 `keyword`、`sort`、`page`、`size`） | `user:manage` |
| GET/PATCH/DELETE | `/api/v1/admin/users/{id}` | `user:manage` |
| PATCH | `/api/v1/admin/users/{id}/enabled` | `user:manage` |
| PUT | `/api/v1/admin/users/{id}/roles` | `user:manage` |
| GET/POST | `/api/v1/admin/roles`（GET 支持 `keyword`、`sort`、`page`、`size`） | `role:manage` |
| GET/PATCH/DELETE | `/api/v1/admin/roles/{id}` | `role:manage` |
| PUT | `/api/v1/admin/roles/{id}/permissions` | `role:manage` |
| GET | `/api/v1/admin/permissions` | `role:manage` |
| GET | `/api/v1/admin/audit-events`（`action`、`actorId`、`page`、`size`） | `audit:read` |

**实验室（1）**

| 方法 | 路径 | 权限 |
| --- | --- | --- |
| GET | `/api/v1/labs` | `lab:read`（只返回本人所属实验室） |

**项目（17）**

| 方法 | 路径 | 权限 |
| --- | --- | --- |
| GET | `/api/v1/projects`（`labId`、`status`、`keyword`、`sort`、`page`、`size`） | `project:read` |
| POST | `/api/v1/projects` | `project:create` |
| GET | `/api/v1/projects/{id}` | `project:read` |
| PUT / DELETE | `/api/v1/projects/{id}` | `project:update` / `project:delete` |
| POST | `/api/v1/projects/{id}/submit` | `project:submit` |
| POST | `/api/v1/projects/{id}/approve` · `/reject` | `project:approve` |
| POST | `/api/v1/projects/{id}/reopen` | `project:update` |
| POST | `/api/v1/projects/{id}/close-request` | `project:close-request` |
| POST | `/api/v1/projects/{id}/close-approve` · `/close-reject` | `project:close-review` |
| GET / POST | `/api/v1/projects/{id}/members` | `project:read` / `project:member:manage` |
| DELETE | `/api/v1/projects/{id}/members/{userId}` | `project:member:manage` |
| GET | `/api/v1/projects/{id}/reviews` | `project:read` |
| GET | `/api/v1/projects/{id}/permissions` | `project:read` |

**任务（11）**

| 方法 | 路径 | 权限 |
| --- | --- | --- |
| GET | `/api/v1/tasks/mine` | `task:read` |
| GET / POST | `/api/v1/projects/{projectId}/tasks` | `task:read` / `task:create` |
| GET / PUT / DELETE | `/api/v1/tasks/{id}` | `task:read` / `task:update` / `task:delete` |
| POST | `/api/v1/tasks/{id}/start` | `task:start`（仅受派人本人） |
| POST | `/api/v1/tasks/{id}/submit` | `task:submit`（仅受派人本人） |
| POST | `/api/v1/tasks/{id}/review` | `task:review`（不得验收自己提交的任务） |
| POST | `/api/v1/tasks/{id}/cancel` | `task:update` |
| GET | `/api/v1/tasks/{id}/submissions` | `task:read` |

**周报（7）**

| 方法 | 路径 | 权限 |
| --- | --- | --- |
| GET / POST | `/api/v1/projects/{projectId}/reports` | `report:read` / `report:create` |
| GET / PUT / DELETE | `/api/v1/reports/{id}` | `report:read` / `report:update` / `report:delete` |
| POST | `/api/v1/reports/{id}/submit` | `report:submit` |
| POST | `/api/v1/reports/{id}/review` | `report:review`（不得审核自己的周报） |

统一返回：`{code, message, data, traceId}`；分页：`{items, total, page, size}`；排序：`sort=field[,asc|desc]`（白名单字段，非法值 400）；登录失败过多或频繁请求返回 429；状态机非法迁移与乐观锁冲突均为 409，用 `code` 区分（`INVALID_STATE_TRANSITION` / `VERSION_CONFLICT`）。

> 权限码只解决"能不能做"，"在哪个范围内做"由应用服务判定：项目类接口按 `project_members` 校验成员关系，审批类接口按 `lab_members` 校验 PI 关系，任务/周报类接口先按 `taskId`/`reportId` 反查所属项目再校验，**避免只改 URL 里的 id 就能读到别人的数据**。

## 9. 本地运行（15 分钟内可完成）

### 9.1 环境要求

| 依赖 | 版本 | 说明 |
| --- | --- | --- |
| JDK | 17+ | `java -version` 确认 |
| Node.js | 20+ | `node -v` 确认 |
| 数据库 | **无需安装** | 默认使用 H2 文件库，数据落在 `backend/data/labflow.mv.db`；生产 PostgreSQL 已由集成测试验证 |

> 若使用 PostgreSQL：把 profile 切到 `prod` 并设置 `DB_URL/DB_USER/DB_PASSWORD`，Flyway 会自动执行同一套迁移脚本（`V1__schema.sql`、`V2__seed_data.sql`）。

### 9.2 启动后端

```bash
cd backend
# Windows
mvnw.cmd spring-boot:run
# macOS / Linux
./mvnw spring-boot:run
```

首次启动会自动下载依赖并执行 Flyway 迁移，日志出现 `Started LabFlowApplication` 即成功。
默认端口 8080；如需修改：`--server.port=8081` 或设置环境变量 `SERVER_PORT`。

健康检查：`curl http://localhost:8080/actuator/health` → `{"status":"UP"}`

### 9.3 启动前端

```bash
cd frontend
npm install
npm run dev
```

打开 `http://localhost:5173`，用下方演示账号登录。Vite 已将 `/api` 代理到 `http://127.0.0.1:8080`。

### 9.4 生产配置（密钥）

```bash
cp .env.example .env      # 按需填写
export JWT_SIGNING_KEY="$(openssl rand -base64 48)"   # 生产必须固定且保密
```

未设置 `JWT_SIGNING_KEY` 时后端会生成本次启动专用的随机密钥（重启后旧令牌失效），仅适用于本地开发。
数据库、Redis、MinIO 等配置项见 `.env.example`，**任何真实密钥都不得提交到仓库**。

### 9.5 重置本地数据

停止后端后删除 `backend/data/` 目录，再次启动即按迁移脚本重建。

## 10. 演示账号

| 用户名 | 密码 | 角色 | 用途 |
| --- | --- | --- | --- |
| `admin_demo` | `Labflow@2026` | SYS_ADMIN | 用户/角色/权限管理、动态授权演示（无科研数据权限） |
| `pi_demo` | `Labflow@2026` | PI | 实验室 A 负责人：立项/结项审批、周报审核 |
| `leader_demo` | `Labflow@2026` | PROJECT_LEADER | 实验室 A 项目负责人：建项目、分派任务、验收 |
| `researcher_a` | `Labflow@2026` | RESEARCHER | 实验室 A 科研人员：承接任务、写周报 |
| `researcher_b` | `Labflow@2026` | RESEARCHER | 实验室 B，跨实验室越权负例对照 |

账号均为**虚构演示数据**，密码以 BCrypt 哈希存储于种子脚本，禁止用于生产环境。

> 演示提示：同一账号连续输错 5 次会触发 10 分钟限流（429）。课堂上误触时重启后端即可清零（计数在进程内），
> 或等窗口过期；不要为了演示方便关闭限流。

## 11. 测试说明

### 11.1 一键运行

```bash
cd backend
mvnw.cmd test        # macOS/Linux: ./mvnw test
mvnw.cmd verify      # 生成 JaCoCo 覆盖率报告：target/site/jacoco/index.html
```

端到端冒烟（真实 HTTP + 真实数据库，需先起 dev 服务）：

```bash
cd backend && mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev   # 终端 1
python scripts/e2e_smoke.py                                             # 终端 2
```

### 11.2 结果（实测）

- **258 个用例，全部通过**：MockMvc 接口级 93 个、领域/服务/工具单元 159 个、**真实 PostgreSQL 集成 6 个**
- **真实 PostgreSQL 16 集成测试**：测试启动临时实例，执行与生产完全相同的 Flyway 脚本，验证建表、唯一约束、会话吊销、动态权限与审计
- **整体行覆盖率 92.8%**（2154/2321 行）；JaCoCo 门禁整体 ≥85% 已在 CI 中强制
- **端到端冒烟 71 项断言全通过**：五账号真实登录、项目/任务/周报三条工作流走完、交付快照不覆盖历史、审批轨迹 2 条、审计动作齐全
- 覆盖必测场景：注册重名、弱密码、登录成功/失败（统一提示）、**退出后旧令牌 401**、无权限 403、账号禁用、刷新令牌重放、**动态权限生效**、最后一个管理员保护、内置角色/被引用角色删除、**登录限流 429**、**来源校验**、**越权与失败登录审计留痕**、排序与分页边界；**业务侧**：草稿态建任务、过期版本 409、非成员读详情 403、负责人自批 403、非受派人开始任务 403、退回后重提快照历史、同周重复周报 409、提交后正文只读、存在未完成任务不允许结项
- CI：GitHub Actions 每次推送执行 `mvnw verify` + 前端构建，[最近一次运行](https://github.com/1-Vast/Research-Laboratories/actions/workflows/ci.yml)

![覆盖率摘要](docs/assets/coverage-summary.png)

详细用例清单见 [`docs/testing.md`](docs/testing.md)。

## 12. 附加分申请（如实说明）

第二阶段完成后，申请以下三项：**架构升级（DDD）**、**安全增强**、**自动化测试进阶（部分）**。其余项未实现，不申请。

| 附加项 | 当前状态 | 证据 / 说明 |
| --- | --- | --- |
| 架构升级（DDD）+2 | **已具备** | 两个业务模块均按 `interfaces → application → domain ← infrastructure` 分层；领域层零 Spring 依赖（`Project`/`Task`/`Report` 为纯 POJO，可脱离容器单测）；跨模块只读访问通过端口（`LabScopeRepository`、`UserDirectory`）而非直接依赖对方实体 |
| 安全增强 +1 | **已实现，申请** | ① 刷新令牌一次性轮换 + 重放检测吊销；② 登录失败限流 5 次/10 分钟 → 429；③ Cookie 接口来源校验；④ 退出登录服务端吊销会话，旧令牌立即 401；⑤ JWT 不携带权限，每次请求重载，改权限即时生效；⑥ **三层授权**（角色能力 + 实验室作用域 + 项目成员归属），任务/周报接口按 id 反查归属，防水平越权；⑦ 自审拦截（负责人不能批自己的项目、不能验收自己提交的任务、不能审核自己的周报）；⑧ 审计脱敏且按作用域收敛（PI 只看与自己相关的记录） |
| 自动化测试进阶 +1 | **部分申请** | 258 个用例 + 92.8% 行覆盖率门禁 + 真实 PostgreSQL 集成 + 72 项真实 HTTP 端到端断言；**未做 Playwright 浏览器 E2E**，此项按半档申报 |
| WebSocket 实时通知 +2 | 未开始 | 第三阶段：STOMP + 认证 + outbox 幂等，双窗口演示 |
| 外部系统（MinIO/SMTP）+1.5 | 未开始 | 第三阶段：附件私有桶、授权下载、邮件提醒 |
| AI 集成 +1 | 未开始 | 第三阶段：后端适配器 + 授权数据 + 来源校验 |
| Redis +0.5 | 未开始 | 第三阶段：吊销标志、缓存、限流 |
| 部署 +0.5 | 未开始 | 第三阶段：Docker Compose |
| 无障碍 +0.5 | 部分（表单 label、焦点样式、状态用文字而非仅颜色区分） | 第三阶段：键盘全流程与读屏检查 |

> 未列出的项视为不申请；最终分值以教师评定为准。

## 13. 第三方代码声明

本项目**未使用** RuoYi、JeecgBoot、Pig 等全栈后台脚手架，权限、登录与业务代码均为自行实现。

使用到的开源库（均为依赖形式引入，未修改源码）：

| 库 | 版本 | 用途 | 许可证 |
| --- | --- | --- | --- |
| Spring Boot / Spring Security / Spring Data JPA | 3.4.13 | Web 框架、认证授权、持久化 | Apache-2.0 |
| JJWT | 0.12.6 | JWT 签发与校验 | Apache-2.0 |
| Flyway | 10.x | 数据库版本迁移 | Apache-2.0 |
| springdoc-openapi | 2.8.13 | 接口文档 | Apache-2.0 |
| H2 Database | 2.x | 本地开发数据库 | MPL-2.0 / EPL-1.0 |
| PostgreSQL JDBC | 42.x | 生产数据库驱动 | BSD-2-Clause |
| Vue / Vue Router / Vite / Axios | 见 package.json | 前端框架与构建 | MIT |
| JUnit 5 / Mockito / AssertJ / JaCoCo | 见 pom.xml | 测试与覆盖率 | EPL-2.0 / MIT / Apache-2.0 |

参考设计（未复制代码）：项目书第 13–18 节的 DDD 分层与表结构设计。

## 14. AI 使用说明与核心 Prompt

- 工具：DeepSeek Harness 编码 Agent（模型 `deepseek-flash`），用于设计文档、后端认证与 RBAC、科研业务三条工作流、前端页面、测试与端到端脚本生成。
- 完整记录（含任务级提示词原文、项目书 §39 模块提示词、生成后的修正记录、待人工确认事项）：[`docs/prompts.md`](docs/prompts.md)
- 关键约束（写入提示词）：权限必须后端校验、密码 BCrypt、退出服务端失效、权限动态生效、不得提交密钥、只使用虚构数据；业务侧额外要求——状态机非法迁移必须 409 而非静默成功、交付快照不得覆盖历史、按 id 访问必须先反查归属再校验成员关系。
- **学生需自行完成**：逐文件理解代码、按实际填写分工表、在干净环境按 README 复现一次、完成第三阶段业务代码，以便答辩时解释自己提交的内容。

---

## 目录结构

```text
.
├── backend/                 Spring Boot 后端（DDD 模块化单体）
│   ├── src/main/java/com/labflow/{shared,identity,research}
│   ├── src/main/resources/{application*.yml, db/migration/*.sql}
│   └── src/test/java/com/labflow/**          258 个用例
├── frontend/                Vue 3 + TypeScript 前端（10 个页面）
│   └── src/{api,views,components,router,stores,utils}
├── docs/
│   ├── design/              初步设计（总览、架构、数据库、权限、接口、计划）
│   ├── assets/              架构图、覆盖率摘要
│   ├── 演示脚本.md          答辩演示流程、负例与讲解要点
│   ├── testing.md           测试清单与结果
│   └── prompts.md           AI 使用记录与核心 prompt
├── log/                     项目进展日志（按日期归档、里程碑状态、决策与证据）
├── .github/workflows/       CI：后端 verify（含覆盖率门禁）+ 前端构建
├── scripts/                 文档配图生成 + e2e_smoke.py（真实 HTTP 端到端冒烟）
├── .env.example             环境变量模板（无真实密钥）
└── README.md
```

## 开发约定

- 分支：`main` 为稳定分支，功能开发使用 `feat/xxx`；2 人组通过 Pull Request 合并并互相 review。
- 提交信息：`feat(backend): ...`、`feat(rbac): ...`、`fix(auth): ...`、`test(...)`、`docs: ...`，禁止 `update`/`fix`/`111`。
- 禁止提交：`node_modules/`、`target/`、`dist/`、`data/`、`.env`、IDE 配置与任何真实密钥。
