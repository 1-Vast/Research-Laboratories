"""Generate the preliminary design deliverable (DOCX) for LabFlow phase 1."""
import os

from docx import Document
from docx.enum.section import WD_SECTION
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.enum.text import WD_ALIGN_PARAGRAPH, WD_BREAK
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Cm, Pt, RGBColor

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "docs", "LabFlow-初步设计说明书.docx")
ARCH = os.path.join(ROOT, "docs", "assets", "architecture.png")
COVERAGE = os.path.join(ROOT, "docs", "assets", "coverage-summary.png")

ACCENT = RGBColor(0x2F, 0x5D, 0x8A)
MUTED = RGBColor(0x61, 0x70, 0x8A)

doc = Document()

# ---------- page & base styles ----------
section = doc.sections[0]
section.page_width = Cm(21.0)
section.page_height = Cm(29.7)
section.top_margin = Cm(2.4)
section.bottom_margin = Cm(2.2)
section.left_margin = Cm(2.6)
section.right_margin = Cm(2.6)


def set_font(run, name="微软雅黑", size=10.5, bold=False, color=None):
    run.font.name = name
    run.font.size = Pt(size)
    run.font.bold = bold
    if color is not None:
        run.font.color.rgb = color
    run._element.rPr.rFonts.set(qn("w:eastAsia"), name)


normal = doc.styles["Normal"]
normal.font.name = "微软雅黑"
normal.font.size = Pt(10.5)
normal.element.rPr.rFonts.set(qn("w:eastAsia"), "微软雅黑")
normal.paragraph_format.line_spacing = 1.4
normal.paragraph_format.space_after = Pt(4)

for level, size in ((1, 16), (2, 13), (3, 11.5)):
    style = doc.styles[f"Heading {level}"]
    style.font.name = "微软雅黑"
    style.font.size = Pt(size)
    style.font.bold = True
    style.font.color.rgb = ACCENT
    style.element.rPr.rFonts.set(qn("w:eastAsia"), "微软雅黑")
    style.paragraph_format.space_before = Pt(12 if level == 1 else 8)
    style.paragraph_format.space_after = Pt(6)
    style.paragraph_format.keep_with_next = True


def para(text, size=10.5, bold=False, align=None, color=None, space_after=4, indent=False):
    p = doc.add_paragraph()
    if align is not None:
        p.alignment = align
    p.paragraph_format.space_after = Pt(space_after)
    if indent:
        p.paragraph_format.first_line_indent = Pt(21)
    run = p.add_run(text)
    set_font(run, size=size, bold=bold, color=color)
    return p


def bullet(text):
    p = doc.add_paragraph(style="List Bullet")
    p.paragraph_format.space_after = Pt(2)
    run = p.add_run(text)
    set_font(run)
    return p


def numbered(text):
    p = doc.add_paragraph(style="List Number")
    p.paragraph_format.space_after = Pt(2)
    run = p.add_run(text)
    set_font(run)
    return p


def table(headers, rows, widths=None, font_size=9, header_fill="EAF1F8"):
    t = doc.add_table(rows=1, cols=len(headers))
    t.style = "Table Grid"
    t.alignment = WD_TABLE_ALIGNMENT.CENTER
    t.autofit = False
    hdr = t.rows[0].cells
    for i, h in enumerate(headers):
        hdr[i].text = ""
        p = hdr[i].paragraphs[0]
        p.alignment = WD_ALIGN_PARAGRAPH.CENTER
        run = p.add_run(h)
        set_font(run, size=font_size, bold=True, color=ACCENT)
        shade = OxmlElement("w:shd")
        shade.set(qn("w:val"), "clear")
        shade.set(qn("w:color"), "auto")
        shade.set(qn("w:fill"), header_fill)
        hdr[i]._tc.get_or_add_tcPr().append(shade)
    for row in rows:
        cells = t.add_row().cells
        for i, value in enumerate(row):
            cells[i].text = ""
            p = cells[i].paragraphs[0]
            p.paragraph_format.space_after = Pt(1)
            run = p.add_run(str(value))
            set_font(run, size=font_size)
    if widths:
        tbl = t._tbl
        layout = OxmlElement("w:tblLayout")
        layout.set(qn("w:type"), "fixed")
        tbl.tblPr.append(layout)
        for i, grid_col in enumerate(tbl.tblGrid.findall(qn("w:gridCol"))):
            grid_col.set(qn("w:w"), str(int(widths[i] * 567)))
        for row in t.rows:
            for i, width in enumerate(widths):
                row.cells[i].width = Cm(width)
    for row in t.rows:
        tr_pr = row._tr.get_or_add_trPr()
        tr_pr.append(OxmlElement("w:cantSplit"))
    doc.add_paragraph().paragraph_format.space_after = Pt(2)
    return t


def caption(text):
    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    run = p.add_run(text)
    set_font(run, size=9, color=MUTED)


