# Architectury Migration Plan

## Current objective
Move Proximity Crafting from a single Forge module to a modular Architectury layout without breaking the Forge 1.20.1 implementation.

## Decisions already applied
- Project layout now uses:
  - `common`
  - `forge`
  - `fabric`
  - `neoforge`
- The first extracted slice is the planning/source core, not the full service layer.
- Platform bridges use plain Java interfaces.
- `common` uses Architectury API minimally.
- Forge remains the only release target in this phase.

## What is already migrated
- Shared assets/data/resources moved to `common`.
- Forge metadata moved to `forge`.
- The following types now live in `common`:
  - `ProximityCrafting`
  - `IngredientSourcePool`
  - `ItemSourceRef`
  - `ItemSourceSlot`
  - `SlotIdentity`
  - `SourcePriority`
  - `ScanOptions`
  - `SourceCollector`
  - `ExtractionPlan`
  - `ExtractionCommitResult`
  - `PlannedExtraction`
  - `FillResult`
- The recipe fill core now also lives in `common`:
  - `CraftingSessionPort`
  - `RecipeFillOperations`
- Common networking/state payloads now exist for the first shared packet boundary:
  - `RecipeBookSourceEntry`
  - `RecipeBookSourceSnapshotPayload`
  - `RecipeFillFeedbackPayload`
- The shift-craft consume helper now also lives in `common`:
  - `CraftConsumeSessionPort`
  - `CraftConsumeOperations`
- Forge now provides:
  - `ForgeCraftingSessionAdapter`
  - `ForgeCraftConsumeSessionAdapter`
  - `ForgeScanOptionsFactory`
  - thin `RecipeFillService` and `CraftConsumeService` facades delegating into common
- Forge now owns the runtime/bootstrap and current gameplay implementation.

## Current platform state
- `forge`: active and validated by `:forge:build`
- `fabric`: scaffolded and validated by `:fabric:compileJava`
- `neoforge`: placeholder module only for now

## Why NeoForge is still placeholder
NeoForge 1.20.1 setup through the current Architectury/Loom stack fails during configuration in this branch. The module exists intentionally, but is not yet wired into the shared runtime path.

## Current boundary after phase 2
- `RecipeFillOperations` no longer depends on:
  - `ProximityCraftingMenu`
  - `ProximityCraftingConfig`
  - Forge-only classes
- `ProximityInventoryScanner` remains Forge-side, but now consumes `ScanOptions` as the scan contract.
- Recipe book snapshot building also uses the Forge scan boundary via `ForgeScanOptionsFactory`.
- Forge transport still owns packet registration and delivery, but the snapshot/feedback payload model is now shared in `common`.

## Current boundary after phase 3
- `common` now also owns the outbound client request contract:
  - `ClientRequestSender`
  - `ProximityClientServices`
- Forge provides the active outbound bridge through:
  - `ForgeClientRequestSender`
- `ProximityCraftingScreen`, JEI, and EMI no longer call Forge networking directly for client-to-server requests.
- `common` now owns the non-visual client session controller:
  - `ClientRecipeSessionState`
  - `SourceSnapshotApplyResult`
  - `RecipeActionFeedbackApplyResult`
- Forge still owns:
  - packet registration
  - packet transport
  - inbound packet handlers
  - screen/menu side effects and rendering
- The current split keeps transport platform-side, but outbound request intent and client request/apply state are now reusable.

## Current boundary after phase 4
- `common` now also owns the inbound client apply seam for the current S2C payloads:
  - `ClientResponseDispatcher`
  - `ClientRuntimeHooks`
  - `ActiveClientSessionHandle`
- Forge provides the active runtime bridge through:
  - `ForgeClientRuntimeHooks`
  - `ForgeActiveClientSessionHandle`
- The current S2C handlers no longer mutate `ProximityCraftingScreen` directly.
- `ProximityCraftingScreen` is now consumed through a Forge runtime handle instead of being the packet apply target.
- Fabric now registers an early client runtime hook so the same dispatcher contract exists there, even though gameplay parity is still not implemented.

## Next migration targets
1. Decide whether to keep inbound transport fully platform-side or introduce a wider common response/event layer beyond the current two S2C payloads
2. Keep JEI/EMI isolated in Forge until the inbound dispatcher/runtime-hook split is validated in real gameplay
3. Revisit what part of recipe-book snapshot application can move further out of Forge screens/menus
4. Evaluate `CraftConsumeService` call sites and decide whether the facade should remain or be inlined
