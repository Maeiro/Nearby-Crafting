# Forge Decoupling Checklist

Architecture diagrams: [docs/architecture/README.md](architecture/README.md)

## Completed milestones
- [x] Extract planning/source primitives to `common`
- [x] Extract recipe fill core to `common`
- [x] Extract shift-craft consume core to `common`
- [x] Add outbound client request bridge in `common`
- [x] Add inbound client response dispatcher and runtime hooks in `common`
- [x] Extract source discovery orchestration to `common`
- [x] Reduce `ProximityCraftingMenu` ownership to session hosting and slot adapters
- [x] Extract more screen-side presenters/view-models out of `ProximityCraftingScreen`
- [x] Move config semantics/default resolution into `common`
- [x] Extract recipe-resolution/result-update helper from `ProximityCraftingMenu`
- [x] Extract status/feedback presenter flow from `ProximityCraftingScreen`
- [x] Extract recipe request/session operations from `ProximityCraftingMenu`
- [x] Review registry/bootstrap descriptors for further loader isolation
- [x] Establish a first real Fabric runtime host on top of the shared common core
- [x] Establish a first real NeoForge runtime host on top of the shared common core

## Current interpretation
- The heavy Forge-shaped architecture work is already done.
- `common` now owns the reusable core.
- Forge is the reference runtime host.
- Fabric has moved past scaffold status and is stable for the vanilla recipe book path.
- NeoForge 1.20.1 has also moved past placeholder status, but it is intentionally capped at vanilla recipe book support.

## Open review items
- [ ] Decide whether Fabric/NeoForge should keep the current lightweight file-backed config persistence or move to loader-native config frameworks later.
- [ ] Review whether any additional high-value action/panel performance view-models should leave `ProximityCraftingScreen`.
- [ ] Review whether any remaining menu-side result/session flow still worth extracting from `ProximityCraftingMenu` would materially reduce parity cost.