def code_block(lines):
    for line in lines:
        p = doc.add_paragraph()
        p.paragraph_format.space_after = Pt(0)
        p.paragraph_format.left_indent = Pt(12)
        run = p.add_run(line)
        set_font(run, name="Consolas", size=9)
    doc.add_paragraph().paragraph_format.space_after = Pt(2)


def add_footer_page_number():
    footer = section.footer
    p = footer.paragraphs[0]
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    run = p.add_run()
    begin = OxmlElement("w:fldChar")
    begin.set(qn("w:fldCharType"), "begin")
    instr = OxmlElement("w:instrText")
    instr.set(qn("xml:space"), "preserve")
    instr.text = "PAGE"
    end = OxmlElement("w:fldChar")
    end.set(qn("w:fldCharType"), "end")
    run._r.append(begin)
    run._r.append(instr)
    run._r.append(end)
    set_font(run, size=9, color=MUTED)


def add_header_title():
    header = section.header
    p = header.paragraphs[0]
    p.alignment = WD_ALIGN_PARAGRAPH.RIGHT
    run = p.add_run("LabFlow · 初步设计说明书")
    set_font(run, size=9, color=MUTED)


add_footer_page_number()
add_header_title()

# ---------- cover ----------
for _ in range(3):
    doc.add_paragraph()
para("科研实验室智能协同管理平台", size=26, bold=True, align=WD_ALIGN_PARAGRAPH.CENTER, color=ACCENT,
     space_after=6)
para("LabFlow", size=16, bold=True, align=WD_ALIGN_PARAGRAPH.CENTER, color=MUTED, space_after=18)
para("初 步 设 计 说 明 书", size=20, bold=True, align=WD_ALIGN_PARAGRAPH.CENTER, space_after=60)

table(
    ["项目属性", "内容"],
    [
        ["项目名称", "科研实验室智能协同管理平台（LabFlow）"],
        ["项目类型", "Spring Boot 课程项目（Vue 3 + Spring Boot 前后端分离）"],
        ["文档版本", "V1.0（第一阶段设计基线）"],
        ["编制依据", "《期末项目任务书》；《科研实验室智能协同管理平台项目书 V1.1》"],
        ["当前阶段", "第一阶段（可运行底座）已完成并通过测试；第二、三阶段待实施"],
        ["技术方向", "Vue 3 + TypeScript；Spring Boot 3.4 + Spring Security + JWT；PostgreSQL（本地 H2）"],
        ["代码仓库", "1-Vast / Intelligent-Collaborative-Management-Platform-for-Scientific-Research-Laboratories"],
        ["项目组", "待按实际情况填写（姓名、学号、分工比例）"],
    ],
    widths=[3.2, 12.2],
    font_size=9.5,
)

doc.add_page_break()

# ---------- TOC ----------
doc.add_heading("目录", level=1)
toc_items = [
    "1  项目概述",
    "2  需求分析",
    "3  总体架构设计",
    "4  模块与代码结构",
    "5  认证与会话设计",
    "6  RBAC 权限设计",
    "7  数据库设计",
    "8  接口设计",
    "9  第一阶段实现与验证",
    "10 实施计划、风险与后续工作",
    "11 附录：演示账号与运行步骤",
]
for item in toc_items:
    p = doc.add_paragraph()
    p.paragraph_format.space_after = Pt(3)
    run = p.add_run(item)
    set_font(run, size=11)

doc.add_page_break()

# ---------- 1 project overview ----------
doc.add_heading("1  项目概述", level=1)
doc.add_heading("1.1  建设背景", level=2)
para("高校科研实验室的日常协作长期依赖聊天记录、个人文档与临时表格：项目进度由负责人手工汇总，任务交付缺少统一标准与验收轨迹，"
     "GPU/工作站等资源靠口头预约导致时段冲突，实验结论与论文笔记难以检索复用，新成员交接成本高。", indent=True)
para("说明：本文为设计基线，其中「已实现」状态以仓库实际代码与测试结果为准；标注为「待实施」的内容属于后续阶段计划，不代表已经完成。",
     size=9, color=MUTED)
para("LabFlow 以「项目—任务—周报」为主流程，把立项审批、任务分派与验收、进展汇报、资源预约、成果与知识管理纳入同一套权限模型，"
     "并通过后端授权保证科研数据只被有权人访问。", indent=True)

doc.add_heading("1.2  建设目标", level=2)
bullet("业务目标：让 PI 掌握实验室项目全局，项目负责人明确计划与交付，科研人员跟踪任务与资源，管理员负责身份与平台运行。")
bullet("工程目标：清晰的领域边界、后端强制授权、事务一致性与可重复验证；前端不承担任何安全决策。")
bullet("验收目标：在真实持久化数据库上完成业务闭环，展示角色差异、越权拦截、状态机、实时推送与授权 AI，教师可独立复现。")

