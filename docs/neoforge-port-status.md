# NeoForge Port Status

## Architecture diagrams
See the current PlantUML baseline in [docs/architecture/README.md](architecture/README.md).

## Scope of the current slice
This is the first real NeoForge runtime slice for Proximity Crafting.

The goal of this slice was not full gameplay parity. The goal was to move NeoForge from "placeholder only" to a state where it has:
- a real module build
- real content registration
- a real menu/screen host path
- real packet transport
- real runtime adapters for the current common core
- a stable vanilla recipe book path

## What is implemented

### Module/build
- `:neoforge:compileJava` passes
- `:neoforge:build` passes
- the NeoForge module now produces a remapped jar

### NeoForge implementation note
- NeoForge 1.20.1 is currently hosted through a Forge-shaped Loom/tooling path in this branch:
  - `loom.platform=forge`
  - dependency: `net.neoforged:forge:${neoforge_version}`
- This is intentional for this branch because the direct NeoForge Loom path was not stable in the current Architectury setup.

### NeoForge bootstrap
- `ProximityCraftingNeoForge` initializes:
  - common bootstrap
  - block/item registration
  - menu type registration
  - packet registration
  - creative tab insertion
- client bootstrap registers:
  - client request sender
  - client runtime hooks
  - screen factory registration

### Content registration
- NeoForge now registers:
  - `proximity_crafting_table` block
  - `proximity_crafting_table` item
  - `proximity_crafting_menu` menu type
- Shared ids/bootstrap descriptors from `common` are used by the NeoForge module.

### Menu open flow
- `ProximityCraftingTableBlock` now opens the menu through the native Forge-like `NetworkHooks.openScreen(...)` path used by this NeoForge 1.20.1 runtime
- `ModMenuTypes` now creates the menu through `IForgeMenuType.create(...)`
- `BlockPos` is transferred to the menu through the extra menu buffer

### Networking
- NeoForge now has a real `ProximityCraftingNetwork`
- The current packet set is registered on NeoForge:
  - `C2SRequestRecipeFill`
  - `C2SAdjustRecipeLoad`
  - `C2SClearCraftGrid`
  - `C2SUpdateClientPreferences`
  - `C2SRequestRecipeBookSources`
  - `S2CRecipeFillFeedback`
  - `S2CRecipeBookSourceSnapshot`
- The NeoForge packets route through the same common request/response/session seams already used by Forge/Fabric:
  - `ClientRequestSender`
  - `ClientResponseDispatcher`
  - `ClientRuntimeHooks`
  - `ClientRecipeSessionState`

### Runtime adapters
- NeoForge now has runtime adapters for:
  - crafting session
  - craft consume session
  - scan options creation
  - item source slots
  - nearby container discovery
  - player inventory source collection
- The NeoForge runtime path uses the same common orchestration layers as Forge/Fabric for:
  - recipe fill/add/remove/refill
  - consume/craft-all
  - source composition
  - recipe-book supplemental source aggregation
  - result-take refill policy

### Screen/runtime integration
- NeoForge now has:
  - `ClientSetup`
  - `NeoForgeClientRequestSender`
  - `NeoForgeClientRuntimeHooks`
  - `NeoForgeActiveClientSessionHandle`
- `ProximityCraftingScreen` is now hosted in NeoForge and wired to the same common client-side state/presenter layers already extracted from Forge.

### Vanilla recipe book path
- NeoForge now includes:
  - recipe book accessors/mixins for hover resolution
  - screen-side hover-scroll support targeting recipe book entries
  - recipe-book fill/snapshot wiring through the shared common/session infrastructure
- This path has now been validated in-game on NeoForge 1.20.1.
- Verified behaviors:
  - table item appears correctly
  - table/menu open flow works
  - vanilla recipe book opens and renders inside the Proximity screen
  - recipe selection/fill through the vanilla recipe book works
  - hover scroll directly over vanilla recipe book items works
- For the vanilla recipe book path specifically, NeoForge 1.20.1 should now be treated as stable.

## What is intentionally minimal or incomplete

### Config persistence
- NeoForge now generates:
  - `config/proximitycrafting-client.toml`
  - `config/proximitycrafting-server.toml`
- The NeoForge config layer now persists the shared config records through a NeoForge-local file backend built on the shared config codec.
- This is a lightweight file-backed binding, not a deeper NeoForge config framework integration.

### Mod compat
- NeoForge JEI integration is not implemented in this slice.
- NeoForge EMI integration is not implemented in this slice.
- Backpack compat is not implemented on NeoForge.
- Current NeoForge compat classes for JEI/EMI are stubs/no-op placeholders so the module can compile cleanly.
- This is intentional for NeoForge 1.20.1:
  - this target is now capped at vanilla recipe book support
  - no further overlay/compat expansion is planned on this specific runtime line
  - the reason is the lack of a clean direct Architectury support path for NeoForge 1.20.1

### Validation depth
- This slice has been validated at build level:
  - `:common:compileJava`
  - `:forge:build`
  - `:fabric:build`
  - `:neoforge:build`
- It has also been validated in-game for the vanilla recipe book path.
- So the correct statement is:
  - NeoForge now has a real runtime host implementation and builds successfully
  - NeoForge 1.20.1 is stable for the vanilla recipe book path
  - NeoForge 1.20.1 is intentionally not a full-parity target

## What remains before a meaningful NeoForge gameplay milestone

### High priority
- Keep NeoForge 1.20.1 limited to the stable vanilla recipe book path
- Avoid feature work that would require deeper Architectury runtime support on this target

### Medium priority
- Confirm the current NeoForge container discovery path behaves correctly across vanilla containers
- Validate packet ordering and client session state behavior under repeated actions when needed for bugfixes

### Later
- No planned JEI/EMI expansion on NeoForge 1.20.1
- No planned backpack/extra source integrations on NeoForge 1.20.1
- Future NeoForge feature expansion should target a later version line with direct Architectury support

## Current recommended interpretation
- Forge remains the reference runtime.
- Fabric is stable for the vanilla recipe book path.
- NeoForge is no longer placeholder-only.
- NeoForge 1.20.1 is now in "stable vanilla recipe book runtime" state.
- NeoForge 1.20.1 is intentionally capped at vanilla recipe book support.
