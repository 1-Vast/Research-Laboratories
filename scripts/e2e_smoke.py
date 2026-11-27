"""端到端冒烟：真实 HTTP + 真实数据库，验证主流程与关键负例。

只用标准库，避免额外依赖。任一项失败不中断，最后汇总并以退出码反映结果。
用法：先启动 dev profile 后端，再执行 python scripts/e2e_smoke.py
"""
import json
import sys
import urllib.error
import urllib.request

BASE = "http://127.0.0.1:8080/api/v1"
PASSWORD = "Labflow@2026"
passed = []
failed = []

# 本机访问必须绕过 HTTP 代理：环境里若设了 http_proxy/HTTP_PROXY，
# urllib 会把 127.0.0.1 也发给代理，得到 502 而不是应用响应。
_OPENER = urllib.request.build_opener(urllib.request.ProxyHandler({}))


def call(method, path, token=None, body=None):
    url = BASE + path
    data = None if body is None else json.dumps(body).encode("utf-8")
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with _OPENER.open(req) as resp:
            raw = resp.read().decode("utf-8")
            return resp.status, (json.loads(raw) if raw else None)
    except urllib.error.HTTPError as err:
        raw = err.read().decode("utf-8")
        try:
            return err.code, (json.loads(raw) if raw else None)
        except json.JSONDecodeError:
            return err.code, {"raw": raw}


def check(name, actual, expected):
    if actual == expected:
        passed.append(name)
        print(f"  PASS  {name} -> {actual}")
    else:
        failed.append(name)
        print(f"  FAIL  {name} -> 期望 {expected}，实际 {actual}")


def login(username):
    status, body = call("POST", "/auth/login", body={"username": username, "password": PASSWORD})
    if status != 200:
        print(f"登录失败 {username}: {status} {body}")
        sys.exit(1)
    return body["data"]["accessToken"]


print("== 1. 登录五个账号 ==")
leader = login("leader_demo")
pi = login("pi_demo")
researcher = login("researcher_a")
outsider = login("researcher_b")
admin = login("admin_demo")
check("leader 登录", bool(leader), True)
check("pi 登录", bool(pi), True)
check("researcher 登录", bool(researcher), True)
check("outsider 登录", bool(outsider), True)
check("admin 登录", bool(admin), True)

status, me = call("GET", "/me", researcher)
researcher_id = me["data"]["id"]
status, me = call("GET", "/me", leader)
leader_id = me["data"]["id"]

print("== 2. 实验室列表按成员关系收敛 ==")
status, body = call("GET", "/labs", leader)
check("leader 看到 LAB-A", body["data"][0]["code"], "LAB-A")
lab_id = body["data"][0]["id"]
status, body = call("GET", "/labs", outsider)
check("outsider 只看到 LAB-B", [x["code"] for x in body["data"]], ["LAB-B"])

print("== 3. 建项目（草稿）==")
status, body = call("POST", "/projects", leader, {
    "labId": lab_id,
    "name": "端到端冒烟项目",
    "objective": "验证全链路",
    "plannedStart": "2026-11-01",
    "plannedEnd": "2027-03-31",
})
check("创建项目 201", status, 201)
project_id = body["data"]["id"]
check("初始状态 DRAFT", body["data"]["status"], "DRAFT")
check("初始版本 0", body["data"]["version"], 0)

print("== 4. 负例：草稿态不能建任务 ==")
status, body = call("POST", f"/projects/{project_id}/tasks", leader,
                    {"assigneeId": researcher_id, "title": "草稿任务"})
check("草稿建任务 409", status, 409)
check("错误码 INVALID_STATE_TRANSITION", body["code"], "INVALID_STATE_TRANSITION")

print("== 5. 负例：过期版本提交 ==")
status, body = call("POST", f"/projects/{project_id}/submit", leader, {"version": 9})
check("过期版本 409", status, 409)
check("VERSION_CONFLICT", body["code"], "VERSION_CONFLICT")