doc.add_heading("1.3  目标用户与角色", level=2)
table(
    ["角色", "代码", "职责", "授权边界"],
    [
        ["系统管理员", "SYS_ADMIN", "用户、角色、权限、实验室登记、PI 任命", "管理控制面；默认不读取科研正文"],
        ["实验室负责人", "PI", "管理实验室成员，审批立项/结项/资源预约", "仅被任命的实验室"],
        ["科研项目负责人", "PROJECT_LEADER", "管理所负责项目的成员、任务、周报与成果", "仅负责人关系的项目"],
        ["科研人员", "RESEARCHER", "承接任务、提交周报、申请资源、共享成果", "仅受邀加入的实验室/项目"],
    ],
    widths=[2.4, 3.4, 5.8, 4.2],
)
para("设计要点：系统角色决定「具备哪类操作能力」，实验室成员关系与项目成员关系决定「在哪个范围具有能力」。"
     "两者分离，避免把「项目负责人」误当成所有项目的负责人。", indent=True)

doc.add_heading("1.4  范围与优先级", level=2)
table(
    ["优先级", "模块", "交付内容", "明确不做"],
    [
        ["P0", "身份与授权", "注册、登录、刷新、退出、用户/角色/权限管理、动态授权", "第三方联邦登录"],
        ["P0", "科研项目", "立项草稿、PI 审批、成员维护、结项归档、分页筛选", "经费报销、财务审计"],
        ["P0", "任务协作", "分派、开始、提交、退回、验收、进度计算", "复杂依赖 DAG"],
        ["P0", "进度汇报", "周报草稿、提交、审核、退回重提", "读取私人聊天/邮件"],
        ["P1", "资源协同", "逻辑资源登记、半开区间预约、冲突控制", "真实 SSH/GPU 排队"],
        ["P1", "成果知识", "成果元数据、附件、知识条目检索", "真实投稿系统"],
        ["P1", "实时与 AI", "定向通知、授权周报草稿（人工确认）", "智能体自动审批"],
        ["P2", "远期扩展", "RAG、GitHub 同步、资源队列", "不纳入本次交付"],
    ],
    widths=[1.6, 2.6, 7.4, 4.2],
)

# ---------- 2 requirements ----------
doc.add_heading("2  需求分析", level=1)
doc.add_heading("2.1  核心用例", level=2)
table(
    ["编号", "操作者 / 用例", "完成条件"],
    [
        ["AUTH-01", "访客注册", "用户名唯一；密码规则后端校验；默认科研人员；无实验室数据"],
        ["AUTH-02", "所有用户登录/退出", "登录失败提示统一；过期或已注销凭证返回 401"],
        ["RBAC-01", "管理员管理用户/角色", "CRUD、禁用启用、分配角色/权限；变更后生效"],
        ["RBAC-02", "所有业务操作者", "每个接口授权；角色合法但范围不符仍拒绝"],
        ["PROJ-01", "负责人发起立项、PI 审批", "仅审批本实验室项目；记录理由和时间"],
        ["TASK-01/02", "负责人分派任务；科研人员提交、负责人审核", "非法跳转失败；退回原因必填；验收后进度更新"],
        ["REP-01", "成员创建、提交周报", "唯一「项目+作者+周起始日」；提交后不得直接改内容"],
        ["RES-01", "成员申请资源、PI 审批", "同资源批准时段不可重叠；取消释放"],
        ["AI-01", "授权用户生成周报草稿", "AI 输入仅授权数据；输出人工审核后走正常接口"],
    ],
    widths=[2.4, 6.4, 6.6],
)
para("第一阶段实现 AUTH-01/02 与 RBAC-01/02 的接口部分；PROJ/TASK/REP 等业务用例在第二阶段实现（设计已冻结，见第 10 章）。",
     size=9.5, color=MUTED)

doc.add_heading("2.2  非功能与安全要求", level=2)
table(
    ["类别", "要求", "实现方式"],
    [
        ["密码安全", "BCrypt 加盐哈希，任何接口不返回密码", "BCryptPasswordEncoder；VO 不含密码字段"],
        ["会话安全", "退出后旧凭证立即失效；令牌有有效期", "JWT(15min) + 服务端 auth_sessions；退出吊销"],
        ["授权", "后端逐接口校验，越权返回 403/404", "@PreAuthorize + 作用域/归属/状态校验"],
        ["数据一致性", "写操作单事务；状态变更可审计", "应用服务 @Transactional + audit_events"],
        ["可观测性", "错误可定位且不泄露内部信息", "traceId + 统一异常处理（无堆栈）"],
        ["可复现性", "教师按文档 15 分钟内跑起来", "Maven Wrapper + H2 文件库 + 种子数据"],
    ],
    widths=[2.4, 6.6, 6.4],
)

