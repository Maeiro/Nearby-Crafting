# Version-Branch Portability Checklist

## Intent
This checklist is not about making one codebase run across a wide range of Minecraft versions with no branching.

The target is narrower and more realistic:
- keep the project modular enough that nearby version patches can often reuse most of the architecture
- allow **version-specific branches** when Minecraft internals change
- keep those branches broad across loaders whenever practical

Recommended interpretation:
- one branch per Minecraft version line when needed
- shared `common + platform` architecture inside each version branch
- reuse adjacent branches where the runtime/API delta is still small enough to justify it

## What "good enough" looks like
- The project is not Forge-only in structure.
- Most gameplay behavior lives in `common`.
- Each loader mainly hosts:
  - bootstrap
  - registries
  - packet transport
  - menu/screen runtime
  - loader-specific compat
- A new Minecraft patch should primarily require:
  - version-specific loader/runtime adapters
  - targeted menu/screen/mixin updates
  - selective compat adjustments

## Completed foundations
- [x] Shared core behavior is centered in `common`
- [x] Forge is no longer the only runtime host
- [x] Fabric has a real runtime host
- [x] NeoForge has a real runtime host
- [x] Request/response client flow is abstracted behind common seams
- [x] Source discovery orchestration is separated from raw loader discovery
- [x] Screen presenters and session state have started moving out of loader UI classes
- [x] Shared ids/bootstrap descriptors are no longer hardcoded only in Forge

## Remaining work to improve version-branch portability
- [ ] Keep `common` free from loader APIs and loader-shaped assumptions
- [ ] Reduce direct dependency on fragile Minecraft-version-specific UI internals where a common presenter/state seam is possible
- [ ] Isolate all recipe book accessors/mixins clearly per loader and per version-sensitive runtime path
- [ ] Keep packet payload models and request/response state transitions version-local but loader-neutral inside each branch
- [ ] Keep menu/result-slot/session logic concentrated behind small runtime ports instead of spreading version-sensitive logic across many host classes
- [ ] Keep scanning/discovery split clean:
  - `common` owns orchestration
  - per-loader code owns raw discovery only
- [ ] Prevent compat code from leaking into shared core:
  - JEI
  - EMI
  - backpacks
  - future mod-specific bridges
- [ ] Make config semantics stay shared even when config file binding differs by loader/version
- [ ] Keep architecture diagrams and port-status docs updated when a version branch diverges materially
- [ ] Document branch strategy explicitly when opening a new Minecraft version line

## Branch strategy guideline
- Prefer reusing the previous adjacent version branch first.
- Create a new branch when:
  - Minecraft internals changed enough to make patching noisy
  - loader APIs changed in a way that would pollute the current branch
  - mixins/accessors/menu flow diverged enough to hurt maintainability
- Keep the branch broad across loaders when possible:
  - example target shape: `1.20.2` branch with Forge/Fabric/NeoForge hosts for that version line
- Do not force a loader/version target to feature parity if the underlying tooling path is weak:
  - example: NeoForge `1.20.1` staying capped at vanilla recipe book support

## Current practical conclusion
- The project is already modular enough to support this branch-per-version strategy.
- It is not yet fair to call it "version-agnostic".
- The healthier claim is:
  - Proximity Crafting now has a shared-core multiplatform architecture that is designed to make adjacent-version branches cheaper to create and maintain.
