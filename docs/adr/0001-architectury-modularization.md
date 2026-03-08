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

## Consequences
- Future ports can reuse common planning/source logic directly.
- Future ports can reuse the full recipe fill workflow by implementing `CraftingSessionPort` and `SourceCollector`.
- Future ports can reuse the current snapshot/feedback payload model without adopting Forge packet classes.
- Future ports can reuse outbound client request intent and non-visual request lifecycle bookkeeping without adopting Forge networking APIs.
- Future ports can reuse the current inbound response/apply seam without reusing Forge packet handlers or screens.
- Future ports can reuse the source composition and recipe-book supplemental source aggregation flow without reusing Forge scanning code.
- Forge-specific runtime behavior stays intact while the structure evolves.
- The first migration phase does not yet make NeoForge feature-complete.
- JEI/EMI remain Forge-side until the platform-neutral core is stable.
- The adapter indirection keeps menu internals free to evolve without leaking Forge container APIs into `common`.
- Packet transport and registration are still platform-side, but inbound client apply flow is no longer hardcoded to Forge screen mutation.
- World/inventory discovery remains platform-side by design; only the orchestration of discovered sources has been generalized.
