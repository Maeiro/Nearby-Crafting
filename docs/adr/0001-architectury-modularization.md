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

## Consequences
- Future ports can reuse common planning/source logic directly.
- Future ports can reuse the full recipe fill workflow by implementing `CraftingSessionPort` and `SourceCollector`.
- Future ports can reuse the current snapshot/feedback payload model without adopting Forge packet classes.
- Forge-specific runtime behavior stays intact while the structure evolves.
- The first migration phase does not yet make NeoForge feature-complete.
- JEI/EMI remain Forge-side until the platform-neutral core is stable.
- The adapter indirection keeps menu internals free to evolve without leaking Forge container APIs into `common`.
