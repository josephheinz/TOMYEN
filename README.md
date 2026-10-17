# TOMYEN: The Only Mod You'll Ever Need

Fabric mod for Minecraft 26.3 (Java 25).

## Building from source

### Requirements

- **JDK 25.** Any distribution works, such as [Temurin](https://adoptium.net/temurin/releases/?version=25), which CI uses.
  Gradle runs on whichever JDK `JAVA_HOME` (or `java` on your `PATH`) points at, so check with `java -version`.
  The code compiles with `--release 25`, so an older JDK will fail.
- **Git.** You don't need to install Gradle: the wrapper (`gradlew`) downloads Gradle 9.7.1 on first use.

### Build

```sh
git clone https://github.com/josephheinz/TOMYEN.git
cd TOMYEN
./gradlew build
```

On Windows, run `gradlew.bat build` in place of `./gradlew build`.

The first build downloads Minecraft, Fabric Loader, Fabric API and the Loom toolchain, so it takes a few minutes. Later builds are much faster.

The output goes to `build/libs/`:

| File | What it is |
|---|---|
| `tomyen-<version>.jar` | **The mod. This is the one to install.** |
| `tomyen-<version>-sources.jar` | Source code only, for IDEs and other developers. Don't put it in `mods/`. |

`<version>` comes from `version` in `gradle.properties` (currently `0.2.0`).

### Install

1. Install **Minecraft 26.3** with **Fabric Loader 0.19.5 or newer**, using the [Fabric installer](https://fabricmc.net/use/installer/).
2. Download **Fabric API** for 26.3 from [Modrinth](https://modrinth.com/mod/fabric-api) or [CurseForge](https://www.curseforge.com/minecraft/mc-mods/fabric-api). TOMYEN needs it, and it's built against `0.162.0+26.3`.
3. Put `tomyen-<version>.jar` and the Fabric API jar in your `mods` folder:
   - Windows: `%APPDATA%\.minecraft\mods`
   - macOS: `~/Library/Application Support/minecraft/mods`
   - Linux: `~/.minecraft/mods`
4. Start the game with the Fabric profile from the launcher.

Players don't need a JDK, because the Minecraft launcher comes with its own Java.

### Run a dev client

```sh
./gradlew runClient
```

This starts Minecraft with TOMYEN and Fabric API loaded, straight from the source, with no install. The game directory is `run/`, which git ignores. Use `./gradlew runServer` for a dedicated server.

## Develop

For hot reload, use JetBrains Runtime 25 (installed at `~/Library/Java/JavaVirtualMachines/jbrsdk-25…`). It's optional, because any JDK 25 builds and runs the mod. JBR can also hot reload added or removed methods, fields and classes, where a normal JDK can only reload method bodies.

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

## CI and releases

Every push and pull request to `master` runs `.github/workflows/build.yml`. It builds the mod and attaches the jars to the run as the `tomyen-jars` artifact.

To cut a release:

1. Bump `version` in `gradle.properties` and add a `# TOMYEN <version>: ...` section to the top of `CHANGELOG.md`. Commit both and push.
2. Tag the commit and push the tag:

   ```sh
   git tag v0.1.0
   git push origin v0.1.0
   ```

`.github/workflows/release.yml` then builds the mod and publishes a GitHub Release with `tomyen-<version>.jar` attached. It uses that version's `CHANGELOG.md` section as the release notes, or GitHub's generated notes if there isn't one. The workflow stops if the tag doesn't match `version` in `gradle.properties`.
