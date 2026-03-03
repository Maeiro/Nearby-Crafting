# Nearby Crafting

Forge 1.20.1 mod that adds a **Nearby Crafting Table**.

## Features
- Craft using items from nearby containers + player inventory
- 3x3 crafting table UI with Recipe Book support
- Nearby container scanning with configurable radius, blacklist, and minimum slots
- Optional JEI and EMI recipe-fill integration

## Known Bugs
- Vanilla Recipe Book: `Nearby Items` panel overlaps the recipe book panel because both occupy the same screen region.
- Vanilla Recipe Book: scroll-based recipe increment does not start until at least one recipe unit is already loaded.
- JEI Craftable Only: ingredient list can be unstable in some situations, occasionally showing non-craftable entries and some liquid-type entries.

## Requirements
- Minecraft 1.20.1
- Forge 47.x
- Java 17

## Build
```powershell
.\gradlew build -x test
```

Output jar:
- `build/libs/nearbycrafting-1.0.0+forge-1.20.1.jar`
