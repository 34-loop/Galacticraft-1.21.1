# Galacticraft (Boobcat Edition) on NeoForge 1.21.1

This fork ports Galacticraft 5 ("Boobcat Edition") to NeoForge 1.21.1 and adds Galacticraft 4
content that GC5 lacks. Work on `neoforge-ci`. When the **NeoForge Build** workflow is green,
fast-forward `main` to it (`git push origin neoforge-ci:main`).

## Layout

- All mod code lives in `fabric/src/main/java`. The `neoforge` module compiles that same tree
  (`srcDir`) minus the excludes in `neoforge/build.gradle.kts`. NeoForge-only code is in
  `neoforge/src/main/java/dev/galacticraft/neoforge`.
- Client registration happens twice. `GalacticraftClient.java` is Fabric-only (excluded from
  NeoForge), so register screens, block entity renderers and item renderers in **both**:
  - `fabric/.../mod/GalacticraftClient.java`
  - `neoforge/.../neoforge/GCNeoForgeClient.java` (item renderers also go in
    `neoforge/.../client/GCNeoItemRenderer.java` and the `registerClientExtensions` item list)
- Generated resources are in `fabric/src/main/generated`. Never edit them by hand; change the
  providers in `fabric/.../mod/data/` and rerun datagen.
- GC4 reference source: TeamGalacticraft/Galacticraft-Legacy, branch `master-1.12` (4.0.7-dev).
  Clone it to a scratch directory when porting content. Planets content is under
  `micdoodle8/mods/galacticraft/planets/{mars,asteroids,venus}`, assets under
  `assets/galacticraftplanets`.

## Environment setup (cloud sessions)

1. **Maven Central returns 429.** Create `~/.gradle/init.d/central-mirror.gradle`:
   ```groovy
   def MIRROR = 'https://maven-central.storage-download.googleapis.com/maven2/'
   def fix = { RepositoryHandler repos ->
       repos.withType(MavenArtifactRepository).configureEach { r ->
           def u = r.url.toString()
           if (u.startsWith('https://repo.maven.apache.org/maven2') || u.startsWith('https://repo1.maven.org/maven2')) r.url = MIRROR
       }
   }
   beforeSettings { s -> fix(s.pluginManagement.repositories); fix(s.buildscript.repositories) }
   settingsEvaluated { s -> fix(s.dependencyResolutionManagement.repositories) }
   allprojects { fix(buildscript.repositories); fix(repositories) }
   ```
2. **repo.terradevelopment.net is unreachable.** Build MachineLib and DynamicDimensions locally
   as siblings of this repo. `fabric/` and `neoforge/build.gradle.kts` pick up jars from
   `../MachineLib/{fabric,neoforge}/build/libs` and `../DynamicDimensions/...` automatically:
   ```sh
   git clone -b feat/architectury-multiloader https://github.com/34-loop/MachineLib ../MachineLib
   git clone -b fix/neoforge-runtime-dimension-lifecycle https://github.com/34-loop/DynamicDimensions ../DynamicDimensions
   (cd ../MachineLib && ./gradlew :fabric:build :neoforge:build -x test)
   (cd ../DynamicDimensions && ./gradlew :fabric:build :neoforge:build -x test)
   ```
   The refs must match `MACHINELIB_REF` and `DYNAMICDIMENSIONS_REF` in
   `.github/workflows/neoforge.yml`.
3. After the first successful build, add `--offline` to Gradle commands. It is much faster.
4. Run builds one at a time. Parallel Gradle runs on this repo fight over locks and memory.

## Commands

| What | Command |
|---|---|
| Datagen (after any provider change) | `./gradlew :fabric:runDatagen` |
| Build and checks, same as CI | `./gradlew :neoforge:build spotlessCheck pmdMain :fabric:assemble -x test` |
| Server smoke test | `.github/scripts/neoforge-server-smoke.sh` |
| Client end-to-end test (~15 min) | `E2E_OUT_DIR=<dir> .github/scripts/neoforge-client-e2e.sh` |

**Datagen drifts up to three unrelated files. Revert whichever changed before committing:**

```sh
git checkout -- fabric/src/main/generated/assets/galacticraft/lang/zh_cn.json \
  fabric/src/main/generated/data/galacticraft/tags/block/sensor_glasses_detectable.json \
  fabric/src/main/generated/data/galacticraft/tags/item/sensor_glasses.json
```

When you change a block item's model from generated to hand-written (for example to
`builtin/entity`), delete the old generated file. Otherwise `processResources` fails with a
duplicate entry.

Spotless has no license header step. New files copy the MIT header from a neighbouring file.

