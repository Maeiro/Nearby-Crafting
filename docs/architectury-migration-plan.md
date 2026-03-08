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

## Next migration targets
1. Decide whether the next networking step should be:
   - common packet send intent interfaces, or
   - keeping transport fully platform-side and only sharing payloads
2. Keep JEI/EMI isolated in Forge until the service split is stable
3. Revisit what part of recipe-book snapshot application can move out of Forge screens/menus
4. Evaluate `CraftConsumeService` call sites and decide whether the façade should remain or be inlined
