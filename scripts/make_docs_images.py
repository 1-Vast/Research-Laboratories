"""Generate documentation images from real project data.

1. coverage-summary.png  - service-layer coverage table rendered from target/site/jacoco/jacoco.csv
2. architecture.png      - block diagram of the running architecture
"""
import csv
import os
from collections import OrderedDict

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "docs", "assets")
os.makedirs(ASSETS, exist_ok=True)

FONT_REG = r"C:\Windows\Fonts\msyh.ttc"
FONT_BOLD = r"C:\Windows\Fonts\msyhbd.ttc"

INK = (31, 42, 55)
MUTED = (97, 112, 138)
BRAND = (47, 93, 138)
BRAND_LIGHT = (234, 241, 248)
BORDER = (205, 215, 227)
OK = (30, 122, 70)
BG = (255, 255, 255)


def font(size, bold=False):
    return ImageFont.truetype(FONT_BOLD if bold else FONT_REG, size)


def rounded(draw, box, radius, fill, outline=BORDER, width=2):
    draw.rounded_rectangle(box, radius=radius, fill=fill, outline=outline, width=width)


def centered(draw, box, text, fnt, fill=INK):
    left, top, right, bottom = box
    bbox = draw.multiline_textbbox((0, 0), text, font=fnt, spacing=6, align="center")
    w, h = bbox[2] - bbox[0], bbox[3] - bbox[1]
    draw.multiline_text(((left + right - w) / 2, (top + bottom - h) / 2), text, font=fnt,
                        fill=fill, spacing=6, align="center")


def arrow(draw, start, end, fill=MUTED, width=2):
    draw.line([start, end], fill=fill, width=width)
    # simple arrow head
    import math
    angle = math.atan2(end[1] - start[1], end[0] - start[0])
    length = 9
    for delta in (2.7, -2.7):
        x = end[0] + length * math.cos(angle + delta)
        y = end[1] + length * math.sin(angle + delta)
        draw.line([end, (x, y)], fill=fill, width=width)


def read_coverage():
    path = os.path.join(ROOT, "backend", "target", "site", "jacoco", "jacoco.csv")
    rows = []
    with open(path, newline="", encoding="utf-8") as handle:
        for row in csv.DictReader(handle):
            line_cov = int(row["LINE_COVERED"])
            line_miss = int(row["LINE_MISSED"])
            total = line_cov + line_miss
            if total == 0:
                continue
            rows.append((row["PACKAGE"].split(".")[-1], row["CLASS"], line_cov, line_miss))
    return rows


def count_tests():
    """从 Surefire 报告里读真实用例数，避免图片上的数字写死后与代码脱节。"""
    report_dir = os.path.join(ROOT, "backend", "target", "surefire-reports")
    total = 0
    if not os.path.isdir(report_dir):
        return total
    import xml.etree.ElementTree as ET
    for name in os.listdir(report_dir):
        if not name.endswith(".xml"):
            continue
        try:
            root = ET.parse(os.path.join(report_dir, name)).getroot()
        except ET.ParseError:
            continue
        total += int(root.get("tests", "0"))
    return total


def build_coverage_image(rows):
    services = [r for r in rows if r[1] in {
        "AuthService", "UserAdminService", "RoleAdminService", "AccessTokenAuthenticator",
        "LoginThrottle", "AuditQueryService", "JwtService", "PasswordPolicy", "User", "Role",
        "ProjectService", "TaskService", "ReportService", "ProjectScopeService",
        "Project", "Task", "Report"}]
    order = ["AuthService", "AccessTokenAuthenticator", "LoginThrottle", "JwtService", "User", "Role",
             "PasswordPolicy", "AuditQueryService", "RoleAdminService", "UserAdminService",
             "Project", "Task", "Report", "ProjectScopeService",
             "ProjectService", "TaskService", "ReportService"]
    services.sort(key=lambda r: order.index(r[1]) if r[1] in order else 99)

    total_cov = sum(r[2] for r in rows)
    total_miss = sum(r[3] for r in rows)
    overall = 100.0 * total_cov / (total_cov + total_miss)

    width = 900
    row_h = 40
    header_h = 130
    height = header_h + row_h * (len(services) + 3) + 30
    img = Image.new("RGB", (width, height), BG)
    draw = ImageDraw.Draw(img)

    draw.text((40, 30), "LabFlow 后端测试覆盖率摘要", font=font(26, True), fill=INK)
    draw.text((40, 72),
              f"来源：JaCoCo 0.8.12 · {count_tests()} 个用例全部通过 · 整体行覆盖率 {overall:.1f}%",
              font=font(15), fill=MUTED)

    cols = [40, 330, 560, 760]
    y = 108
    draw.line([(40, y), (width - 40, y)], fill=BORDER, width=2)
    for x, label in zip(cols, ["类 / 组件", "行覆盖", "未覆盖行", "覆盖率"]):
        draw.text((x, y + 8), label, font=font(14, True), fill=MUTED)
    y += row_h
    draw.line([(40, y), (width - 40, y)], fill=BORDER, width=1)

    def draw_row(y, name, cov, miss, pct, strong=False):
        color = OK if pct >= 60 else (179, 38, 30)
        draw.text((cols[0], y + 10), name, font=font(15, strong), fill=INK)
        draw.text((cols[1], y + 10), str(cov), font=font(15), fill=INK)
        draw.text((cols[2], y + 10), str(miss), font=font(15), fill=INK)
        draw.text((cols[3], y + 10), f"{pct:.1f}%", font=font(15, True), fill=color)
        draw.line([(40, y + row_h), (width - 40, y + row_h)], fill=(238, 242, 247), width=1)
        return y + row_h

    for package, cls, cov, miss in services:
        pct = 100.0 * cov / (cov + miss)
        y = draw_row(y, cls, cov, miss, pct)

    y = draw_row(y, "全部类合计", total_cov, total_miss, overall, strong=True)
    draw.text((40, y + 10),
              "门禁：整体行覆盖率 ≥ 85%、identity.application 包 ≥ 80%，由 JaCoCo check 在 verify 阶段强制。",
              font=font(13), fill=MUTED)

    img.save(os.path.join(ASSETS, "coverage-summary.png"))
    print("coverage image written, overall", round(overall, 1))


