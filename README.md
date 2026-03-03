# Proximity Crafting

Forge 1.20.1 mod that adds a **Proximity Crafting Table**.

## Features
- Craft using items from proximity containers + player inventory
- 3x3 crafting table UI with Recipe Book support
- Proximity container scanning with configurable radius, blacklist, and minimum slots
- Optional JEI and EMI recipe-fill integration

## Known Bugs
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
- `build/libs/proximitycrafting-1.0.0+forge-1.20.1.jar`