# ---------- 3 architecture ----------
doc.add_heading("3  总体架构设计", level=1)
doc.add_heading("3.1  运行拓扑", level=2)
para("系统为前后端分离的模块化单体：浏览器加载 Vue 3 单页应用，通过 /api/v1 调用 Spring Boot；"
     "后端在安全过滤器链完成身份校验后进入接口层、应用层与领域层，基础设施层负责持久化与外部服务适配。", indent=True)
doc.add_picture(ARCH, width=Cm(15.6))
doc.paragraphs[-1].alignment = WD_ALIGN_PARAGRAPH.CENTER
caption("图 3-1  LabFlow 系统架构（第一阶段）")

doc.add_heading("3.2  技术选型", level=2)
table(
    ["层次", "技术", "版本", "理由"],
    [
        ["前端", "Vue 3 + TypeScript", "3.5 / 5.7", "课程要求；组合式 API 便于演示页面"],
        ["构建", "Vite + Vue Router + Axios", "6.4 / 4.5 / 1.7", "启动快；代理避免跨域与 Cookie 问题"],
        ["后端", "Spring Boot + Spring Security", "3.4.13 / 6.4", "分层清晰，方法级权限成熟"],
        ["持久化", "Spring Data JPA（Hibernate 6）", "3.4", "聚合映射直观，利于 DDD 分层"],
        ["数据库", "PostgreSQL 14+ / H2 2.x", "—", "生产用 PG；本地 H2 文件库零安装"],
        ["迁移", "Flyway", "10.x", "版本化脚本，一次执行可重复启动"],
        ["认证", "JJWT（HS256）", "0.12.6", "无状态令牌 + 服务端会话校验"],
        ["测试", "JUnit 5 / Mockito / MockMvc / JaCoCo", "—", "单元、接口与覆盖率证据"],
        ["文档", "springdoc-openapi", "2.8.13", "Swagger UI 可现场调试"],
    ],
    widths=[2.2, 5.4, 2.8, 5.0],
)

doc.add_heading("3.3  分层与依赖方向", level=2)
table(
    ["层次", "职责", "约束"],
    [
        ["interfaces", "接收请求、DTO 校验、VO 映射、声明式权限", "不写业务判断，不直接返回实体"],
        ["application", "用例编排、事务边界、审计与事件", "不依赖具体数据库实现"],
        ["domain", "聚合、状态机、领域不变量、仓储端口", "不依赖 Spring MVC / JPA"],
        ["infrastructure", "JPA 实体与适配器、外部服务实现", "只实现领域端口，不反向被依赖"],
    ],
    widths=[3.2, 7.2, 5.0],
)
para("依赖方向：interfaces → application → domain，infrastructure → domain。该约束使领域规则（如禁用账号递增授权版本、"
     "受保护角色不可删除）可以脱离框架单独测试。", indent=True)

# ---------- 4 modules ----------
doc.add_heading("4  模块与代码结构", level=1)
doc.add_heading("4.1  限界上下文划分", level=2)
table(
    ["上下文", "聚合 / 主要用例", "阶段"],
    [
        ["identity 身份与授权", "User、Role、Permission、AuthSession、RefreshToken；登录/授权", "已实现"],
        ["organization 组织", "Laboratory、Membership；成员与 PI 管理", "表已建，接口待实现"],
        ["research 科研", "Project、Task、Report；立项/验收/汇报", "第二阶段"],
        ["resource 资源", "Resource、Booking；申请与审批", "第三阶段"],
        ["knowledge 知识", "Artifact、KnowledgeEntry、Attachment", "第三阶段"],
        ["collaboration 协作", "Notification、Audit、AIJob", "部分（审计已实现）"],
    ],
    widths=[4.2, 8.4, 3.0],
)

doc.add_heading("4.2  包结构", level=2)
code_block([
    "com.labflow",
    "├── shared/                     统一响应、异常、traceId、安全、审计、配置",
    "│   ├── api/  error/  web/  security/  audit/  config/",
    "└── identity/                   身份与授权上下文",
    "    ├── domain/                 User、Role、Permission、AuthSession、RefreshToken、仓储端口",
    "    ├── application/            AuthService、UserAdminService、RoleAdminService、AccessTokenAuthenticator",
    "    ├── infrastructure/persistence/  JPA 实体、Spring Data 仓储、端口适配器、映射器",
    "    └── interfaces/rest/        Controller、DTO、VO、Web 映射器",
])

