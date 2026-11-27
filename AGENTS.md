# BookTracker – rules for coding agents

These rules apply to every agent in this repo (Claude Code, Codex). `CLAUDE.md` only imports this file.

## Repo

| Folder | What | Read first |
|---|---|---|
| `app/` | Mobile app, Compose Multiplatform for Android and iOS | [`app/AGENTS.md`](app/AGENTS.md) |
| `backend/` | REST API, Spring Boot, Keycloak | [`backend/Readme.md`](backend/Readme.md), [`backend/docs/architecture.md`](backend/docs/architecture.md) |
| `tools/` | Agent hooks, pinned ktlint, run-and-screenshot scripts | – |
| `.claude/`, `.codex/`, `.agents/` | Agent configuration: hooks, rules, skills | – |

**Working in `app/`? Read `app/AGENTS.md` before you change anything.** Codex loads nested `AGENTS.md` files only down to the folder it started in, so read it explicitly when you start at the root.

Start Claude Code and Codex in the repo root, so the hooks in `.claude/` and `.codex/` apply.

## Language and writing

- Code, comments, docs, commit messages and PR texts are in English. Talk to the user in their language.
- Write plainly: short sentences, common words, the answer first, no filler. Keep technical terms.
- Docs describe the current state and the reasons. Decisions with trade-offs go into an ADR (`app/docs/adr/`, `backend/docs/adr/`).

## Git

- **Never commit or push without the user's explicit yes.** A plan step or an earlier yes is not permission. Claude and Codex ask for approval on `git commit` and `git push`.
- **Never write to GitHub** (push, PRs, comments, issues, labels, releases) without the user's explicit yes for that one action. Reading is fine.
- New branches start from a fresh `origin/main` without tracking: `git fetch origin && git switch --no-track -c <branch> origin/main`. Names: `feature/<snake_case>` or `chore/<snake_case>`.
- `main` changes only through pull requests. Never bypass the `pre-push` hook (`--no-verify`).
- Commit messages: one capitalized line in the imperative, e.g. `Add the bookshelf list`. When asked for one, suggest it; do not commit.

## Way of working

- Bigger tasks go through stages: clarify the requirements, write a plan, get it approved, implement, then review. Do not skip from an answered question straight to code.
- Never decide a product question silently in code. Ask.
- **Never add a library or Gradle plugin without asking the user first**, even a small or test-only one.
- **Unit tests only.** No UI, screenshot, integration or container tests, and no dependencies for them.
- Keep secrets out of Git: `.env` files, `local.properties` and keys stay local. The hooks block edits to them; ask the user instead.
- Do not install SDKs, simulator runtimes, system images or other tools yourself. Tell the user what is missing.

## Hooks

The same scripts in `tools/agent-hooks/` run for both clients:

| When | Script | What it does |
|---|---|---|
| Before a tool call | `guard.sh` | Blocks obvious writes to `.env*`, `local.properties`, `gradlew*` and `gradle/wrapper/`, and `git` with options before `commit`/`push` (run plain `git commit` / `git push`) |
| After a file edit | `format-kotlin.sh` | Formats changed Kotlin files in `app/` with the pinned ktlint; reports what it cannot fix |
| After a file edit or shell command | `track-edits.sh` | Notes that this session changed something under `app/` |
| End of a turn | `check-app.sh` | Only if this session changed `app/`: builds and unit-tests the touched app modules (and the iOS project when needed); sends you back once per failed state |

After changing `guard.sh`, run its self-test: `tools/agent-hooks/test-guard.sh`.

Claude Code reads them from `.claude/settings.json`, Codex from `.codex/hooks.json`. Codex must trust the project hooks once (`/hooks`). Commit and push approval: `permissions.ask` in `.claude/settings.json`, `.codex/rules/git.rules` for Codex.
