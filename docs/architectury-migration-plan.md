# Architectury Migration Plan

## Architecture diagrams
See the current PlantUML baseline in [docs/architecture/README.md](architecture/README.md).

## Version-branch portability
See the current checklist in [docs/version-branch-portability-checklist.md](version-branch-portability-checklist.md).

## Current objective
Keep Proximity Crafting on a modular Architectury-based structure where:
- `common` owns reusable behavior, state, contracts, payloads, config semantics, and shared descriptors
- platform modules own runtime adapters, transport, bootstrap, screens, and loader-specific integrations

The migration is no longer about proving the module split. That baseline is already in place. The next goal is controlled parity work on top of it.

## Current state
- Project layout is established:
  - `common`
  - `forge`
  - `fabric`
  - `neoforge`
- Forge remains the reference runtime.
- Fabric 1.20.1 has a real runtime host and a stable vanilla recipe book path.
- NeoForge 1.20.1 has a real runtime host and a stable vanilla recipe book path, but that line is intentionally capped there.
- Shared assets/data/resources live in `common`.
- Shared architecture seams now exist for:
  - source scanning and aggregation
  - crafting session operations
  - consume/result-take operations
  - request/response client flow
  - screen presenters and client session state
  - config semantics and shared descriptors

Detailed platform status:
- Fabric: `docs/fabric-port-status.md`
- NeoForge: `docs/neoforge-port-status.md`

## Current architectural boundary

### `common` owns
- source and slot abstractions
- source discovery orchestration and aggregation
- crafting, consume, result, and session operations
- client request/response contracts and client session state
- presenters for non-visual screen state
- config defaults, normalized config records, and lightweight persistence helper
- shared payload models
- shared ids and bootstrap descriptors

### Platform modules own
- content registration and bootstrap
- concrete menu/screen hosts
- packet transport and registration
- runtime adapters over the common ports
- recipe-book runtime bridges for version-sensitive vanilla UI access
- loader-specific UI integration and compat

### Platform-specific status
- Forge:
  - reference runtime
  - current full feature set
  - JEI / EMI / Sophisticated Backpacks integrations remain here
- Fabric:
  - stable for the vanilla recipe book path
  - vanilla recipe book runtime access is isolated behind a platform-local bridge
  - overlay integrations still pending
- NeoForge 1.20.1:
  - stable for the vanilla recipe book path
  - vanilla recipe book runtime access is isolated behind a platform-local bridge
  - intentionally limited to that scope
  - does not target further overlay or backpack parity on this version line

## Next migration targets
1. Implement EMI integration on Fabric.
2. Implement JEI on Fabric only if it remains part of the target feature set.
3. Keep NeoForge 1.20.1 in maintenance-only mode for vanilla-book issues.
4. Revisit whether the lightweight Fabric/NeoForge config persistence should later move to loader-native config frameworks.
5. Continue moving any remaining high-value screen/menu runtime glue out of platform hosts only when it reduces future parity cost.
