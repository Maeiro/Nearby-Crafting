# Forge Port Status

## Architecture diagrams
See the current PlantUML baseline in [docs/architecture/README.md](architecture/README.md).

## Scope of the current branch
This `version/1.20` line keeps Forge as the reference runtime.

The target for Forge 1.20 is:
- stable runtime
- stable vanilla recipe book path
- JEI integration
- EMI integration

## Current branch objective
- retarget the current shared-core architecture from `1.20.1 / Forge 47` to `1.20 / Forge 46`
- recover Forge boot first
- recover vanilla runtime next
- recover JEI and EMI after vanilla stability is confirmed

## Platform policy for this branch
- Forge is the feature reference runtime
- Fabric is active on this branch, but starts as vanilla-only
- NeoForge is out of scope for `version/1.20`
