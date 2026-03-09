# Fabric Port Status

## Architecture diagrams
See the current PlantUML baseline in [docs/architecture/README.md](architecture/README.md).

## Scope of the current branch
This `version/1.20` line keeps Fabric active, but not as the reference runtime.

The initial Fabric target for this branch is:
- stable runtime
- stable vanilla recipe book path
- no JEI/EMI requirement in the first pass

## What is implemented

- The branch is reusing the proven `1.20.1` Fabric architecture:
  - real runtime host
  - real packet transport
  - real menu/screen host path
  - real runtime adapters over the current `common` core
  - platform-local recipe book runtime bridge

## Current branch objective
- retarget the current Fabric runtime from `1.20.1` to `1.20`
- keep Architectury in place
- preserve the shared-core architecture from `version/1.20.1`
- recover the stable vanilla recipe book path on the new version line

## What is intentionally minimal or incomplete

### Mod compat
- Fabric JEI/EMI integration is not part of the initial `1.20` branch scope.
- Current Fabric compat classes for JEI/EMI remain stubs/no-op placeholders so the module can compile cleanly.
- Sophisticated Backpacks compat is not implemented on Fabric.
- The Fabric backpack source collector currently returns no backpack sources.

## Success criteria for this branch
- the Fabric `1.20` module loads
- the table item appears
- table/menu open flow works
- vanilla recipe book opens and fills recipes
- hover scroll over vanilla recipe book items works
- clear grid / result take / source sync behave correctly

## Current recommended interpretation
- Forge remains the reference runtime for `version/1.20`.
- Fabric is an active secondary runtime on this branch.
- Fabric should be treated as a vanilla-first target until `1.20` boot and vanilla stability are confirmed.
