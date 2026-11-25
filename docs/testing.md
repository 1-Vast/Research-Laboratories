# 测试说明

## 1. 一键运行

```bash
cd backend
# Windows
mvnw.cmd verify
# macOS / Linux
./mvnw verify
```

`verify` 会依次执行测试、生成 JaCoCo 报告并执行**覆盖率门禁**（整体行覆盖率 ≥ 85%，`identity.application` 包 ≥ 80%，不达标即失败）。
报告位置：`target/site/jacoco/index.html`。

> 本机验证使用 JDK 17 + Maven 3.9.11；CI 使用 GitHub Actions（Ubuntu + Temurin 17），每次推送自动执行同一命令。
> 测试使用 H2 内存库并执行**真实 Flyway 迁移脚本**，因此同时验证了 DDL 与种子数据的可用性。

## 2. 测试结果（实测）

| 指标 | 数值 |
| --- | --- |
| 有效用例数 | **102** |
| 失败 / 错误 | 0 |
| 接口级（MockMvc + 真实过滤器链）用例 | 54（认证 14 + RBAC 14 + 安全加固 10 + 管理维护 9 + 自助改密 7） |
| 真实 PostgreSQL 集成用例 | 6（临时拉起的 PostgreSQL 16，执行与生产相同的 Flyway 迁移） |
| 领域 / 服务单元用例 | 42（认证服务 8、限流 5、请求级认证 5、JWT 6、密码策略 6、聚合 7、排序参数 5） |
| 整体行覆盖率 | **92.2%** |
| `identity.application` 包行覆盖率 | 91.9%（最低类 `UserAdminService` 86.9%） |
| 覆盖率门禁 | JaCoCo `check`：整体 ≥ 85%、application 包 ≥ 80% —— 全部满足 |

![覆盖率摘要](assets/coverage-summary.png)

## 3. 用例清单

### 3.1 认证接口 `AuthApiTest`（14）

| # | 场景 | 关键断言 |
| --- | --- | --- |
| 1 | 注册成功 | 201；默认 `RESEARCHER`；响应无 `password`/`passwordHash`；traceId 非空 |
| 2 | 注册重名 | 409 `DUPLICATE_USERNAME` |
| 3 | 用户名大小写归一化 | 大写重名同样 409 |
| 4 | 弱密码（长度不足） | 400 `VALIDATION_FAILED` |
| 5 | 弱密码（无数字） | 400，提示「密码必须同时包含字母和数字」 |
| 6 | 非法用户名 | 400 且返回字段级错误 |
| 7 | 登录成功 | 200；access token；HttpOnly `labflow_refresh`；权限含 `report:create` |
| 8 | 密码错误 | 401 `INVALID_CREDENTIALS`，提示统一 |
| 9 | 用户不存在 | 与密码错误完全一致 |
| 10 | 未携带令牌 | 401 `UNAUTHENTICATED` |
| 11 | 伪造令牌 | 401 `TOKEN_INVALID` |
| 12 | 当前用户 | 200，返回角色、权限、授权版本 |
| 13 | **退出后旧令牌** | 204；同一令牌再访问 `/me` → 401 `SESSION_REVOKED` |
| 14 | **刷新轮换与重放** | 刷新成功；旧刷新令牌重放 → 401 且会话吊销 |

### 3.2 RBAC 接口 `RbacApiTest`（14）

| # | 场景 | 关键断言 |
| --- | --- | --- |
| 1 | 普通用户调用 `/admin/**` | 403（users / roles / permissions） |
| 2 | 管理员分页 + 关键字筛选 | total 正确、page/size 回显 |
| 3 | 管理员创建用户并分配角色 | 201；新用户可登录且角色为 `PI` |
| 4 | 替换用户角色集合 | 200，角色数为 2 |
| 5 | 分配未知角色 | 400 `VALIDATION_FAILED` |
| 6 | **禁用账号** | 已有令牌立即 401；再登录统一失败提示 |
| 7 | **最后一个管理员** | 禁用自己 → 409 `LAST_ADMIN_PROTECTED` |
| 8 | **动态权限生效** | 新角色 0 权限 → 授予 → 同一令牌下一次请求可见 |
| 9 | 内置角色删除 | 409 `PROTECTED_ROLE` |
| 10 | 被引用角色删除 | 409 `ROLE_IN_USE` |
| 11 | 角色编码重复 | 409 `DUPLICATE_ROLE_CODE` |
| 12 | 权限目录 | ≥ 40 条，含 `project:approve`、`task:review` |
| 13 | 参数校验 | 400 且字段级错误 |
| 14 | 未知路径 | 404 `NOT_FOUND`（不是 500） |

