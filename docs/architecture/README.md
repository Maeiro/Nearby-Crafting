# Architecture Diagrams

This directory contains the first PlantUML baseline for the current Proximity Crafting architecture.

These diagrams describe the codebase as it exists now after:
- the Architectury modularization,
- the Fabric 1.20.1 stable vanilla recipe book milestone,
- the NeoForge 1.20.1 stable vanilla recipe book milestone.

Recommended reading order:
1. `01-module-overview.puml`
2. `02-common-core-components.puml`
3. `03-runtime-request-response-flow.puml`
4. `04-platform-hosts-and-adapters.puml`

Diagram purpose:
- `01-module-overview.puml`: module boundaries and ownership rules
- `02-common-core-components.puml`: the reusable `common` subsystems and contracts
- `03-runtime-request-response-flow.puml`: the current request/response runtime flow
- `04-platform-hosts-and-adapters.puml`: how Forge, Fabric, and NeoForge host the shared core, including the platform-local vanilla recipe book seam, shared screen runtime controllers, and shared menu runtime seam

Rendering locally:
- If PlantUML is installed, run:
  - `plantuml docs/architecture/01-module-overview.puml`
  - `plantuml docs/architecture/02-common-core-components.puml`
  - `plantuml docs/architecture/03-runtime-request-response-flow.puml`
  - `plantuml docs/architecture/04-platform-hosts-and-adapters.puml`

This baseline intentionally stores only `.puml` source files in git. Generated image artifacts are not committed in this phase.
