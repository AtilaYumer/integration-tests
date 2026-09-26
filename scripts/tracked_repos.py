#!/usr/bin/env python3
"""Clones the repositories listed in tracked-repos.json and works out what changed since the last sync.

Subcommands:
  clone   Clone every tracked repository at the head of its branch into .work/repos/<name>.
  detect  Clone, then diff each repository from the commit recorded in .sync/state.json to its head.
          Writes .work/changes/summary.json, summary.md and one <name>.diff per changed repository,
          and prints GitHub Actions outputs (has_changes, impact).
  record  Store the head commits found by the last `detect` in .sync/state.json.

Uses only the standard library. GH_TOKEN, when set, is used to clone private GitHub repositories.
--config FILE uses another repository list (default tracked-repos.json).
A repository entry may set "path" to a local checkout instead of (or as well as) "url"; the committed
state of its branch is used, uncommitted edits are not. --local-root DIR clones every repository from
DIR/<name>, overriding both.
"""
import argparse
import fnmatch
import json
import os
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CONFIG = ROOT / "tracked-repos.json"  # replaced by --config
STATE = ROOT / ".sync" / "state.json"
WORK = ROOT / ".work"
REPOS = WORK / "repos"
CHANGES = WORK / "changes"

# Ordered from least to most severe; the overall impact is the most severe across repositories.
IMPACT_ORDER = ["none", "baseline", "implementation", "contract-extended", "contract-changed"]


def git(repo: Path, *args: str) -> str:
    return subprocess.run(["git", "-C", str(repo), *args], check=True, capture_output=True, text=True).stdout


def clone_url(repo: dict, local_root: str | None) -> str:
    if local_root:
        return str(Path(local_root).resolve() / repo["name"])
    if repo.get("path"):
        return str((CONFIG.parent / repo["path"]).resolve())
    url = repo["url"]
    token = os.environ.get("GH_TOKEN")
    if token and url.startswith("https://github.com/"):
        return url.replace("https://", f"https://x-access-token:{token}@", 1)
    return url


def clone(repo: dict, local_root: str | None) -> Path:
    target = REPOS / repo["name"]
    if target.exists():
        subprocess.run(["rm", "-rf", str(target)], check=True)
    REPOS.mkdir(parents=True, exist_ok=True)
    subprocess.run(
        ["git", "clone", "--quiet", "--branch", repo["branch"], clone_url(repo, local_root), str(target)],
        check=True,
    )
    # Never leave a token in the clone's config.
    git(target, "remote", "set-url", "origin", repo.get("url") or clone_url(repo, local_root))
    return target


def matches(path: str, patterns: list[str]) -> bool:
    return any(fnmatch.fnmatch(path, p) or path.startswith(p.rstrip("*").rstrip("/") + "/") for p in patterns)


def classify(repo: dict, checkout: Path, base: str, head: str, files: list[str]) -> str:
    contract = repo.get("contract")
    if contract and contract in files:
        diff = git(checkout, "diff", "--unified=0", f"{base}..{head}", "--", contract)
        removed = [l for l in diff.splitlines() if l.startswith("-") and not l.startswith("---")]
        return "contract-changed" if removed else "contract-extended"
    return "implementation"


def detect(local_root: str | None) -> dict:
    config = json.loads(CONFIG.read_text())
    state = json.loads(STATE.read_text()) if STATE.exists() else {}
    CHANGES.mkdir(parents=True, exist_ok=True)
    results = []

    for repo in config["repositories"]:
        name = repo["name"]
        checkout = clone(repo, local_root)
        head = git(checkout, "rev-parse", "HEAD").strip()
        base = state.get(name, {}).get("sha")
        entry = {"name": name, "base": base, "head": head, "files": [], "impact": "none",
                 "contract": repo.get("contract"), "tests": repo.get("tests", []),
                 "checkout": str(checkout.relative_to(ROOT))}

        if base is None:
            # First sync: nothing to compare against, the existing tests are assumed to match.
            entry["impact"] = "baseline"
        elif base != head:
            changed = git(checkout, "diff", "--name-only", f"{base}..{head}").splitlines()
            entry["files"] = [f for f in changed if matches(f, repo["watch"])]
            if entry["files"]:
                entry["impact"] = classify(repo, checkout, base, head, entry["files"])
                diff = git(checkout, "diff", f"{base}..{head}", "--", *entry["files"])
                (CHANGES / f"{name}.diff").write_text(diff)
                entry["diff"] = str((CHANGES / f"{name}.diff").relative_to(ROOT))
                entry["commits"] = git(checkout, "log", "--oneline", f"{base}..{head}").splitlines()
        results.append(entry)

    impact = max((r["impact"] for r in results), key=IMPACT_ORDER.index, default="none")
    summary = {"impact": impact, "repositories": results,
               "detectedAt": datetime.now(timezone.utc).isoformat(timespec="seconds")}
    (CHANGES / "summary.json").write_text(json.dumps(summary, indent=2) + "\n")
    (CHANGES / "summary.md").write_text(render_markdown(summary))
    return summary


def render_markdown(summary: dict) -> str:
    lines = [f"Overall impact: **{summary['impact']}**", ""]
    for r in summary["repositories"]:
        if r["impact"] in ("none", "baseline"):
            lines.append(f"- `{r['name']}`: {r['impact']} (at `{r['head'][:7]}`)")
            continue
        lines.append(f"- `{r['name']}` `{r['base'][:7]}..{r['head'][:7]}`: **{r['impact']}**")
        lines += [f"  - commit {c}" for c in r.get("commits", [])]
        lines += [f"  - changed `{f}`" for f in r["files"]]
    return "\n".join(lines) + "\n"


def record() -> None:
    summary = json.loads((CHANGES / "summary.json").read_text())
    state = json.loads(STATE.read_text()) if STATE.exists() else {}
    for r in summary["repositories"]:
        state[r["name"]] = {"sha": r["head"], "syncedAt": summary["detectedAt"]}
    STATE.parent.mkdir(parents=True, exist_ok=True)
    STATE.write_text(json.dumps(state, indent=2, sort_keys=True) + "\n")


def github_output(values: dict) -> None:
    out = os.environ.get("GITHUB_OUTPUT")
    text = "".join(f"{k}={v}\n" for k, v in values.items())
    if out:
        with open(out, "a") as f:
            f.write(text)
    sys.stdout.write(text)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("command", choices=["clone", "detect", "record"])
    parser.add_argument("--local-root", help="clone from DIR/<name> instead of the configured URLs")
    parser.add_argument("--config", help="repository list to use instead of tracked-repos.json")
    args = parser.parse_args()
    if args.config:
        global CONFIG
        CONFIG = Path(args.config).resolve()

    if args.command == "clone":
        for repo in json.loads(CONFIG.read_text())["repositories"]:
            clone(repo, args.local_root)
    elif args.command == "detect":
        summary = detect(args.local_root)
        sys.stderr.write((CHANGES / "summary.md").read_text())
        needs_tests = summary["impact"] not in ("none", "baseline")
        needs_state = any(r["impact"] != "none" for r in summary["repositories"])
        github_output({"impact": summary["impact"],
                       "has_changes": str(needs_tests).lower(),
                       "state_changed": str(needs_state).lower()})
    else:
        record()


if __name__ == "__main__":
    main()