### 3.3 安全加固 `SecurityHardeningApiTest`（10）

| # | 场景 | 关键断言 |
| --- | --- | --- |
| 1 | 连续失败达到阈值 | 第 6 次返回 429 `RATE_LIMITED`；此后正确口令也先拒绝 |
| 2 | 成功登录清零 | 4 次失败 + 成功 + 1 次失败仍为 401（未触发 429） |
| 3 | 刷新接口来源校验 | 跨站 `Origin` 被 CORS 拒绝；伪造 `Referer` 返回 403 `FORBIDDEN`；令牌未被消耗 |
| 4 | 退出接口来源校验 | 恶意来源 403，令牌仍然有效 |
| 5 | 数据库控制台 | 非 dev profile 下 `/h2-console` 返回 401 |
| 6 | **越权留痕** | 403 后审计含 `security.access.denied`，actorId 为越权用户 |
| 7 | **登录失败留痕** | 审计含 `auth.login.failed`，metadata 指向尝试的用户名 |
| 8 | 审计作用域 | PI 只能看到本人记录（actorId 全为自己） |
| 9 | 审计权限 | 无 `audit:read` 的用户访问审计 → 403 |
| 10 | 缺 Cookie 刷新 | 来源合法但仍返回 401 `TOKEN_INVALID` |

### 3.4 管理接口维护 `AdminApiTest`（9）

| # | 场景 | 关键断言 |
| --- | --- | --- |
| 1 | 用户列表降序排序 | 结果按用户名降序 |
| 2 | 角色列表升序排序 | 结果按角色码升序 |
| 3 | 非白名单排序字段 | 400 `VALIDATION_FAILED`（拒绝 `passwordHash`） |
| 4 | 非法排序方向 | 400 |
| 5 | 分页边界 | `page=0`、`size=101` 均 400 |
| 6 | 修改用户资料 | 200，昵称与邮箱更新 |
| 7 | 逻辑删除用户 | 204；旧令牌 401；无法再登录 |
| 8 | 角色改名规则 | 自定义角色可改名；内置角色 409 `PROTECTED_ROLE` |
| 9 | 管理员角色权限保护 | 移除 `user:manage`/`role:manage` → 409 |

### 3.5 自助改密 `PasswordChangeApiTest`（7）

| # | 场景 | 关键断言 |
| --- | --- | --- |
| 1 | 改密成功 | 204；旧 access token 立即 401；旧密码登录 401；新密码登录 200 |
| 2 | 旧密码错误 | 400「当前密码不正确」；原密码仍可登录 |
| 3 | 新密码过弱 | 400「密码必须同时包含字母和数字」；密码未变 |
| 4 | 新旧密码相同 | 400「新密码不能与当前密码相同」 |
| 5 | 字段缺失/过短 | 400 且返回字段级错误 |
| 6 | 未认证调用 | 401 |
| 7 | 审计留痕 | `auth.password.change` 可按动作检索到，actorId 为本人 |

### 3.6 PostgreSQL 集成 `PostgresApiTest`（6）

| # | 场景 | 关键断言 |
| --- | --- | --- |
| 1 | 运行环境确认 | `select version()` 含 PostgreSQL，当前库为 postgres |
| 2 | Flyway 建表 | 10 张业务表 + `flyway_schema_history`；4 角色 / 49 权限 / 5 演示账号 / 2 实验室 |
| 3 | 认证闭环 | 登录 → `/me` → 退出 204 → 旧令牌 401；会话 `revoked_at` 落库 |
| 4 | 唯一约束 | 重复用户名 409，数据库中只有一条记录 |
| 5 | 禁用账号 | 令牌立即 401，`auth_version` 递增 |
| 6 | 动态权限与审计 | 新角色 0 权限 → 授权 → 同一令牌可见；审计表有对应记录 |

### 3.7 领域与服务单元测试（42）

