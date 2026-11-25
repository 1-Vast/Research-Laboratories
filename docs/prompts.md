# AI 使用说明与核心 Prompt

## 1. 使用的 AI 工具

| 项 | 内容 |
| --- | --- |
| 工具 | DeepSeek Harness（编码 Agent，模型 `deepseek-flash`） |
| 使用阶段 | 第一阶段（可运行底座）：设计文档、后端认证与 RBAC、前端页面、测试 |
| 使用日期 | 2026-11-25 |
| 输入材料 | `doc/期末项目任务书.docx`、`doc/科研实验室智能协同管理平台.docx`（项目书 V1.1） |
| 产出范围 | `backend/` 全部 Java 代码与迁移脚本、`frontend/` 全部页面、`docs/` 设计文档 |
| 人工确认 | 每位成员必须逐文件复核并在答辩中解释自己负责的模块；本文末尾列出待人工确认项 |

## 2. 任务级提示词（原文）

```text
https://github.com/1-Vast/Intelligent-Collaborative-Management-Platform-for-Scientific-Research-Laboratories.git
@doc\科研实验室智能协同管理平台.docx 以及这个项目书，完成初步设计，并把第一阶段代码上传到该仓库；
@doc\期末项目任务书.docx 要求在这边
```

## 3. 模块级提示词（项目书 §39 定义，本次按此实施）

### P01 工程搭建与数据库

```text
按项目书第 13–18 节搭建 Vue 3、Spring Boot、PostgreSQL 仓库。读取现有依赖及 ER 图，生成 frontend、
backend、docs 所需目录和配置。实现 schema.sql、data.sql、主外键与唯一约束，支持本地一键初始化，
准备四角色虚构账号。输出初始化、清洁库复跑方法以及脚本验证结果。
```

实施结果：Flyway `V1__schema.sql`（10 表）+ `V2__seed_data.sql`（4 角色 / 49 权限 / 5 账号 / 2 实验室）；
本地 H2 文件库与 PostgreSQL 共用同一套脚本。

### P02 注册登录与退出

```text
实现 AUTH-01、AUTH-02。检查认证 DTO 和用户表，实现用户名唯一、密码至少 6 位且含字母数字、BCrypt、
默认 RESEARCHER、统一登录失败信息和过期 JWT。JWT 关联数据库会话 sid，退出持久化吊销当前会话。
新增含重复注册、登录成功/失败、过期和退出旧 JWT 返回 401 的断言，说明密码字段隔离和事务边界。
```

实施结果：`AuthService`、`AuthController`、`AccessTokenAuthenticator`、`JwtService`、`AuthSession`；
密码策略取项目书建议的 8 位下限（≥ 课程要求的 6 位）；退出 → `SESSION_REVOKED` 401；
`AuthApiTest` 14 个用例。

### P03 RBAC 与数据范围

```text
依据第 6–8、19 节实现五张 RBAC 表、用户和角色管理、当前用户权限接口。Spring Security 校验独立权限码，
Service 检查 lab/project 成员关系、负责人和作者。列表先按作用域过滤，再计算分页总量。实现角色权限变更后
刷新或重登生效，测试同角色跨项目、改他人 ID、无权限 403、用户禁用及管理接口。
```

实施结果：`AdminUserController` / `AdminRoleController` + `UserAdminService` / `RoleAdminService`；
权限每次请求从数据库加载（同一令牌即时生效）；最后一个管理员与内置角色保护；
`RbacApiTest` 14 个用例。lab/project 成员关系校验随第二阶段业务接口落地。

## 4. AI 生成后的人工/自动修正记录

| 问题 | 处理 |
| --- | --- |
| `User.updateProfile` 漏传时间参数导致编译失败 | 修正为 `updateProfile(name, email, now)` |
| JWT 单元测试使用 2026-01-01 固定时钟，令牌被真实时间判为过期 | 测试基准时间改为 `Instant.now()` |
| 用户名归一化测试用例带前后空格，被 DTO 正则先拦截 | 改为仅大小写归一化，空白由 DTO 校验拒绝（符合设计） |
| 覆盖率图表首次渲染数字颜色未设置导致不可见 | 修正绘图脚本后重新生成 |

## 5. 待人工确认事项（学生须自行完成）

1. README 分工表中的姓名、学号、贡献比例需按实际情况填写；
2. 逐文件理解代码，尤其是 `AccessTokenAuthenticator`、`AuthService.refresh`、`SecurityConfig` 三处安全关键逻辑；
3. 在本机按 README 步骤从零跑一遍建库、启动、演示、测试；
4. 自己动手完成第二、三阶段业务代码，避免答辩时无法解释提交内容；
5. 若教师要求，补充截图（Swagger、覆盖率 HTML、演示界面）。
