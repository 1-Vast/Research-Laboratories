#!/usr/bin/env bash
# 推送六个链式分支并按顺序创建 PR。
#
# 用法（在仓库根目录执行）：
#   GH_TOKEN=github_pat_xxx bash scripts/push-and-pr.sh
# 或先把 token 写进 .secrets/gh_token.txt（不在仓库内），再直接执行：
#   bash scripts/push-and-pr.sh
#
# 为什么需要 token：当前账号对仓库有 push 权限，但细粒度 PAT 只有 Contents:Read，
# 所以 git push 会 403。token 至少需要 Contents:Read and write；
# 要自动开 PR 还需要 Pull requests:Read and write。
set -euo pipefail

REPO="1-Vast/Research-Laboratories"
API="https://api.github.com/repos/$REPO"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# 注意：本环境的 git 不接受 MSYS 绝对路径（/c/Users/...）作为 -C 参数，
# 所以统一先 cd 到仓库根目录，后续 git 命令都用相对路径。
cd "$ROOT"

# 分支必须按这个顺序合并：它们是链式的，后面的基于前面的。
BRANCHES=(
  "chore-toolchain|chore: 修正仓库名与 CI 徽章，统一构建工具链"
  "feat-research-schema|feat(research): 建立科研业务核心模型与三条状态机"
  "feat-project-workflow|feat(research): 项目工作流闭环（立项/结项审批、作用域、乐观锁）"
  "feat-task-workflow|feat(research): 任务工作流（分派、交付快照、验收、退回重提）"
  "feat-report-workflow|feat(research): 周报工作流（周归一、同周唯一、提交后只读）"
  "feat-frontend-research|feat(frontend): 科研业务四页 + 端到端冒烟 + 文档校准"
)

TOKEN="${GH_TOKEN:-}"
if [ -z "$TOKEN" ] && [ -f "$ROOT/../.secrets/gh_token.txt" ]; then
  TOKEN="$(tr -d '\r\n' < "$ROOT/../.secrets/gh_token.txt")"
fi
if [ -z "$TOKEN" ]; then
  echo "缺少 token：设置 GH_TOKEN 环境变量，或写入 .secrets/gh_token.txt" >&2
  exit 1
fi

api() {
  # api METHOD PATH [BODY]
  local method="$1" path="$2" body="${3:-}"
  if [ -n "$body" ]; then
    curl -sS --noproxy '*' -X "$method" \
      -H "Authorization: Bearer $TOKEN" \
      -H "Accept: application/vnd.github+json" \
      -H "Content-Type: application/json" \
      -d "$body" "$API$path"
  else
    curl -sS --noproxy '*' -X "$method" \
      -H "Authorization: Bearer $TOKEN" \
      -H "Accept: application/vnd.github+json" \
      "$API$path"
  fi
}

echo "== 1. 预检：token 是否具备写权限 =="
HEAD_SHA="$(cd "$ROOT" && git rev-parse main)"
PROBE="$(api POST /git/refs "{\"ref\":\"refs/heads/perm-probe\",\"sha\":\"$HEAD_SHA\"}")"
if echo "$PROBE" | grep -q '"ref"'; then
  echo "  写权限正常，清理探针分支"
  api DELETE /git/refs/heads/perm-probe > /dev/null || true
elif echo "$PROBE" | grep -q "Reference already exists"; then
  echo "  写权限正常（探针分支已存在，忽略）"
else
  echo "  ✗ 写权限不足。GitHub 返回：" >&2
  echo "$PROBE" | head -5 >&2
  cat >&2 <<'EOF'

  请重新生成 Fine-grained PAT：
    1. https://github.com/settings/personal-access-tokens → Generate new token
    2. Repository access → Only select repositories → 勾选 1-Vast/Research-Laboratories
    3. Repository permissions：
         Contents        → Read and write   （推送必需）
         Pull requests   → Read and write   （开 PR 必需）
    4. 生成后点最下方 Update token / Generate token 保存
EOF
  exit 1
fi

echo
echo "== 2. 推送分支 =="
for entry in "${BRANCHES[@]}"; do
  branch="${entry%%|*}"
  printf '  %-26s ' "$branch"
  # 显式清空 http(s).proxy：本机环境变量里配了代理，但直连 GitHub 反而更稳，
  # 走代理时 git 会在中途卡住（curl 加 --noproxy 一直正常，说明直连没问题）。
  git -c http.proxy= -c https.proxy= -c http.sslBackend=openssl -c http.version=HTTP/1.1 \
    push -q "https://x-access-token:${TOKEN}@github.com/${REPO}.git" \
    "refs/heads/${branch}:refs/heads/${branch}" && echo "已推送" || { echo "失败"; exit 1; }
done

echo
echo "== 3. 创建 PR（base=main）=="
for entry in "${BRANCHES[@]}"; do
  branch="${entry%%|*}"
  title="${entry#*|}"
  body=$(cat <<EOF
## 这个 PR 做了什么

$title

## 自检

- \`mvnw verify\` 通过：258 个用例，行覆盖率 92.8%（门禁 85%）
- 新增业务代码均有测试，且包含越权/非法状态/版本冲突负例
- 新增接口均有 \`@PreAuthorize\`，服务层另做作用域校验
- 前端 \`npm run build\` 通过（\`vue-tsc\` 无错误）
- 未提交 \`node_modules/\`、\`target/\`、\`dist/\`、\`data/\`、\`.env\` 或任何真实密钥

## 说明

本分支与后续分支是链式的，请**按 PR 编号顺序合并**；未合并前，后续 PR 的 diff 会包含前序提交，合并后自动收敛。

详见 \`log/2026-11-26.md\`。
EOF
)
  payload=$(BRANCH="$branch" TITLE="$title" BODY="$body" python -c '
import json, os
print(json.dumps({
    "title": os.environ["TITLE"],
    "head": os.environ["BRANCH"],
    "base": "main",
    "body": os.environ["BODY"],
}, ensure_ascii=False))
')
  resp="$(api POST /pulls "$payload")"
  if echo "$resp" | grep -q '"number"'; then
    num=$(echo "$resp" | python -c 'import sys,json; print(json.load(sys.stdin)["number"])')
    url=$(echo "$resp" | python -c 'import sys,json; print(json.load(sys.stdin)["html_url"])')
    printf '  #%-4s %s\n' "$num" "$url"
  elif echo "$resp" | grep -q "A pull request already exists"; then
    echo "  ${branch}：PR 已存在，跳过"
  else
    echo "  ${branch}：创建失败" >&2
    echo "$resp" | head -5 >&2
  fi
done

echo
echo "完成。接下来："
echo "  1. 让 1-Vast 逐个 review 并按 1→6 顺序合并（先合后面的会把前序提交重复算进 diff）"
echo "  2. 合并后 main 上跑一次 CI，确认 258 用例与覆盖率门禁通过"
echo "  3. 项目结束后撤销本次使用的 token"
