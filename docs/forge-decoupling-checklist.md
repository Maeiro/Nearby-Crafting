# Forge Decoupling Checklist

- [x] Extract planning/source primitives to `common`
- [x] Extract recipe fill core to `common`
- [x] Extract shift-craft consume core to `common`
- [x] Add outbound client request bridge in `common`
- [x] Add inbound client response dispatcher and runtime hooks in `common`
- [x] Extract source discovery orchestration to `common`
- [ ] Reduce `ProximityCraftingMenu` ownership to session hosting and slot adapters
- [ ] Extract more screen-side presenters/view-models out of `ProximityCraftingScreen`
- [ ] Move config semantics/default resolution into `common`
- [ ] Review registry/bootstrap descriptors for further loader isolation

## Completed in this phase
- Source discovery split:
  - `common` owns source orchestration and recipe-book source aggregation
  - `forge` keeps raw container/player/backpack discovery adapters

## Next focus candidates
- Reduce `ProximityCraftingMenu` ownership to session hosting and slot adapters
- Extract more screen-side presenters/view-models out of `ProximityCraftingScreen`
- Move config semantics/default resolution into `common`