# ---------- 5 auth ----------
doc.add_heading("5  认证与会话设计", level=1)
doc.add_heading("5.1  令牌与会话", level=2)
table(
    ["凭证", "形态", "有效期", "存储位置", "失效方式"],
    [
        ["Access Token", "JWT（HS256）", "15 分钟", "前端内存 + sessionStorage", "过期 / 会话吊销 / 授权版本变更"],
        ["Refresh Token", "32 字节随机串（不透明）", "7 天", "HttpOnly Cookie + 数据库哈希", "一次性使用、轮换、重放即吊销"],
        ["服务端会话", "auth_sessions 记录", "与 Refresh 一致", "数据库", "退出、禁用、改密时置 revoked_at"],
    ],
    widths=[3.2, 4.4, 2.6, 4.0, 4.0],
)
para("JWT 载荷只包含 sub（用户）、sid（会话）、av（授权版本）、iat/exp/jti，不携带权限列表。"
     "权限在每次请求时从数据库加载，因此管理员调整角色权限后下一次请求立即生效，无需重启或重新登录。", indent=True)

doc.add_heading("5.2  认证流程", level=2)
numbered("客户端提交用户名与密码；后端规范化用户名后查询用户。")
numbered("即使账号不存在也执行一次等价 BCrypt 校验，降低通过响应时间探测账号的差异。")
numbered("校验失败（不存在/密码错误/已禁用）统一返回 401「用户名或密码错误」。")
numbered("校验成功创建 auth_sessions 会话，签发 access token 与一次性 refresh token（Cookie 下发）。")
numbered("后续请求由过滤器校验签名、会话有效性、账号状态与授权版本，再从数据库读取权限。")
numbered("退出登录吊销会话并清除 Cookie；旧 access token 下一次请求返回 401（SESSION_REVOKED）。")
numbered("刷新时旧 refresh token 标记已用并轮换；已用令牌被重放视为泄露，吊销整个会话族。")

doc.add_heading("5.3  关键安全约束", level=2)
table(
    ["风险", "控制措施", "对应实现"],
    [
        ["密码明文/弱哈希", "BCrypt 加盐（cost 10）", "SecurityConfig.passwordEncoder"],
        ["密码泄露到响应", "VO 不含密码字段", "UserVo / MeVo"],
        ["退出仅前端删 Token", "服务端吊销会话", "AuthService.logout"],
        ["禁用账号仍可访问", "会话吊销 + enabled + auth_version", "AccessTokenAuthenticator"],
        ["刷新令牌重放", "used_at 检测 + 会话族吊销", "AuthService.refresh"],
        ["密钥入库", "环境变量注入；未配置时随机生成", "JwtService"],
        ["异常堆栈外泄", "全局异常处理统一响应", "GlobalExceptionHandler"],
    ],
    widths=[4.4, 7.0, 4.0],
)

# ---------- 6 rbac ----------
doc.add_heading("6  RBAC 权限设计", level=1)
doc.add_heading("6.1  数据模型", level=2)
para("标准五表模型：users、roles、permissions、user_roles、role_permissions。用户与角色、角色与权限均为多对多，"
     "支持一人多角色，权限集合取并集。角色表设 protected 标记，内置角色不可删除或改码，防止把系统锁死。", indent=True)

doc.add_heading("6.2  授权判定", level=2)
para("允许 = 已认证 ∧ 拥有接口权限码 ∧ 范围匹配 ∧ 归属或审核关系 ∧ 状态守卫", bold=True)
para("接口层通过 @PreAuthorize(\"hasAuthority('xxx:yyy')\") 校验操作码；应用服务根据数据库关系校验实验室/项目范围与对象归属，"
     "不信任请求中的 userId、labId、projectId；列表接口先按作用域过滤再分页；无权限返回 403，跨范围且需隐藏存在性的返回 404。", indent=True)

doc.add_heading("6.3  角色 × 权限矩阵（第一阶段实现部分）", level=2)
table(
    ["权限码", "管理员", "PI", "负责人", "科研人员", "范围/守卫"],
    [
        ["user:manage / role:manage", "●", "", "", "", "平台"],
        ["lab:create / lab:appoint", "●", "", "", "", "平台；任命 PI"],
        ["lab:read", "", "●", "●", "●", "所属实验室"],
        ["lab:member:manage", "", "●", "", "", "本人负责实验室"],
        ["audit:read", "●", "●", "", "", "平台脱敏 / 实验室"],
        ["project:create", "", "", "●", "", "所属实验室"],
        ["project:read", "", "●", "●", "●", "PI 范围或项目成员"],
        ["project:update / delete", "", "", "●", "", "负责人；仅草稿可删"],
        ["project:approve / close-review", "", "●", "", "", "PI；非本人申请"],
        ["task:create / update / delete", "", "", "●", "", "负责人；状态约束"],
        ["task:read", "", "●", "●", "●", "项目范围"],
        ["task:start / submit", "", "", "●", "●", "实际受派人本人"],
        ["task:review", "", "", "●", "", "负责人；不得自审"],
        ["report:create / update / delete / submit", "", "", "●", "●", "本人；项目成员"],
        ["report:read", "", "●", "●", "●", "PI/负责人；本人"],
        ["report:review", "", "●", "●", "", "负责人；PI 审负责人本人周报"],
        ["resource:manage / booking:review", "", "●", "", "", "资源所属实验室"],
        ["artifact:* / knowledge:*", "", "审核", "●", "●", "作者本人；项目范围"],
        ["ai:report:generate", "", "●", "●", "●", "授权源数据范围"],
    ],
    widths=[6.2, 1.8, 1.6, 2.0, 2.2, 3.8],
    font_size=8.5,
)
para("共 49 条权限码入库；表中合并写法仅用于文档排版，数据库内为独立权限码，可单独授予或撤销。", size=9, color=MUTED)

