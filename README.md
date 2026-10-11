# My Mod

Fabric mod for Minecraft 26.3 (Java 25).

## Develop

Requires JetBrains Runtime 25 (installed at `~/Library/Java/JavaVirtualMachines/jbrsdk-25…`).
It enables hot reload of added/removed methods, fields and classes, not just method bodies.

**VS Code** (needs the "Extension Pack for Java"):

1. `./gradlew vscode` once (regenerates `.vscode/launch.json`; rerun after changing `build.gradle`).
2. Run and Debug → **Minecraft Client**.
3. Edit code and save: VS Code hot-swaps it into the running game.

**Terminal only:** `./gradlew runClient` (no hot reload).

What reloads without a restart:

| Change | How |
|---|---|
| Java code, mixins | Save while debugging |
| Textures, models, lang | Save, then `F3+T` in game |
| Recipes, loot tables, tags | Save, then `/reload` |
| New blocks/items, `fabric.mod.json` | Restart |

## Build

`./gradlew build` → `build/libs/mymod-<version>.jar` (drop into a `mods/` folder, or upload to Modrinth later).
Bump `version` in `gradle.properties` per release.
