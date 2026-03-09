# Architectury Migration Plan

## Architecture diagrams
See the current PlantUML baseline in [docs/architecture/README.md](architecture/README.md).

## Version-branch portability
See the current checklist in [docs/version-branch-portability-checklist.md](version-branch-portability-checklist.md).

## Version-line process
See the required branch-opening and branch-divergence workflow in [docs/version-line-playbook.md](version-line-playbook.md).

## Current objective
Keep Proximity Crafting on a modular Architectury-based structure where:
- `common` owns reusable behavior, state, contracts, payloads, config semantics, and shared descriptors
- platform modules own runtime adapters, transport, bootstrap, screens, and loader-specific integrations

For this branch, the immediate target is a new version line:
- Minecraft `1.20`
- active loaders: `forge` and `fabric`
- reference runtime: `forge`
- Fabric starts as vanilla-only
- NeoForge is out of scope for this line

The migration is no longer about proving the module split. That baseline is already in place. The next goal is controlled parity work on top of it, starting with the `1.20` adaptation.

## Current state
- Project layout is established:
  - `common`
  - `forge`
  - `fabric`
- Forge remains the reference runtime.
- Fabric remains an active runtime target.
- NeoForge is not part of the active `1.20` branch scope.
- Shared assets/data/resources live in `common`.
- Shared architecture seams now exist for:
  - source scanning, discovery orchestration, and aggregation
  - crafting session operations
  - consume/result-take operations
  - menu runtime controllers and snapshot lifecycle
  - server request payloads and C2S request handling
  - request/response client flow
  - shared screen runtime controllers for action/sync/scroll flow
  - screen presenters and client session state
  - config semantics, shared config codecs, and shared descriptors
  - a common-boundary verification gate in `:common:check`

Detailed platform status:
- Forge: `docs/forge-port-status.md`
- Fabric: `docs/fabric-port-status.md`

## Current architectural boundary

### `common` owns
- source and slot abstractions
- source discovery orchestration, scan runtime, and aggregation
- crafting, consume, result, and session operations
- menu runtime controllers, shared tracked-container helpers, and snapshot build orchestration
- server request payloads and common C2S request handling
- client request/response contracts and client session state
- client runtime controllers for screen action/sync/scroll coordination
- presenters for non-visual screen state
- config defaults, normalized config records, and shared config codecs
- shared payload models
- shared ids and bootstrap descriptors
- no JEI/EMI toggle state, plugin ids, or overlay-specific follow-up decisions

### Platform modules own
- content registration and bootstrap
- concrete menu/screen hosts
- packet transport and registration
- snapshot transport callbacks and raw scan/source adapters
- packet context extraction and platform send APIs
- runtime adapters over the common ports
- final UI-side effects triggered by shared screen runtime controllers
- recipe-book runtime bridges for version-sensitive vanilla UI access
- loader-specific UI integration and compat
- optional overlay toggle persistence and compat-specific follow-up behavior
- loader-local config binding backends

### Platform-specific status
- Forge:
  - reference runtime for `1.20`
  - vanilla runtime plus JEI / EMI are the target scope
  - backpack compat remains on the Forge side when this line reaches parity
- Fabric:
  - active on the `1.20` line
  - vanilla recipe book runtime is the first-pass target
  - overlay integrations are not in scope for the initial adaptation

## Next migration targets
1. Retarget Forge from `1.20.1 / Forge 47 / Architectury 9` to `1.20 / Forge 46 / Architectury 8`.
2. Recover stable Forge vanilla runtime on `1.20`.
3. Re-enable JEI and EMI on Forge `1.20` after vanilla stability.
4. Retarget Fabric to `1.20` with the same shared-core seams.
5. Revisit Fabric overlays only after the `1.20` vanilla path is stable.