doc.add_heading("6.4  动态生效与保护规则", level=2)
bullet("权限变更：管理员替换角色权限集合后，下一次请求即按新权限判定（有接口测试覆盖）。")
bullet("最后一个管理员：禁用或降级前统计「除本人外仍启用的管理员数」，为 0 时返回 409。")
bullet("内置角色：不可删除、不可改名；内置管理员角色必须保留 user:manage 与 role:manage。")
bullet("角色引用：仍被用户引用的角色不可删除（409 ROLE_IN_USE）。")

# ---------- 7 database ----------
doc.add_heading("7  数据库设计", level=1)
doc.add_heading("7.1  设计原则", level=2)
bullet("一套 DDL 兼容 PostgreSQL 14+ 与 H2 2.x（PostgreSQL 兼容模式），本地零安装与生产部署共用脚本。")
bullet("主键自增 BIGINT；会话与刷新令牌使用 UUID；业务表统一 created_at / updated_at（TIMESTAMPTZ，UTC 存储）。")
bullet("状态字段 VARCHAR + CHECK；外键默认 RESTRICT；用户与会话之间 CASCADE；已验收科研记录禁止级联删除。")
bullet("迁移由 Flyway 管理：V1__schema.sql 建表、V2__seed_data.sql 种子数据，一次执行、可重复启动。")

doc.add_heading("7.2  实体关系（第一阶段）", level=2)
code_block([
    "users 1:N auth_sessions 1:N refresh_tokens",
    "users M:N roles（user_roles）  roles M:N permissions（role_permissions）",
    "labs 1:N lab_members N:1 users",
    "users 1:N audit_events N:1 labs/projects（可空作用域）",
])
para("完整 ER 图（Mermaid）见 docs/design/02-数据库设计.md；第二阶段将补充 projects、project_members、project_reviews、"
     "tasks、task_submissions、reports、report_tasks 等 7 张表。", indent=True)

doc.add_heading("7.3  表清单与用途", level=2)
table(
    ["表名", "用途", "关键约束"],
    [
        ["users", "用户、密码哈希、启用状态、授权版本", "UQ(username)"],
        ["roles", "角色（4 个内置角色）", "UQ(code)；protected"],
        ["permissions", "权限目录（49 个操作码）", "UQ(code)"],
        ["user_roles", "用户-角色多对多", "PK(user_id, role_id)"],
        ["role_permissions", "角色-权限多对多", "PK(role_id, permission_id)"],
        ["auth_sessions", "服务端会话（退出即吊销）", "FK(user_id)；CHECK(expires_at>issued_at)"],
        ["refresh_tokens", "刷新令牌哈希（一次性）", "UQ(token_hash)；FK(session_id)"],
        ["labs", "实验室", "UQ(code)"],
        ["lab_members", "实验室成员与 PI 关系", "PK(lab_id,user_id)；CHECK(member_type)"],
        ["audit_events", "脱敏操作审计", "FK(actor_id)；按 actor/action 建索引"],
    ],
    widths=[3.4, 7.2, 6.0],
    font_size=9,
)
para("表总数 10，满足课程「≥ 8 张」要求；种子数据包含 4 类角色账号、49 条权限、2 个实验室。", indent=True)

# ---------- 8 api ----------
doc.add_heading("8  接口设计", level=1)
doc.add_heading("8.1  约定", level=2)
para("统一前缀 /api/v1；JSON 请求与响应；除注册、登录、刷新外均需 Bearer 令牌。成功返回 "
     "{code:\"OK\", message, data, traceId}；分页数据为 {items, total, page, size}；失败返回业务码与 traceId，字段校验错误附带字段列表。", indent=True)
table(
    ["HTTP", "业务码示例", "场景"],
    [
        ["200 / 201 / 204", "OK", "查询/更新、创建、删除或退出"],
        ["400", "VALIDATION_FAILED / BAD_REQUEST", "参数校验失败、请求体不可解析"],
        ["401", "INVALID_CREDENTIALS / TOKEN_EXPIRED / SESSION_REVOKED", "登录失败、令牌过期、会话已注销"],
        ["403", "FORBIDDEN", "已认证但无权限"],
        ["404", "NOT_FOUND / USER_NOT_FOUND / ROLE_NOT_FOUND", "资源不存在"],
        ["409", "DUPLICATE_USERNAME / LAST_ADMIN_PROTECTED / PROTECTED_ROLE / ROLE_IN_USE", "唯一约束与保护规则冲突"],
        ["500", "INTERNAL_ERROR", "兜底异常（不返回堆栈）"],
    ],
    widths=[2.8, 7.4, 6.4],
)

