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

## Current boundary after phase 5
- `common` now owns the source discovery orchestration layer:
  - `ContainerSourceCollector`
  - `PlayerInventorySourceCollector`
  - `PlayerBackpackSourceCollector`
  - `SourceCollectionResult`
  - `CompositeSourceCollector`
  - `RecipeBookSourceAggregator`
- Forge now keeps only the raw discovery adapters:
  - `ForgeContainerSourceCollector`
  - `ForgePlayerInventorySourceCollector`
  - `ForgeBackpackSourceCollector`
- `ProximityInventoryScanner` is no longer the owner of the full source composition flow. It is now a thin Forge-side boundary over the common orchestration layer.
- Recipe book source snapshot aggregation no longer lives in Forge networking code. The grouping/aggregation logic now lives in `common` through `RecipeBookSourceAggregator`.
- The practical split is now:
  - `common`: compose, merge, prioritize, and aggregate source refs
  - `forge`: discover concrete inventories/capabilities/backpacks and adapt them into source refs

## Current boundary after phase 6
- `common` now also owns menu-adjacent session state that was previously embedded directly in `ProximityCraftingMenu`:
  - `TrackedCraftGridPort`
  - `TrackedCraftGridSession`
  - `RecipeBookSourceSessionState`
- `TrackedCraftGridSession` now owns:
  - craft-grid bulk mutation suppression and pending flush behavior
  - source-ledger tracking for grid slots
  - tracked return/remove/add/set operations on the crafting grid
- `RecipeBookSourceSessionState` now owns:
  - client supplemental recipe-book source state
  - server recipe-book snapshot cache state
  - prewarm/adjust-throttle bookkeeping
- `ProximityCraftingMenu` is now closer to a session host:
  - it owns the concrete `CraftingContainer`, result slot, and runtime callbacks
  - it delegates tracked-grid state and recipe-book session state into reusable common components
- The practical split is now:
  - `common`: menu-adjacent session state and slot mutation bookkeeping
  - `forge`: concrete menu/container implementation and result recomputation hook

## Current boundary after phase 7
- `common` now also owns the first screen-side presenter/view-model slice:
  - `IngredientsPanelContext`
  - `IngredientsPanelEntry`
  - `IngredientsPanelPresenter`
  - `IngredientsPanelUpdateResult`
- `IngredientsPanelPresenter` now owns:
  - ingredients panel cache dirtiness and reuse
  - craft-grid signature diffing
  - source aggregation for recipe ingredients
  - non-visual panel data building
- `ProximityCraftingScreen` now consumes that presenter through a small Forge-side context adapter instead of owning the full ingredients-panel cache and aggregation flow directly.
- The practical split is now:
  - `common`: ingredients panel state/presenter logic
  - `forge`: screen rendering, hover/tooltips, and UI-side reactions around the presenter

## Next migration targets
1. Move config semantics/default resolution into `common`, leaving only platform config binding per loader
2. Review whether the remaining recipe-resolution/result-update logic can be separated from `ProximityCraftingMenu`
3. Review whether additional action/status/panel perf view models should leave `ProximityCraftingScreen`
4. Review registry/bootstrap descriptors for additional loader isolation without over-abstracting platform registration
