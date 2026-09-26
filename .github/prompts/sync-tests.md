# Sync the integration tests with upstream changes

You maintain the Rest Assured integration tests in this repository. One or more tracked service
repositories changed since the last sync. Your job is to bring the tests in line with those changes.

## Inputs

- `.work/changes/summary.json`: for each tracked repository, the previous synced commit (`base`), the
  new commit (`head`), the watched files that changed, an `impact` level, and the test directories that
  cover it (`tests`).
- `.work/changes/<name>.diff`: the diff of the watched files for each changed repository.
- `.work/repos/<name>`: a full clone of each service at its new head. Use `git -C .work/repos/<name> log`
  or `git -C .work/repos/<name> show` for more context, and read `api/openapi.yaml` there for the
  current contract.
- The services are already running at those heads: catalog-service on http://localhost:8081 and
  order-service on http://localhost:8082.

Impact levels, from least to most severe:

- `implementation`: code changed but the committed OpenAPI contract did not. Behaviour may still have
  changed (validation, status codes, calculations, calls between services).
- `contract-extended`: the OpenAPI contract only gained lines (new endpoints, fields or parameters).
- `contract-changed`: the OpenAPI contract lost or altered lines (removed or renamed endpoints or
  fields, changed types or status codes).

## What to do

1. Read the summary and diffs and decide, per change, what it means for API consumers.
2. Read the existing tests in the directories listed under `tests`, and `CLAUDE.md` for conventions.
3. Then:
   - For new behaviour (new endpoints, fields, parameters, validation rules, status codes), **add**
     tests next to the existing ones for that resource.
   - For changed or removed behaviour, **refactor** the affected tests to the new contract. Remove a
     test only when the behaviour it covers no longer exists, and say so in the report.
   - When a change crosses services (for example a catalog field that order-service reads), update or
     add a test under `flows/`.
   - When nothing observable changed (refactoring, logging, internal renames), change no tests.
4. Run `mvn -B verify` and make the suite pass against the running services.
   If a test fails because the service looks wrong (for example it now returns 500, or it contradicts
   its own OpenAPI contract), do **not** bend the test to match. Leave the test asserting the correct
   behaviour and flag it in the report as a suspected regression.

## Rules

- Only edit files under `src/test/`. Never edit `.work/`, `.sync/`, `.github/`, `pom.xml` or the
  service clones.
- Follow the conventions in `CLAUDE.md`.

## Report

Finish by writing `.work/claude-report.md`, which becomes part of the pull request description:

- one bullet per upstream change with your impact assessment and what you did about it
  (added / refactored / removed / no change needed), naming the test classes and methods;
- a "Suspected regressions" section if any, with the failing test and why you think the service is wrong;
- the final `mvn verify` result (tests run, failures).