doc.add_heading("8.2  第一阶段接口清单", level=2)
table(
    ["方法", "路径", "权限", "说明"],
    [
        ["POST", "/api/v1/auth/register", "匿名", "注册；默认 RESEARCHER"],
        ["POST", "/api/v1/auth/login", "匿名", "登录；下发 HttpOnly 刷新 Cookie"],
        ["POST", "/api/v1/auth/refresh", "刷新 Cookie", "一次性轮换；重放吊销会话"],
        ["POST", "/api/v1/auth/logout", "已认证", "服务端吊销会话"],
        ["GET", "/api/v1/me", "已认证", "当前用户、角色、权限"],
        ["GET/POST", "/api/v1/admin/users", "user:manage", "分页/搜索、创建"],
        ["GET/PATCH/DELETE", "/api/v1/admin/users/{id}", "user:manage", "详情、编辑、逻辑删除"],
        ["PATCH", "/api/v1/admin/users/{id}/enabled", "user:manage", "启用/禁用（吊销会话）"],
        ["PUT", "/api/v1/admin/users/{id}/roles", "user:manage", "替换角色集合"],
        ["GET/POST", "/api/v1/admin/roles", "role:manage", "分页、创建"],
        ["GET/PATCH/DELETE", "/api/v1/admin/roles/{id}", "role:manage", "详情、改名、删除"],
        ["GET", "/api/v1/admin/permissions", "role:manage", "权限目录"],
        ["PUT", "/api/v1/admin/roles/{id}/permissions", "role:manage", "替换权限集合"],
    ],
    widths=[3.0, 8.0, 2.8, 4.6],
    font_size=9,
)
para("在线文档：http://localhost:8080/swagger-ui.html；OpenAPI：/v3/api-docs（实测返回 12 KB 完整契约）。", size=9.5, color=MUTED)

# ---------- 9 implementation ----------
doc.add_heading("9  第一阶段实现与验证", level=1)
doc.add_heading("9.1  实现状态", level=2)
table(
    ["模块", "内容", "状态"],
    [
        ["工程骨架", "Maven Wrapper、多环境配置、统一响应与异常、traceId", "已完成"],
        ["数据库", "Flyway 迁移（10 表）、四角色/49 权限/5 账号种子数据", "已完成"],
        ["认证", "注册、登录、刷新轮换、退出吊销、当前用户", "已完成"],
        ["RBAC", "用户/角色/权限管理、后端强制校验、动态权限、管理员保护", "已完成"],
        ["审计", "注册、登录、用户与角色变更写入审计表", "已完成"],
        ["前端", "登录、注册、工作台、用户管理、角色权限 5 页", "已完成"],
        ["业务模块", "项目、任务、周报、资源、成果知识、通知、AI", "第二阶段起实施"],
    ],
    widths=[3.0, 10.6, 3.0],
)

doc.add_heading("9.2  测试与覆盖率", level=2)
para("58 个有效用例全部通过：接口级 28 个（认证 14 + RBAC 14），领域/服务单元 30 个。整体行覆盖率 86.7%，"
     "Service/Application 层最低 67.9%，满足课程「≥ 20 个有效用例、Service 行覆盖率 ≥ 60%、≥ 3 个 Controller 测试」要求。", indent=True)
doc.add_picture(COVERAGE, width=Cm(14.5))
doc.paragraphs[-1].alignment = WD_ALIGN_PARAGRAPH.CENTER
caption("图 9-1  后端测试覆盖率摘要（由 JaCoCo 数据生成）")

doc.add_heading("9.3  真实 HTTP 冒烟结果", level=2)
table(
    ["请求", "结果"],
    [
        ["GET /actuator/health", "200 {\"status\":\"UP\"}"],
        ["POST /auth/login（admin_demo）", "200，code=OK，权限 7 项"],
        ["GET /me（管理员）", "200，roles=[SYS_ADMIN]"],
        ["GET /admin/users?page=1&size=3", "200，total=5，items=3"],
        ["GET /admin/users（无令牌）", "401"],
        ["POST /auth/logout", "204"],
        ["GET /me（退出后旧令牌）", "401 {\"code\":\"SESSION_REVOKED\"}"],
        ["POST /auth/login（错误口令）", "401 {\"code\":\"INVALID_CREDENTIALS\"}"],
        ["GET /v3/api-docs", "200，12195 bytes"],
    ],
    widths=[8.0, 10.0],
    font_size=9,
)