print("== 6. 提交立项 ==")
status, body = call("POST", f"/projects/{project_id}/submit", leader, {"version": 0})
check("提交 200", status, 200)
check("状态 PENDING", body["data"]["status"], "PENDING")
check("版本 1", body["data"]["version"], 1)

print("== 7. 负例：负责人无 project:approve，不能自批 ==")
status, _ = call("POST", f"/projects/{project_id}/approve", leader,
                 {"decision": "APPROVE", "version": 1})
check("负责人审批 403", status, 403)

print("== 8. PI 审批通过 ==")
status, body = call("POST", f"/projects/{project_id}/approve", pi,
                    {"decision": "APPROVE", "reason": "目标清晰，同意立项", "version": 1})
check("审批 200", status, 200)
check("状态 ACTIVE", body["data"]["status"], "ACTIVE")
check("版本 2", body["data"]["version"], 2)

print("== 9. 加成员 ==")
status, body = call("POST", f"/projects/{project_id}/members", leader,
                    {"userId": researcher_id, "memberType": "MEMBER"})
check("加成员 201", status, 201)
status, body = call("GET", f"/projects/{project_id}/members", leader)
check("成员数 2", len(body["data"]), 2)
check("成员含负责人", leader_id in [m["userId"] for m in body["data"]], True)

print("== 10. 分派任务 ==")
status, body = call("POST", f"/projects/{project_id}/tasks", leader,
                    {"assigneeId": researcher_id, "title": "标定实验", "priority": "HIGH"})
check("建任务 201", status, 201)
task_id = body["data"]["id"]
check("任务 TODO", body["data"]["status"], "TODO")
check("任务版本 0", body["data"]["version"], 0)

print("== 11. 负例：负责人不能代替受派人开始任务 ==")
status, _ = call("POST", f"/tasks/{task_id}/start", leader, {"version": 0})
check("非受派人开始 403", status, 403)

print("== 12. 受派人推进任务 ==")
status, body = call("POST", f"/tasks/{task_id}/start", researcher, {"version": 0})
check("开始 200", status, 200)
check("IN_PROGRESS", body["data"]["status"], "IN_PROGRESS")
status, body = call("POST", f"/tasks/{task_id}/submit", researcher,
                    {"content": "完成标定，误差 0.3%", "version": 1})
check("提交交付 200", status, 200)
check("SUBMITTED", body["data"]["status"], "SUBMITTED")
status, body = call("GET", f"/tasks/{task_id}/submissions", leader)
check("交付快照已记录 1 条", len(body["data"]), 1)
check("快照号 1", body["data"][0]["submissionNo"], 1)

print("== 13. 退回后重新开始并重提，生成新快照 ==")
status, body = call("POST", f"/tasks/{task_id}/review", leader,
                    {"decision": "REJECT", "note": "请补误差曲线", "version": 2})
check("退回 200", status, 200)
check("状态 RETURNED", body["data"]["status"], "RETURNED")
status, body = call("POST", f"/tasks/{task_id}/submit", researcher,
                    {"content": "退回态直接提交", "version": 3})
check("退回态不能直接提交 409", status, 409)
status, body = call("POST", f"/tasks/{task_id}/start", researcher, {"version": 3})
check("重新开始 200", status, 200)
check("回到 IN_PROGRESS", body["data"]["status"], "IN_PROGRESS")
status, body = call("POST", f"/tasks/{task_id}/submit", researcher,
                    {"content": "补充误差曲线后重新提交", "version": 4})
check("重提 200", status, 200)
status, body = call("GET", f"/tasks/{task_id}/submissions", leader)
check("历史快照保留 2 条", len(body["data"]), 2)
check("旧快照内容未被覆盖", body["data"][0]["content"], "完成标定，误差 0.3%")
check("新快照号 2", body["data"][1]["submissionNo"], 2)