| 测试类 | 数量 | 覆盖点 |
| --- | --- | --- |
| `AuthServiceTest` | 8 | 注册哈希与默认角色、重名不落库、登录建会话与双令牌、三类失败统一、退出吊销、刷新重放吊销会话族、改密吊销会话、旧密码错误不落库 |
| `AccessTokenAuthenticatorTest` | 5 | 有效令牌、会话吊销、未知会话、授权版本不一致、账号禁用 |
| `LoginThrottleTest` | 5 | 阈值拦截、用户名/IP 隔离、成功清零、窗口过期、过期清理 |
| `JwtServiceTest` | 6 | 签发/解析、过期、篡改、异源签名、短密钥拒绝、随机密钥生成 |
| `PasswordPolicyTest` | 6 | 合法、过短、纯字母/纯数字、空白、下限、超长 |
| `UserDomainTest` | 7 | 注册初始状态、禁用/改密递增版本、多角色并集、只读集合、受保护角色、权限替换 |
| `SortSpecTest` | 5 | 默认升序、字段+方向解析、非法字段、非法方向、多余参数 |

## 4. 手工冒烟记录（真实 HTTP，dev profile）

```text
GET  /actuator/health                        -> 200 {"status":"UP"}
POST /api/v1/auth/login (admin_demo)         -> 200, 权限 7 项
GET  /api/v1/admin/users?page=1&size=3       -> 200, total=5
GET  /api/v1/admin/users?sort=username,desc  -> 200, 首条 researcher_b、末条 admin_demo
GET  /api/v1/admin/users?sort=passwordHash   -> 400 VALIDATION_FAILED
GET  /api/v1/admin/audit-events?size=5       -> 200, total=3, 首条 auth.login/SUCCESS
POST /api/v1/auth/login（同一账号连续失败 6 次）-> 第 6 次 429 RATE_LIMITED
POST /api/v1/auth/refresh（Referer: evil）    -> 403 FORBIDDEN
GET  /api/v1/admin/users（researcher）        -> 403；审计新增 security.access.denied，actor=4
GET  /api/v1/admin/audit-events（pi_demo）    -> 200, 仅本人 1 条记录（作用域收敛）
GET  /h2-console（dev profile）               -> 200（仅本地开发放行）
GET  /api/v1/me（退出后旧令牌）                -> 401 {"code":"SESSION_REVOKED"}
POST /api/v1/auth/login（错误口令）            -> 401 {"code":"INVALID_CREDENTIALS"}
```

## 5. CI 证据

- 工作流：`.github/workflows/ci.yml`，在 `push` / `pull_request` 时执行。
- 后端：`./mvnw -B verify`（102 用例，含临时 PostgreSQL 实例 + JaCoCo check），上传 Surefire 与 JaCoCo 报告为 artifact。
- 前端：`npm ci && npm run build`。
- 最近一次 `main` 运行：**成功**（运行编号见下）。

| 运行编号 | 触发提交 | 结果 |
| --- | --- | --- |
| 45565099683 | docs: 补充演示时限流提示… | 前后端均成功 |
| 45566994267 | test(postgres) + feat(auth/observability/frontend) | 后端 102 用例（含 PostgreSQL 集成 5.3s）、前端构建均成功 |

## 6. 尚未覆盖（如实说明）

- PostgreSQL 集成测试覆盖单实例下的迁移、约束与主链路；**多线程并发审批/预约**等并发用例留给第二阶段（需要业务状态机就绪）；
- 未做浏览器 E2E（接口契约由 MockMvc 覆盖，E2E 列为第三阶段附加项）；
- 未做压力测试、Redis 失效回退、WebSocket 认证等第三阶段内容；
- 登录限流为**进程内固定窗口**实现（重启清零、多实例不共享），第三阶段替换为 Redis 计数；
- 访问日志只记录方法、路径、状态、耗时与用户名，不记录请求体、查询串和令牌。

## 7. 覆盖率口径

JaCoCo 统计 `LINE_COVERED / (LINE_COVERED + LINE_MISSED)`，排除启动类、DTO/VO 与 JPA 实体，配置见 `backend/pom.xml`。
门禁规则：`BUNDLE` 行覆盖率 ≥ 0.85；包 `com/labflow/identity/application` 行覆盖率 ≥ 0.80。