# ---------- 10 plan ----------
doc.add_heading("10  实施计划、风险与后续工作", level=1)
doc.add_heading("10.1  三阶段路线", level=2)
table(
    ["阶段", "主要内容", "退出标准", "状态"],
    [
        ["第一阶段", "工程骨架、数据库迁移、认证、RBAC、测试基线", "身份闭环可运行；退出 401；无权限 403", "已完成"],
        ["第二阶段", "项目/任务/周报三实体、状态机、作用域、分页、进度", "全流程可演示；越权负例拦截", "待实施"],
        ["第三阶段", "资源预约、成果知识、MinIO/SMTP、WebSocket、Redis、AI、E2E/CI/Compose", "附加项有可复现证据", "待实施"],
    ],
    widths=[2.4, 8.6, 5.4, 2.4],
)

doc.add_heading("10.2  第二阶段任务分解", level=2)
numbered("迁移 V3：projects、project_members、project_reviews、tasks、task_submissions、reports、report_tasks。")
numbered("实现三套状态机与命令接口（提交/审批/退回/验收/结项），非法跳转返回 409。")
numbered("实现作用域与归属校验：负责人只能操作自己的项目；受派人本人才能开始与提交；审核人不得自审。")
numbered("实现服务端分页与条件筛选、Bean Validation、乐观锁版本守卫。")
numbered("实现项目进度 = 100 × DONE 任务数 / 非取消任务数（分母为 0 显示 0%）。")
numbered("补齐前端业务页面并完成 PI 审批、任务交付、周报退回重提的联调与演示脚本。")

doc.add_heading("10.3  风险与对策", level=2)
table(
    ["风险", "对策"],
    [
        ["本机无 Docker / PostgreSQL 服务", "默认 H2 文件库保证可复现；生产 PG 共用 Flyway 脚本；第三阶段补 Compose"],
        ["范围膨胀", "P0 三实体优先；附加项按可验证证据逐项推进"],
        ["越权漏洞", "每个业务接口都做权限码 + 作用域 + 归属 + 状态四重校验并配负例测试"],
        ["并发状态不一致", "乐观锁 version + 事务内状态守卫 + 冲突 409"],
        ["密钥泄漏", "仅环境变量注入；仓库不含真实密钥；提供 .env.example"],
    ],
    widths=[5.6, 11.4],
)

# ---------- 11 appendix ----------
doc.add_heading("11  附录：演示账号与运行步骤", level=1)
doc.add_heading("11.1  演示账号（虚构数据）", level=2)
table(
    ["用户名", "密码", "角色", "用途"],
    [
        ["admin_demo", "Labflow@2026", "SYS_ADMIN", "用户/角色/权限管理、动态授权演示"],
        ["pi_demo", "Labflow@2026", "PI", "实验室 A 负责人（第二阶段审批演示）"],
        ["leader_demo", "Labflow@2026", "PROJECT_LEADER", "项目负责人（第二阶段任务演示）"],
        ["researcher_a", "Labflow@2026", "RESEARCHER", "实验室 A 科研人员"],
        ["researcher_b", "Labflow@2026", "RESEARCHER", "实验室 B，跨实验室负例对照"],
    ],
    widths=[3.2, 3.4, 4.0, 6.4],
)
para("密码在种子脚本中以 BCrypt 哈希存储；账号仅用于本地演示，禁止用于生产环境。", size=9, color=MUTED)

doc.add_heading("11.2  本地运行", level=2)
code_block([
    "# 环境：JDK 17+、Node.js 20+（无需安装数据库）",
    "cd backend && mvnw.cmd spring-boot:run      # macOS/Linux: ./mvnw spring-boot:run",
    "# 后端启动后：http://localhost:8080/actuator/health ，Swagger: /swagger-ui.html",
    "cd frontend && npm install && npm run dev  # 打开 http://localhost:5173",
    "cd backend && mvnw.cmd verify               # 运行全部测试并生成覆盖率报告",
])
para("首次启动自动执行 Flyway 迁移并写入种子数据；数据文件位于 backend/data/labflow.mv.db，删除该目录即可重置。",
     indent=True)

doc.add_heading("11.3  文档与代码索引", level=2)
table(
    ["内容", "位置"],
    [
        ["分阶段设计文档", "docs/design/00–05"],
        ["测试清单与结果", "docs/testing.md"],
        ["AI 使用说明与核心 prompt", "docs/prompts.md"],
        ["建表与种子脚本", "backend/src/main/resources/db/migration/"],
        ["认证与授权代码", "backend/src/main/java/com/labflow/identity/"],
        ["安全配置", "backend/src/main/java/com/labflow/shared/security/"],
        ["前端页面", "frontend/src/views/"],
    ],
    widths=[6.4, 10.6],
)

doc.save(OUT)
print("written:", OUT, os.path.getsize(OUT), "bytes")
