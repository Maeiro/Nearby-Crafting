# Architecture Diagrams

This directory contains the first PlantUML baseline for the current Proximity Crafting architecture.

These diagrams describe the codebase as it exists now for the active `version/1.20` branch:
- the Architectury modularization is already established,
- Forge is the reference runtime,
- Fabric is the active secondary runtime,
- NeoForge is not part of this branch scope.

Recommended reading order:
1. `01-module-overview.puml`
2. `02-common-core-components.puml`
3. `03-runtime-request-response-flow.puml`
4. `04-platform-hosts-and-adapters.puml`

Diagram purpose:
- `01-module-overview.puml`: module boundaries and ownership rules
- `02-common-core-components.puml`: the reusable `common` subsystems and contracts, including the shared `SourceScanRuntime` and shared config codec
- `03-runtime-request-response-flow.puml`: the current request/response runtime flow, including the shared server request controller on the C2S side
- `04-platform-hosts-and-adapters.puml`: how Forge and Fabric host the shared core on the active `1.20` branch, including the platform-local vanilla recipe book seam, shared screen runtime controllers, shared menu runtime seam, and shared server request handling

Rendering locally:
- If PlantUML is installed, run:
  - `plantuml docs/architecture/01-module-overview.puml`
  - `plantuml docs/architecture/02-common-core-components.puml`
  - `plantuml docs/architecture/03-runtime-request-response-flow.puml`
  - `plantuml docs/architecture/04-platform-hosts-and-adapters.puml`

This baseline intentionally stores only `.puml` source files in git. Generated image artifacts are not committed in this phase.

When a version branch diverges materially, these diagrams must be updated as part of the branch workflow described in [docs/version-line-playbook.md](../version-line-playbook.md).
