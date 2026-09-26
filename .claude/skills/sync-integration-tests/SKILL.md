---
name: sync-integration-tests
description: Bring the Rest Assured integration tests in line with changes in the tracked service repositories. Use when .work/changes/summary.json exists (after scripts/tracked_repos.py detect), or when asked to sync, update or refactor the integration tests after a service changed.
---

# Sync integration tests with upstream changes

One or more tracked service repositories changed since the last sync. You coordinate the update:
you assess each change, hand the test writing to the `integration-test-author` agent, verify the
result and write the report.

## 1. Make sure the inputs exist

- `.work/changes/summary.json` must exist. If it does not, stop and say that
  `python3 scripts/tracked_repos.py detect` has to run first (add `--local-root DIR` for local clones).
- The services must be running at their new heads: `curl -fs http://localhost:8081/actuator/health`
  and `curl -fs http://localhost:8082/actuator/health`. The workflow starts them; locally,
  `scripts/start-services.sh` does.

`summary.json` lists, per repository: `base` (last synced commit), `head` (new commit), `files`
(watched files that changed), `impact`, `tests` (test directories that cover it), `diff` (path to the
diff of the watched files) and `checkout` (a full clone at `head`, e.g. `.work/repos/catalog-service`).

## 2. Assess the impact of every change

For each repository whose `impact` is not `none` or `baseline`, read its diff, and where the diff is
not enough, `git -C <checkout> show <commit>` and `<checkout>/api/openapi.yaml`. Classify each
observable change for API consumers:

| Kind | Examples | Action |
| --- | --- | --- |
| New behaviour | new endpoint, field, query parameter, validation rule, status code | **add** tests |
| Changed behaviour | renamed or moved endpoint, changed field type, new required field, different status code | **refactor** affected tests |
| Removed behaviour | endpoint or field deleted | **remove** only the tests for it |
| Cross-service | a field or endpoint that another tracked service calls | also cover it in `flows/` |
| Nothing observable | refactoring, logging, internal renames | no test change |

The detector's `impact` is a hint (`implementation`, `contract-extended`, `contract-changed`); your
own reading of the diff decides. An `implementation` change can still alter behaviour, for example
a new validation rule or a different calculation.

## 3. Delegate the test changes

Start the `integration-test-author` agent once per repository that needs test changes, in parallel
when there are several. Run them in the foreground and wait for every one to return: in CI the
session ends when you stop, and a background agent would be cut off. Give each one a self-contained brief:

- the repository name, the commits, and the diff path;
- your list of classified changes with the action for each;
- the test directories it owns (`tests` in the summary) and that `flows/` is shared, so only one
  agent edits a given `flows/` class; name which one.

Each agent reports back the files and test methods it added, changed or removed, and anything that
looks like a service bug.

## 4. Verify

Run `mvn -B verify` yourself once all agents are done. If tests fail:

- a test that is wrong for the new contract: fix it, or send it back to the agent with the failure;
- a service that looks wrong (a 500, or a response that contradicts its own `api/openapi.yaml`):
  do not bend the test. Keep it asserting the correct behaviour and list it as a suspected regression.

## 5. Report

Write `.work/claude-report.md`; the workflow puts it in the pull request description:

- one bullet per upstream change: the commit, your classification, and what was done
  (added / refactored / removed / no change needed) naming test classes and methods;
- a **Suspected regressions** section when there are any, with the failing test and why;
- the final `mvn verify` line (tests run, failures, errors).

## Rules

- Only `src/test/` may change. Never edit `.work/`, `.sync/`, `.github/`, `.claude/`, `pom.xml` or
  the service clones.
- Test conventions live in `CLAUDE.md`; the agent follows them and so do your own fixes.