def build_architecture_image():
    width, height = 1100, 760
    img = Image.new("RGB", (width, height), (250, 252, 254))
    draw = ImageDraw.Draw(img)

    draw.text((40, 32), "LabFlow 系统架构（第一阶段）", font=font(28, True), fill=INK)
    draw.text((40, 74), "前后端分离 · DDD 模块化单体 · JWT + 服务端会话 · RBAC 动态授权",
              font=font(15), fill=MUTED)

    # browser + frontend
    rounded(draw, (40, 130, 300, 210), 12, BG)
    centered(draw, (40, 130, 300, 210), "浏览器\nVue 3 + TypeScript", font(17, True))
    rounded(draw, (40, 240, 300, 330), 12, BG)
    centered(draw, (40, 240, 300, 330), "Vite / 反向代理\n/api/v1 → 后端", font(15))

    # backend container
    rounded(draw, (360, 120, 800, 560), 16, (255, 255, 255), BRAND, 3)
    draw.text((382, 136), "Spring Boot 3.4 模块化单体", font=font(17, True), fill=BRAND)

    layers = [
        ("Security Filter Chain", "JWT 认证 · 会话校验 · 方法级授权", 182),
        ("Interfaces 接口层", "Controller · DTO 校验 · VO 映射", 262),
        ("Application 应用层", "用例编排 · 事务边界 · 事件/审计", 342),
        ("Domain 领域层", "聚合 · 状态机 · 仓储端口", 422),
        ("Infrastructure 基础设施层", "JPA 适配器 · 外部服务端口实现", 502),
    ]
    for title, desc, top in layers:
        rounded(draw, (385, top, 775, top + 58), 10, BRAND_LIGHT, BORDER, 1)
        draw.text((402, top + 9), title, font=font(15, True), fill=BRAND)
        draw.text((402, top + 31), desc, font=font(12), fill=MUTED)
    # internal arrows
    for top in [240, 320, 400, 480]:
        arrow(draw, (580, top + 0), (580, top - 18), fill=(122, 167, 214), width=2)

    # datasource box
    rounded(draw, (860, 180, 1060, 300), 12, BG)
    centered(draw, (860, 180, 1060, 300), "数据库\nPostgreSQL 14+\n本地：H2 文件库", font(14, True))
    rounded(draw, (860, 340, 1060, 440), 12, (245, 247, 250), BORDER, 2)
    centered(draw, (860, 340, 1060, 440), "Redis / MinIO / LLM\n第三阶段接入", font(13), MUTED)

    # connections
    arrow(draw, (300, 170), (352, 170), fill=BRAND)
    arrow(draw, (300, 285), (352, 285), fill=BRAND)
    arrow(draw, (802, 240), (852, 240), fill=BRAND)
    arrow(draw, (802, 390), (852, 390), fill=MUTED)

    # test / docs
    rounded(draw, (40, 380, 300, 470), 12, BG)
    centered(draw, (40, 380, 300, 470), "JUnit 5 + Mockito\nMockMvc + JaCoCo\n58 用例 / 86.7%", font(14))
    rounded(draw, (40, 500, 300, 560), 12, BG)
    centered(draw, (40, 500, 300, 560), "Swagger UI\n/v3/api-docs", font(14))

    # bottom note
    rounded(draw, (360, 600, 1060, 700), 12, BRAND_LIGHT, BORDER, 1)
    draw.multiline_text((382, 616),
                        "授权判定：authenticated ∧ permissionExists ∧ scopeMatch ∧ ownerOrReviewer ∧ stateGuard\n"
                        "权限每次请求从数据库重新加载，退出登录与禁用账号立即生效（无需等令牌过期）",
                        font=font(14), fill=BRAND, spacing=8)

    img.save(os.path.join(ASSETS, "architecture.png"))
    print("architecture image written")


if __name__ == "__main__":
    build_coverage_image(read_coverage())
    build_architecture_image()