print("== 14. 负责人验收 ==")
status, body = call("POST", f"/tasks/{task_id}/review", leader,
                    {"decision": "APPROVE", "version": 5})
check("验收 200", status, 200)
check("DONE", body["data"]["status"], "DONE")

print("== 15. 项目进度 ==")
status, body = call("GET", f"/projects/{project_id}", leader)
check("进度 100%", body["data"]["progress"]["percent"], 100)
check("任务数 1", body["data"]["progress"]["total"], 1)
check("详情内项目状态 ACTIVE", body["data"]["project"]["status"], "ACTIVE")

print("== 16. 周报 ==")
status, body = call("POST", f"/projects/{project_id}/reports", researcher,
                    {"weekStart": "2026-11-25", "work": "完成标定", "nextPlan": "误差分析",
                     "taskIds": [task_id]})
check("建周报 201", status, 201)
report_id = body["data"]["id"]
check("周起始归一到周一", body["data"]["weekStart"], "2026-11-23")
status, body = call("GET", f"/projects/{project_id}/reports", researcher)
check("列表回显关联任务", body["data"]["items"][0]["taskIds"], [task_id])
status, body = call("POST", f"/projects/{project_id}/reports", researcher,
                    {"weekStart": "2026-11-27", "work": "重复周报"})
check("同周重复 409", status, 409)
check("DUPLICATE_REPORT_WEEK", body["code"], "DUPLICATE_REPORT_WEEK")
status, body = call("POST", f"/reports/{report_id}/submit", researcher, {"version": 0})
check("提交周报 200", status, 200)
check("提交后 SUBMITTED", body["data"]["status"], "SUBMITTED")
status, body = call("PUT", f"/reports/{report_id}", researcher,
                    {"work": "试图篡改", "version": 1})
check("提交后正文只读 409", status, 409)
status, body = call("POST", f"/reports/{report_id}/review", leader,
                    {"decision": "APPROVE", "note": "记录清晰", "version": 1})
check("审核周报 200", status, 200)
check("APPROVED", body["data"]["status"], "APPROVED")

print("== 17. 结项归档 ==")
status, body = call("POST", f"/projects/{project_id}/close-request", leader,
                    {"closeNote": "全部交付", "version": 2})
check("申请结项 200", status, 200)
check("CLOSING", body["data"]["status"], "CLOSING")
status, body = call("POST", f"/projects/{project_id}/close-approve", pi,
                    {"decision": "APPROVE", "version": 3})
check("归档 200", status, 200)
check("ARCHIVED", body["data"]["status"], "ARCHIVED")

print("== 18. 越权负例 ==")
status, body = call("GET", f"/projects/{project_id}", outsider)
check("非成员读详情 403", status, 403)
check("PROJECT_MEMBER_REQUIRED", body["code"], "PROJECT_MEMBER_REQUIRED")
status, _ = call("POST", "/projects", outsider, {"labId": lab_id, "name": "越权项目"})
check("无 project:create 建项目 403", status, 403)

print("== 19. 审批轨迹与审计 ==")
status, body = call("GET", f"/projects/{project_id}/reviews", pi)
check("审批轨迹 2 条（立项 + 结项）", len(body["data"]), 2)
check("轨迹类型 INIT/CLOSE", [r["reviewType"] for r in body["data"]], ["INIT", "CLOSE"])
status, body = call("GET", "/admin/audit-events?page=1&size=100", admin)
check("审计查询 200", status, 200)
actions = [e["action"] for e in body["data"]["items"]]
check("审计含 project.approve", "project.approve" in actions, True)
check("审计含 task.review", "task.review" in actions, True)
check("审计含 report.review", "report.review" in actions, True)
check("审计不含密码字段", any("password" in json.dumps(e).lower() for e in body["data"]["items"]), False)

print()
print(f"通过 {len(passed)} 项，失败 {len(failed)} 项")
if failed:
    print("失败项：" + ", ".join(failed))
    sys.exit(1)
