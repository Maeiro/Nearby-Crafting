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
- `fabric`: first runtime host slice implemented and validated by `:fabric:build`
- `neoforge`: real runtime host implemented and validated by `:neoforge:build`

Detailed Fabric status:
- `docs/fabric-port-status.md`
Detailed NeoForge status:
- `docs/neoforge-port-status.md`

## NeoForge implementation note
NeoForge 1.20.1 is now wired as a real platform module in this branch, but it uses a Forge-shaped Loom/tooling path (`loom.platform=forge` with `net.neoforged:forge`) because the direct NeoForge Loom path was not stable in this setup.

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
  - `ContainerDiscoveryPort`
  - `DiscoveredContainer`
  - `NearbyContainerSourceCollector`
  - `PlayerInventorySourceCollector`
  - `PlayerBackpackSourceCollector`
  - `SourceCollectionResult`
  - `CompositeSourceCollector`
  - `RecipeBookSourceAggregator`
- Forge now keeps only the raw discovery adapters:
  - `ForgeContainerDiscoveryPort`
  - `ForgePlayerInventorySourceCollector`
  - `ForgeBackpackSourceCollector`
- `ProximityInventoryScanner` is no longer the owner of the full source composition flow. It is now a thin Forge-side boundary over the common orchestration layer.
- Recipe book source snapshot aggregation no longer lives in Forge networking code. The grouping/aggregation logic now lives in `common` through `RecipeBookSourceAggregator`.
- The practical split is now:
  - `common`: compose, merge, prioritize, aggregate source refs, and own nearby-container scan geometry/filtering
  - `forge`: discover concrete inventories/capabilities/backpacks and adapt them into raw discovered containers/source refs

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

## Current boundary after phase 8
- `common` now also owns shared content/bootstrap descriptors and namespace helpers:
  - `ProximityCrafting.id(...)`
  - `ProximityId`
  - `ProximityContentDescriptors`
  - `ProximityBootstrapDescriptors`
- Forge registries/bootstrap now bind to those shared descriptors instead of hardcoded local string ids for:
  - content registration paths
  - network channel id
  - JEI plugin uid
- The practical split is now:
  - `common`: shared ids, namespace helpers, and bootstrap metadata descriptors
  - `forge`: actual `DeferredRegister` usage, packet channel transport, and loader bootstrap wiring

## Current boundary after phase 9
- `common` now owns config semantics and default resolution through shared config models:
  - `ProximityConfigDefaults`
  - `ServerRuntimeSettings`
  - `ClientPreferences`
  - `ClientUiState`
- Forge now keeps:
  - `ForgeConfigSpec` binding
  - block entity blacklist parsing/resolution
  - application of shared config records to the current runtime
- `common` now also owns preferred recipe selection and result recomputation helpers:
  - `CraftingResultPort`
  - `CraftingResultOperations`
- `ProximityCraftingMenu` no longer owns recipe lookup/result recomputation directly. It now hosts a small runtime adapter and delegates active-recipe/result decisions into `common`.
- The practical split is now:
  - `common`: config defaults/normalization, preferred recipe lookup, result recomputation
  - `forge`: config file binding, block entity registry resolution, concrete menu/container host

## Current boundary after phase 10
- `common` now also owns screen-side status/feedback presentation state:
  - `StatusMessagePresenter`
  - `StatusMessageView`
- `ProximityCraftingScreen` no longer owns:
  - status message expiry bookkeeping
  - status color selection
  - feedback payload to status mapping
- `common` now also owns recipe request/session operations:
  - `RecipeSessionOperations`
- `ProximityCraftingMenu` no longer owns:
  - recipe-by-id resolution flow
  - scroll adjust/load orchestration
- The practical split is now:
  - `common`: status message state, feedback mapping, recipe request/load operations
  - `forge`: render the current status message, invalidate snapshot cache around delegated recipe operations, host the concrete menu/screen runtime

## Current boundary after phase 11
- `common` now actively owns the result-take refill decision path:
  - `ResultTakePort`
  - `ResultTakeOperations`
  - `ResultTakeOutcome`
- `ProximityResultSlot` no longer owns:
  - refill decision policy
  - direct snapshot-send policy
- `ProximityCraftingMenu` now hosts that runtime seam through:
  - `handleResultSlotTake(...)`
  - `sendRecipeBookSourceSnapshot(...)`
- The practical split is now:
  - `common`: result-take refill policy and outcome model
  - `forge`: concrete result slot host, server packet transport, and menu-side runtime callback

## Current Fabric slice
- Fabric is no longer "compile-only scaffold".
- Fabric now has:
  - block/item/menu registration
  - Architectury menu open flow
  - Architectury network channel registration
  - C2S/S2C packet implementations wired to the shared common request/response/session seams
  - Fabric runtime adapters for scanning, crafting session, consume session, and active client session hooks
- Fabric 1.20.1 now has a validated vanilla recipe book path, including direct hover scroll over recipe book items.
- What is still intentionally missing from Fabric is documented separately in:
  - `docs/fabric-port-status.md`
- The important architectural point is:
  - Fabric now has a concrete runtime host
  - the remaining parity work is now primarily platform-specific feature completion, especially JEI/EMI integration, not foundational restructuring
  - Fabric now also has lightweight file-backed config generation for client/server config files

## Current NeoForge slice
- NeoForge is no longer scaffold-only.
- NeoForge now has:
  - block/item/menu registration
  - screen registration
  - creative tab insertion
  - packet channel registration and packet handlers
  - NeoForge-side runtime adapters over the current common core
  - vanilla recipe book hover-resolution wiring through NeoForge mixins/accessors
- The NeoForge module now builds successfully through:
  - `:neoforge:compileJava`
  - `:neoforge:build`
- The current NeoForge milestone should be read as:
  - build-valid
  - validated stable for the vanilla recipe book path on 1.20.1
  - intentionally capped at vanilla recipe book scope on this version line
- Remaining NeoForge gaps for full parity are intentionally deferred:
  - no JEI integration yet
  - no EMI integration yet
  - no backpack compat yet
  - no plan to expand further on NeoForge 1.20.1 unless a vanilla-book bug requires it

## Next migration targets
1. Implement EMI integration on Fabric
2. Implement JEI integration on Fabric if it remains part of the target loader feature set
3. Treat further NeoForge feature expansion as a later-version concern, not a 1.20.1 goal
4. Review whether the lightweight file-backed config layer should later be replaced by loader-native config frameworks