## Adding a machine (checklist)

Copy the most similar existing machine, for example `GasLiquefierBlockEntity` (generic
`MachineMenu`) or `GeothermalGeneratorBlockEntity` (custom menu with synced fields). Touch all
of these:

- `Constant`: `Block.X`, `Menu.X_MENU`, `ScreenTexture`, NBT keys
- Classes: block in `content/block/machine`, block entity in `content/block/entity/machine`,
  menu in `screen`, screen in `client/gui/screen/ingame`
- Registries: `GCBlocks`, `GCBlockEntityTypes`, `GCMenuTypes` (field and `Registry.register`),
  `GCApiLookupProviders`, `GCCreativeModeTabs`, client screens in both loaders (see Layout)
- Statuses: `GCMachineStatuses` with keys in `Translations.MachineStatus`. `MachineStatus.Type`
  has no `IDLE`; use `OTHER` for non-working states.
- Datagen: `GCModelProvider`, `GCBlockLootTableProvider` (`dropSelf`), `GCBlockTagProvider`
  (`MACHINES` gives the pickaxe tag), `GCTranslationProvider` (name, `blockDesc`, statuses, UI
  strings), `GCMachineRecipes` (port the GC4 recipe)
- C2S packets: a record implementing `C2SPayload` in `network/c2s`, registered in `GCPackets`
  with `registerC2S`. In `handle`, check `player.containerMenu instanceof XMenu` and
  `machine.getSecurity().hasAccess(player)`.
- Add an e2e check in `neoforge-client-e2e.sh` (see below).

Multiblocks with OBJ models: follow `ShortRangeTelepadBlock` with `ShortRangeTelepadPartBlock`,
or `AstroMinerBaseBlock`. OBJ models go in `assets/galacticraft/models/misc/<name>.{obj,mtl,json}`.
The `.mtl` points to `galacticraft:obj/<texture>` (`textures/obj/`). GC4 `o` lines must become
`g` to select groups with `GCModelState`. Render through `GCRenderTypes.obj(OBJ_ATLAS)`.

## MachineLib facts

- Fluid amounts are **droplets on both loaders: 81000 per bucket**. One millibucket is
  `FluidUtil.bucketsToDroplets(1) / 1000`.
- Block entity NBT: `EnergyStorage:<long>L`,
  `FluidStorage:[{Resource:"<fluid id>",Amount:<droplets>L},{},...]`,
  `ItemStorage:[{Resource:"<item id>",Amount:<int>},{},...]`. Use one entry per slot or tank,
  `{}` for empty, in spec order.
- There is no `ResourceFilter.or`. Use `ResourceFilters.or(a, b)`.
- Default energy capacity is `Galacticraft.CONFIG.machineEnergyStorageSize()` (30000).
- Gases are fluids: `Gases.OXYGEN`, `HYDROGEN`, `METHANE`, `CARBON_DIOXIDE`. Liquids include
  `GCFluids.FUEL`, `LIQUID_OXYGEN` and `SULFURIC_ACID`. A world's atmosphere is
  `level.galacticraft$getCelestialBody().value().atmosphere().composition()`.

## Client end-to-end test

`neoforge-client-e2e.sh` starts a dedicated server with RCON (port 25575, password `gctest`)
and a client on Xvfb that joins it. It drives the game through `.github/scripts/rcon.py` and
`xdotool`, writes screenshots to `$E2E_OUT_DIR`, and fails on crashes, `/ERROR]` log lines or
failed checks.

- Machines are placed around x=3 in the overworld, and telepads at x=-5. Preload them with
  `data merge block` using the NBT formats above. Read values back with the `block_value` helper
  and check them after the Moon trip, which gives machines time to run.
- Inside bash, use double-quoted strings for NBT with `\"`. Single quotes keep the backslashes.
- `xdotool` must use XTEST events: no `--window` flag. GLFW ignores synthetic window events.
  Click with `xdotool click 3` while the player faces the block (set yaw and pitch with `tp`).
- Look at the screenshots: the test passing does not prove a GUI or model renders correctly.
- Known noise that is filtered out: SoundEngine, OpenAL and Narrator errors on the client.
- To read a long or noisy log, grep it; don't dump it.

## Conventions

- Commit messages: imperative subject ("Add the X from Galacticraft 4"), then a body saying
  what was ported, deviations from GC4, and what the e2e test now checks.
- Port GC4 numbers exactly (energy, timings, ranges, recipes) and say so in the class Javadoc.
  Note any deviation in the commit message.
- Never push to `main` before CI is green on `neoforge-ci`.
