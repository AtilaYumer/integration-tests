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
4. If anything relevant changed, it starts both services at their new heads and runs Claude
   (`anthropics/claude-code-action`) with `.github/prompts/sync-tests.md`. Claude adds tests for new
   behaviour, refactors tests for changed behaviour, runs `mvn verify`, and writes a report. It may only
   touch `src/test/`.
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
| `ANTHROPIC_API_KEY` | Claude in the sync workflow (or swap in `claude_code_oauth_token`) |
| `TRACKED_REPOS_TOKEN` | Cloning the services if they are private (read access to their contents) |
| `SYNC_PR_TOKEN` | Optional. Opening the sync PR with a PAT so the Integration tests workflow runs on it |

In each service repository, set `INTEGRATION_TESTS_TOKEN` (a token that can send a `repository_dispatch`
here) to trigger a sync on every push to `main`; without it the schedule still picks changes up.

To track another service, add it to `tracked-repos.json` and teach `scripts/start-services.sh` how to
start it.

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
