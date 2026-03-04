# Proximity Crafting

![Proximity Crafting Icon](img/icon400.png)

**Proximity Crafting** is a Forge 1.20.1 mod that adds a custom crafting table which can use ingredients from nearby inventories, so you do not need to manually move everything into your player inventory first.

## What This Mod Does
- Adds the **Proximity Crafting Table** (3x3 crafting).
- Builds a shared ingredient pool from:
  - nearby containers (chests and compatible inventories),
  - player inventory (configurable),
  - compatible backpack sources.
- Fills crafting recipes directly from that pool.
- Keeps a live ingredient-availability panel for the currently selected recipe.

## Showcase
### Vanilla Recipe Book Integration
![Vanilla Recipe Book Showcase](img/prox-vanilla-book.gif)

## Recipe
![Recipe](img/recipe.png)

## Main Features
- Nearby container scanning with configurable:
  - scan radius,
  - minimum inventory size,
  - block entity blacklist.
- Source priority control (`CONTAINERS_FIRST` or `PLAYER_FIRST`).
- Recipe Book support with nearby-aware source snapshots.
- Shift and scroll recipe loading workflow for faster batch prep.
- Optional persistence for UI toggle states.

## Integrations
### JEI (optional)
- Recipe transfer support to the Proximity Crafting Table.
- Craftable-only flow integrated with the mod source system.
- Nearby-aware recipe fill requests and feedback.

### EMI (optional)
- Recipe transfer support to the Proximity Crafting Table.
- Custom craftable-only toggle flow designed to avoid conflicts with EMI's own craftable sidebar behavior.
- Alt-click and scroll-assisted loading behaviors integrated with Nearby/Proximity sources.

### Sophisticated Backpacks (optional)
- If installed, backpack inventories detected from the player context are added as valid ingredient sources.
- Works as an extension of the player source pool and follows the same priority/include-player settings.

## Mouse Shortcuts
When using Proximity Crafting recipe interactions (including overlay integrations), these shortcuts speed up recipe loading:

- `Alt + Left Click`: loads ingredients for **1 recipe unit** directly into the crafting grid.
- `Shift + Left Click`: loads the **maximum possible amount** (up to configured limits and grid capacity).
- `Mouse Wheel` (incremental load):
  - scroll up increases loaded recipe amount step-by-step,
  - scroll down decreases loaded amount and returns ingredients through the tracked source flow.

## Requirements
- Minecraft `1.20.1`
- Forge `47.x`
- Java `17`

## Optional Dependencies
- JEI
- EMI
- Sophisticated Backpacks (+ Sophisticated Core)

## Configuration
The mod generates Forge TOML configs for both server and client.

### Server config (`proximitycrafting-server.toml`)
All options are under `proximityCrafting`:
- `scanRadius` (default: `6`): scan radius in blocks around the Proximity Crafting Table.
- `minSlotCount` (default: `6`): minimum slot count for an inventory to be considered a valid source.
- `blockEntityBlacklist` (default: furnace, blast_furnace, smoker): block entity IDs excluded from source scanning.
- `maxShiftCraftIterations` (default: `64`): max recipe units loaded into the grid during max-transfer operations (shift-style load).
- `debugLogging` (default: `false`): enables debug logs for scanning and recipe fill flow.

### Client config (`proximitycrafting-client.toml`)
All options are under `proximityCrafting`:
- `autoRefillAfterCraft` (default: `true`): automatically refills the grid after taking the crafted output.
- `includePlayerInventory` (default: `true`): includes player inventory as ingredient source.
- `sourcePriority` (default: `CONTAINERS_FIRST`): extraction order (`CONTAINERS_FIRST` or `PLAYER_FIRST`).
- `rememberToggleStates` (default: `true`): remembers panel/toggle UI states between openings.
- `proximityItemsPanelOpen` (default: `true`): last remembered state of the Ingredients panel.
- `proximityItemsPanelOffsetX` (default: `0`): Ingredients panel horizontal offset.
- `proximityItemsPanelOffsetY` (default: `0`): Ingredients panel vertical offset.
- `jeiCraftableOnlyEnabled` (default: `false`): last remembered state of JEI Craftable Only.
- `emiCraftableOnlyEnabled` (default: `false`): last remembered state of EMI Craftable Only.

## License
This project is licensed under **GPL-3.0-only**.

[![ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/U7U3EQ9EW)