# Fabric Port Status

## Scope of the current slice
This is the first real Fabric runtime slice for Proximity Crafting.

The goal of this slice was not full gameplay parity. The goal was to move Fabric from "scaffold only" to a state where it has:
- a real module build
- real content registration
- a real menu/screen host path
- real packet transport
- real runtime adapters for the current common core

## What is implemented

### Module/build
- `:fabric:compileJava` passes
- `:fabric:build` passes
- the Fabric module now produces a remapped jar

### Fabric bootstrap
- `ProximityCraftingFabric` initializes:
  - common bootstrap
  - block/item registration
  - menu type registration
  - packet registration
- `ProximityCraftingFabricClient` initializes:
  - client request sender
  - client runtime hooks
  - screen factory registration

### Content registration
- Fabric now registers:
  - `proximity_crafting_table` block
  - `proximity_crafting_table` item
  - `proximity_crafting_menu` menu type
- Shared ids/bootstrap descriptors from `common` are used by the Fabric module.

### Menu open flow
- `ProximityCraftingTableBlock` now opens the menu through Architectury `MenuRegistry.openExtendedMenu(...)`
- `ModMenuTypes` now creates the menu through `MenuRegistry.ofExtended(...)`
- `BlockPos` is transferred to the menu through the extra menu buffer

### Networking
- Fabric now has a real `ProximityCraftingNetwork` using Architectury `NetworkChannel`
- The current packet set is registered on Fabric:
  - `C2SRequestRecipeFill`
  - `C2SAdjustRecipeLoad`
  - `C2SClearCraftGrid`
  - `C2SUpdateClientPreferences`
  - `C2SRequestRecipeBookSources`
  - `S2CRecipeFillFeedback`
  - `S2CRecipeBookSourceSnapshot`
- The Fabric packets route through the same common request/response/session seams already introduced for Forge:
  - `ClientRequestSender`
  - `ClientResponseDispatcher`
  - `ClientRuntimeHooks`
  - `ClientRecipeSessionState`

### Runtime adapters
- Fabric now has runtime adapters for:
  - crafting session
  - craft consume session
  - scan options creation
  - item source slots
  - nearby container discovery
  - player inventory source collection
- The Fabric runtime path uses the same common orchestration layers as Forge for:
  - recipe fill/add/remove/refill
  - consume/craft-all
  - source composition
  - recipe-book supplemental source aggregation
  - result-take refill policy

### Screen/runtime integration
- Fabric now has:
  - `ClientSetup`
  - `FabricClientRequestSender`
  - `FabricClientRuntimeHooks`
  - `FabricActiveClientSessionHandle`
- `ProximityCraftingScreen` is now hosted in Fabric and wired to the same common client-side state/presenter layers already extracted from Forge.

## What is intentionally minimal or incomplete

### Config persistence
- Fabric currently uses an in-memory `ProximityCraftingConfig` wrapper.
- It provides the shared config records to the runtime, but it does not yet bind them to a persisted Fabric config file.
- This means Fabric defaults are functional for compile/build and runtime wiring, but user-facing config persistence is not implemented yet.

### Mod compat
- Fabric JEI/EMI integration is not implemented in this slice.
- Current Fabric compat classes for JEI/EMI are stubs/no-op placeholders so the module can compile cleanly.
- Sophisticated Backpacks compat is not implemented on Fabric.
- The Fabric backpack source collector currently returns no backpack sources.

### Validation depth
- This slice has been validated at build level:
  - `:common:compileJava`
  - `:forge:build`
  - `:fabric:build`
- This slice has not yet been fully smoke-tested in-game on Fabric.
- So the correct statement is:
  - Fabric now has a real runtime host implementation and builds successfully
  - Fabric gameplay parity is not yet confirmed

## What remains before a meaningful Fabric gameplay milestone

### High priority
- Smoke-test the Fabric runtime in-game:
  - open table
  - fill recipe
  - take result
  - clear grid
  - scroll add/remove
  - recipe-book source sync
- Add a persisted Fabric config binding
- Validate that menu open/close, source return, and result-take refill behave correctly on Fabric

### Medium priority
- Confirm the current Fabric container discovery path behaves correctly across vanilla containers
- Validate packet ordering and client session state behavior under repeated actions
- Review whether any remaining menu runtime glue should be extracted before deeper Fabric iteration

### Later
- Implement EMI on Fabric
- Decide whether a Fabric-only recipe overlay integration is needed before parity
- Revisit backpack/extra source integrations in a Fabric-specific way

## Current recommended interpretation
- Forge remains the reference runtime.
- Fabric is no longer just scaffold.
- Fabric is now in "build-valid runtime host" state.
- The next step is not more blind refactor. The next step is targeted Fabric smoke testing and then filling the missing runtime features deliberately.
