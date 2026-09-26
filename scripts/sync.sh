#!/usr/bin/env bash
# One entry point for syncing the integration tests with the tracked service repositories.
# Runs the same way on a laptop and in CI:
#
#   1. detect   clone every tracked repository and diff it against the last synced commit
#   2. agent    if something relevant changed, start the services at their new heads and let a coding
#               agent (Claude Code or Copilot CLI) apply the sync-integration-tests skill
#   3. verify   run the whole suite against the new heads
#   4. record   store the new commits in .sync/state.json and write .work/pr-body.md
#   5. commit   optionally commit the result on a branch (never pushes)
#
# Usage: scripts/sync.sh [options]
#   --agent claude|copilot   coding agent CLI to use (default: claude, or $SYNC_AGENT)
#   --repos FILE             repository list (default: tracked-repos.json)
#   --local-root DIR         use DIR/<name> checkouts instead of the configured URLs/paths
#   --detect-only            report what changed and stop
#   --commit                 commit src/test and .sync/state.json on branch auto/sync-tests
#   --model NAME             model passed to the agent CLI
#
# Exit status: 0 when nothing changed or the suite is green, 2 when the updated suite fails
# (inspect .work/claude-report.md: it may be a service regression), 1 on any other error.
#
# Needs: git, python3, Java 21, Maven, curl, and the chosen CLI logged in
# (claude: ANTHROPIC_API_KEY or `claude login`; copilot: COPILOT_GITHUB_TOKEN/GH_TOKEN or `copilot login`,
# plus GH_HOST for GitHub Enterprise).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

AGENT="${SYNC_AGENT:-claude}"
REPOS=""
LOCAL_ROOT=""
DETECT_ONLY=false
COMMIT=false
MODEL=""
while [ $# -gt 0 ]; do
  case "$1" in
    --agent) AGENT="$2"; shift 2 ;;
    --repos) REPOS="$2"; shift 2 ;;
    --local-root) LOCAL_ROOT="$2"; shift 2 ;;
    --detect-only) DETECT_ONLY=true; shift ;;
    --commit) COMMIT=true; shift ;;
    --model) MODEL="$2"; shift 2 ;;
    -h|--help) sed -n '2,29p' "$0"; exit 0 ;;
    *) echo "Unknown option: $1" >&2; exit 1 ;;
  esac
done

output() {  # key=value for GitHub Actions; harmless elsewhere
  [ -n "${GITHUB_OUTPUT:-}" ] && echo "$1" >> "$GITHUB_OUTPUT"
  return 0
}

repo_args=()
[ -n "$REPOS" ] && repo_args+=(--config "$REPOS")
[ -n "$LOCAL_ROOT" ] && repo_args+=(--local-root "$LOCAL_ROOT")

# 1. detect
mkdir -p .work
python3 scripts/tracked_repos.py detect "${repo_args[@]}" > .work/detect.out
cat .work/detect.out
has_changes=$(sed -n 's/^has_changes=//p' .work/detect.out)
state_changed=$(sed -n 's/^state_changed=//p' .work/detect.out)
impact=$(sed -n 's/^impact=//p' .work/detect.out)
output "impact=$impact"
output "has_changes=$has_changes"
output "state_changed=$state_changed"

if $DETECT_ONLY; then
  exit 0
fi
if [ "$state_changed" != "true" ]; then
  echo "Nothing relevant changed since the last sync."
  exit 0
fi

verify="skipped"
if [ "$has_changes" = "true" ]; then
  if [ -n "$(git status --porcelain)" ]; then
    echo "The working tree has uncommitted changes; commit or stash them so the agent's edits stay separate." >&2
    exit 1
  fi
  # 2. agent
  trap 'scripts/stop-services.sh >/dev/null' EXIT
  scripts/start-services.sh
  rm -f .work/claude-report.md
  prompt="Use the sync-integration-tests skill. Overall impact detected: $impact."
  echo "Running the $AGENT agent"
  case "$AGENT" in
    claude)
      model_args=(); [ -n "$MODEL" ] && model_args=(--model "$MODEL")
      claude -p "$prompt" "${model_args[@]}" --max-turns 80 --permission-mode dontAsk \
        --allowedTools "Skill,Agent,Task,Read,Write,Edit,Glob,Grep,Bash(mvn:*),Bash(git -C .work/repos/*),Bash(curl:*)" \
        | tee .work/agent.log
      ;;
    copilot)
      model_args=(); [ -n "$MODEL" ] && model_args=(--model "$MODEL")
      copilot -p "$prompt" "${model_args[@]}" --no-ask-user \
        --allow-tool 'write' --allow-tool 'shell(mvn:*)' --allow-tool 'shell(git:*)' --allow-tool 'shell(curl:*)' \
        --deny-tool 'shell(git push:*)' --deny-tool 'shell(git commit:*)' \
        | tee .work/agent.log
      ;;
    *) echo "Unsupported agent: $AGENT (use claude or copilot)" >&2; exit 1 ;;
  esac

  # Only src/test may change. The tree was clean before the agent ran, so this only undoes the agent's
  # edits elsewhere; untracked files it created outside src/test are listed, not deleted.
  git checkout -- . ':(exclude)src/test'
  stray=$(git status --porcelain --untracked-files=all | grep -v ' src/test/' || true)
  [ -n "$stray" ] && echo "The agent created files outside src/test (left in place, not committed):" && echo "$stray"

  # 3. verify
  if mvn -B verify; then verify="success"; else verify="failure"; fi
  output "verify=$verify"
fi

# 4. record
python3 scripts/tracked_repos.py record "${repo_args[@]}"
{
  echo "Automated sync of the integration tests with the tracked service repositories."
  echo
  echo "## What changed upstream"
  cat .work/changes/summary.md
  echo
  if [ -f .work/claude-report.md ]; then
    echo "## Test changes ($AGENT)"
    cat .work/claude-report.md
    echo
  fi
  if [ "$verify" != "skipped" ]; then
    echo "## Verification"
    echo "\`mvn verify\` against the new service heads: **$verify**"
  fi
} > .work/pr-body.md
echo "Wrote .work/pr-body.md"

# 5. commit
if $COMMIT; then
  git checkout -B auto/sync-tests
  git add src/test .sync/state.json
  { echo "Sync integration tests (impact: $impact)"; echo; cat .work/pr-body.md; } > .work/commit-msg.txt
  git commit -q -F .work/commit-msg.txt
  echo "Committed on branch auto/sync-tests; review it and push when ready."
fi

[ "$verify" = "failure" ] && exit 2
exit 0
