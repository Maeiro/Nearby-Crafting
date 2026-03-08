# ADR 0001 - Architectury modularization baseline

## Status
Accepted

## Context
The project had grown into a Forge-shaped codebase where core crafting logic, scanning, menus, networking, config, and JEI/EMI integrations were tightly mixed. Future Fabric and NeoForge support would be expensive without an early structural split.

## Decision
- Adopt an Architectury-style module layout:
  - `common`
  - `forge`
  - `fabric`
  - `neoforge`
- Keep Forge as the reference implementation and only release target in the first migration phase.
- Use plain Java interfaces for platform bridges instead of `ExpectPlatform`.
- Keep Architectury API usage minimal in `common`.
- Extract only the first neutral slice now:
  - source references/slot abstractions
  - extraction planning/result types
  - shared mod constants/bootstrap
- In phase 2, introduce `CraftingSessionPort` in `common` to isolate recipe fill/add/remove/refill logic from `ProximityCraftingMenu`.
- Keep `ProximityCraftingMenu` out of the common API surface by wrapping it in a Forge-only adapter (`ForgeCraftingSessionAdapter`) instead of making the menu implement the port directly.
- Keep capability scanning, config-backed scan defaults, and loader-specific source discovery in Forge for now. `common` receives only the scan contract (`ScanOptions`, `SourceCollector`).
- For networking, move shared payload/state records into `common` first, but keep channel registration, packet transport, and screen/menu side effects in Forge.
- Extract `CraftConsumeOperations` behind `CraftConsumeSessionPort` rather than keeping shift-craft behavior tied directly to `ProximityCraftingMenu`.
- In phase 3, introduce an outbound-only client request bridge in `common` (`ClientRequestSender`, `ProximityClientServices`) instead of abstracting the full transport stack immediately.
- Keep `ProximityCraftingScreen` as a Forge-side UI class, but move request/apply bookkeeping into a common controller (`ClientRecipeSessionState`) so future platforms can reuse the session state machine without reusing Forge screens.
- In phase 4, introduce a common inbound response dispatcher (`ClientResponseDispatcher`) plus runtime hook contracts (`ClientRuntimeHooks`, `ActiveClientSessionHandle`) instead of letting Forge packet handlers mutate the screen directly.
- Keep packet transport and packet registration platform-side even after the inbound dispatcher is introduced.
- In phase 5, move source discovery orchestration and recipe-book source aggregation into `common`, while keeping only raw loader-specific discovery adapters in Forge.
- Keep capability access, world/block-entity enumeration, and mod-specific source discovery per loader, but make composition/priority/aggregation reusable from `common`.
- In phase 6, move menu-adjacent tracked-grid state and recipe-book snapshot state into `common`, while keeping the concrete menu/container and result recomputation hook in Forge.
- Keep `ProximityCraftingMenu` as a runtime host and slot adapter rather than the owner of all crafting-grid/session bookkeeping.
- In phase 7, move the Ingredients Panel presenter/cache/diffing logic into `common`, while keeping the Forge screen responsible only for rendering, hover/tooltips, and other UI-side reactions.
- In phase 8, move shared content/bootstrap ids and namespace helpers into `common`, while keeping real registration and bootstrap binding per loader.
- In phase 9, move config defaults and normalization semantics into `common` through shared config records, while keeping `ForgeConfigSpec` binding and blacklist registry resolution platform-side.
- In phase 9, move preferred recipe selection and result recomputation helpers into `common`, while keeping the concrete menu/container host and slot wiring in Forge through a small runtime port.
- In phase 10, move status/feedback presentation state into `common`, while keeping status rendering and UI wrappers inside the Forge screen.
- In phase 10, move recipe-by-id and adjust-load orchestration into `common`, while keeping concrete menu invalidation/runtime host concerns in Forge.
- In phase 11, complete the nearby-container scanning seam by routing Forge through the existing common `NearbyContainerSourceCollector`, leaving Forge with only a raw `ContainerDiscoveryPort` adapter.
- In phase 11, route result-slot refill policy through `common` (`ResultTakePort`, `ResultTakeOperations`) and keep only concrete slot hosting plus packet transport in Forge.
- In the first Fabric runtime slice, move Fabric from scaffold-only to a real runtime host by adding:
  - content/menu/screen registration
  - Architectury `NetworkChannel` transport
  - Fabric runtime adapters for scanning/session/client hooks
  - while intentionally keeping config persistence and overlay compat incomplete for a later step.

## Consequences
- Future ports can reuse common planning/source logic directly.
- Future ports can reuse the full recipe fill workflow by implementing `CraftingSessionPort` and `SourceCollector`.
- Future ports can reuse the current snapshot/feedback payload model without adopting Forge packet classes.
- Future ports can reuse outbound client request intent and non-visual request lifecycle bookkeeping without adopting Forge networking APIs.
- Future ports can reuse the current inbound response/apply seam without reusing Forge packet handlers or screens.
- Future ports can reuse the source composition and recipe-book supplemental source aggregation flow without reusing Forge scanning code.
- Future ports can reuse tracked crafting-grid source-ledger behavior and recipe-book source session state without reusing the Forge menu implementation.
- Future ports can reuse the current ingredients-panel presenter and cache logic without reusing the Forge screen implementation.
- Future ports can bind their own registries/bootstrap flow to the same shared content ids and bootstrap metadata without copying Forge-local string ids.
- Forge-specific runtime behavior stays intact while the structure evolves.
- The first migration phase does not yet make NeoForge feature-complete.
- JEI/EMI remain Forge-side until the platform-neutral core is stable.
- The adapter indirection keeps menu internals free to evolve without leaking Forge container APIs into `common`.
- Packet transport and registration are still platform-side, but inbound client apply flow is no longer hardcoded to Forge screen mutation.
- World/inventory discovery remains platform-side by design; only the orchestration of discovered sources has been generalized.
- Result-slot recomputation and concrete menu/container callbacks remain Forge-side by design; only the menu-adjacent session bookkeeping has been generalized.
- Screen rendering remains platform-side by design; only the non-visual ingredients-panel presenter slice has been generalized.
- Registry/bootstrap binding remains platform-side by design; only the shared ids and descriptor metadata have been generalized.
- Config file binding remains platform-side by design; only defaults and normalized runtime/config records have been generalized.
- Concrete container/result-slot wiring remains platform-side by design; only preferred recipe lookup and result recomputation helpers have been generalized.
- Status rendering remains platform-side by design; only the status message state and feedback mapping have been generalized.
- Concrete menu invalidation/runtime hosting remains platform-side by design; only recipe request/load orchestration has been generalized.
- Nearby container world iteration/filtering is no longer Forge-owned; only raw block-entity/item-handler discovery remains platform-side by design.
- Concrete result slot hosting and packet transport remain platform-side by design; only result-take refill policy has been generalized.
- Fabric is now beyond scaffold status: it has a build-valid runtime host on top of the shared core, but it is not yet declared feature-parity complete.
