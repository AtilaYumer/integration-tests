# integration-tests

Rest Assured integration tests for [catalog-service](https://github.com/AtilaYumer/catalog-service) and
[order-service](https://github.com/AtilaYumer/order-service), kept in step with those services
automatically.

## How the automatic sync works

1. `tracked-repos.json` lists each service repository, the branch to follow, which paths matter
   (`watch`), where its OpenAPI contract lives, and which test packages cover it.
2. `.sync/state.json` records the last commit of each service the tests were synced with.
3. The **Sync tests** workflow (weekdays at 05:00 UTC, on demand, or when a service pushes a
   `tracked-repo-updated` dispatch) runs `scripts/tracked_repos.py detect`. It clones every service,
   diffs the watched paths since the recorded commit and grades the impact:
   - `implementation`: code changed, contract did not;
   - `contract-extended`: the OpenAPI contract only gained lines;
   - `contract-changed`: the contract lost or changed lines.
4. If anything relevant changed, it starts both services at their new heads and runs a coding agent
   (Claude Code by default, or Copilot CLI) with the **`sync-integration-tests` skill**
   (`.claude/skills/sync-integration-tests/SKILL.md`). The skill classifies each upstream change,
   hands the hands-on work to the **`integration-test-author` agent**
   (`.claude/agents/integration-test-author.md`), one per changed service, then runs `mvn verify`
   and writes a report. Only `src/test/` may change.
5. The workflow records the new commits in `.sync/state.json` and opens a PR on `auto/sync-tests` with
   the upstream summary, Claude's report and the verification result. A red suite opens the PR as a
   draft, since it can mean a service regression rather than a stale test. No new sync runs while that
   PR is open.

The **Integration tests** workflow runs the suite against the current service heads on every push and
PR here, and when a service dispatches `tracked-repo-updated`.

## Setup

Repository secrets:

| Secret | Needed for |
| --- | --- |
| `ANTHROPIC_API_KEY` | Claude Code in the sync workflow (when `SYNC_AGENT` is `claude`, the default) |
| `COPILOT_GITHUB_TOKEN` | Copilot CLI in the sync workflow (when the `SYNC_AGENT` variable is `copilot`) |
| `TRACKED_REPOS_TOKEN` | Cloning the services if they are private (read access to their contents) |
| `SYNC_PR_TOKEN` | Optional. Opening the sync PR with a PAT so the Integration tests workflow runs on it |

In each service repository, set `INTEGRATION_TESTS_TOKEN` (a token that can send a `repository_dispatch`
here) to trigger a sync on every push to `main`; without it the schedule still picks changes up.

To track another service, add it to `tracked-repos.json` and teach `scripts/start-services.sh` how to
start it.

## Running the sync locally

`scripts/sync.sh` is the whole sync in one command, and it is exactly what the workflow runs, so
anything that works on your machine works unattended later.

```bash
scripts/sync.sh --detect-only                      # just show what changed since the last sync
scripts/sync.sh                                    # detect, update tests with Claude Code, verify, record
scripts/sync.sh --agent copilot                    # same, with GitHub Copilot CLI
scripts/sync.sh --repos my-repos.json --commit     # your own repo list; commit on branch auto/sync-tests
```

It needs a clean working tree, Java 21, Maven, and the chosen CLI logged in (`claude login` or
`ANTHROPIC_API_KEY`; `copilot login` or `COPILOT_GITHUB_TOKEN`, plus `GH_HOST` on GitHub Enterprise).
It never pushes. Exit status 2 means the updated suite is red; read `.work/claude-report.md` to see
whether a test or a service is at fault.

**Your own repository list.** Copy `tracked-repos.json` and point entries at local checkouts with
`"path"` instead of `"url"` (relative paths resolve from the list's folder). The committed state of the
configured branch is used, so commit service changes before syncing:

```json
{
  "repositories": [
    {
      "name": "catalog-service",
      "path": "../catalog-service",
      "branch": "main",
      "contract": "api/openapi.yaml",
      "watch": ["api/**", "src/main/java/**"],
      "tests": ["src/test/java/com/example/it/catalog", "src/test/java/com/example/it/flows"]
    }
  ]
}
```

**Going unattended.** When you are ready, the **Sync tests** workflow runs the same script on a
schedule and turns the result into a PR. Set the `SYNC_AGENT` repository variable to `claude`
(default, needs `ANTHROPIC_API_KEY`) or `copilot` (needs `COPILOT_GITHUB_TOKEN`, a fine-grained PAT
with the Copilot Requests permission). A cron job on any machine calling
`scripts/sync.sh --commit` works too.

**Skill and agent directly.** In Claude Code, `/sync-integration-tests` runs the procedure by hand
after `scripts/tracked_repos.py detect` and `scripts/start-services.sh`, and the
`integration-test-author` agent handles one-off test work. Copilot reads the same skill from
`.claude/skills/` and its copy of the agent from `.github/agents/`.

## Running locally

```bash
python3 scripts/tracked_repos.py clone        # clones the services into .work/repos
scripts/start-services.sh                     # or: scripts/start-services.sh ../ (your own checkouts)
mvn verify
scripts/stop-services.sh
```

Point the tests at other environments with `-Dcatalog.base-url=... -Dorder.base-url=...` or the
`CATALOG_BASE_URL` / `ORDER_BASE_URL` environment variables.

To try change detection without GitHub, run
`python3 scripts/tracked_repos.py detect --local-root ../` against local clones of the services.
