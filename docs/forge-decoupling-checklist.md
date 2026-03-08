# Forge Decoupling Checklist

- [x] Extract planning/source primitives to `common`
- [x] Extract recipe fill core to `common`
- [x] Extract shift-craft consume core to `common`
- [x] Add outbound client request bridge in `common`
- [x] Add inbound client response dispatcher and runtime hooks in `common`
- [x] Extract source discovery orchestration to `common`
- [x] Reduce `ProximityCraftingMenu` ownership to session hosting and slot adapters
- [x] Extract more screen-side presenters/view-models out of `ProximityCraftingScreen`
- [ ] Move config semantics/default resolution into `common`
- [ ] Review registry/bootstrap descriptors for further loader isolation

## Completed in this phase
- Source discovery split:
  - `common` owns source orchestration and recipe-book source aggregation
  - `forge` keeps raw container/player/backpack discovery adapters
- Menu session split:
  - `common` owns tracked craft-grid mutation/source-ledger state
  - `common` owns recipe-book source snapshot/client supplemental source session state
  - `forge` menu now hosts these session objects and exposes slot/runtime adapters
- Screen presenter split:
  - `common` owns the Ingredients Panel presenter/cache/diffing logic
  - `forge` screen keeps render, tooltips, and UI-side hooks around that presenter

## Next focus candidates
- Move config semantics/default resolution into `common`
- Review whether the remaining recipe-resolution/output update logic can be split from `ProximityCraftingMenu`
- Review whether more action/status/panel perf view models can leave `ProximityCraftingScreen`
