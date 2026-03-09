# Version Line Playbook

## Purpose
This document defines the required process when opening or materially diverging a Minecraft version branch.

The target model is:
- one branch per Minecraft version line when needed
- shared `common + platform` architecture inside that branch
- explicit platform scope per version line

This playbook is mandatory whenever a new version branch is created or an existing version branch diverges enough to change runtime boundaries.

## Branch creation rules
- Prefer reusing the nearest previous version branch first.
- Create a new branch when:
  - Minecraft internals changed enough to make patching noisy
  - loader APIs changed in a way that would pollute the current branch
  - menu/screen/mixin behavior diverged enough to hurt maintainability
- Use a version branch name:
  - `version/<mc-version>`
  - example: `version/1.20.1`

## Required kickoff updates
Before feature work starts on the new version line, update or create:
- `docs/version-branch-portability-checklist.md`
  - keep it accurate for the new branch state
- per-platform port status docs that apply to the branch
  - `docs/fabric-port-status.md`
  - `docs/neoforge-port-status.md`
  - or new per-version variants if the branch diverges enough to require them
- `docs/architecture/*.puml`
  - update diagrams if module boundaries, runtime seams, or platform scope changed materially

Also refresh the local `CONTEXT.md` handoff file for the new branch.

## Documentation gate for material divergence
Treat documentation updates as required when any of these change:
- a platform gains or loses a runtime host
- a platform scope is capped or expanded
- a new shared seam moves behavior into or out of `common`
- a platform starts using a different runtime path for menu, screen, recipe book, config, or network handling

Minimum required updates for a material divergence:
- affected port-status doc
- `docs/version-branch-portability-checklist.md`
- at least one PlantUML diagram if the architecture boundary changed

## Common-boundary guardrail
Run:

```powershell
./gradlew :common:check
```

This now includes `verifyCommonBoundary`, which fails if loader APIs or loader-shaped compat state leak into `common`.

Use this check before merging refactors that touch shared core architecture.

## Version-line decision record
Every version branch should explicitly record:
- which loaders are active in that line
- which loader is the feature reference runtime
- which platforms are capped in scope
- whether config binding, recipe book runtime, or compat paths differ from the previous line

Do not assume parity. Record the intended scope directly.
