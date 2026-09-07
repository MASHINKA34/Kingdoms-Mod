# Third-party jars

These are other people's mods. They are not redistributable, so `libs/*.jar` is gitignored and each
file has to be downloaded locally. Versions come from `gradle.properties`.

`compileJava` checks the required ones before it starts and names any that are absent, so a missing
download shows up as one line rather than a wall of "cannot find symbol" inside the mixins.

## Needed to compile

Without these the build fails, because mixins and integrations reference their classes.

| File | Source |
| --- | --- |
| `CreateNumismatics-${numismatics_version}.jar` | CurseForge / Modrinth: Create Numismatics |
| `ScorchedGuns-1.5.jar` | CurseForge: Scorched Guns |
| `protection_pixel-${protection_pixel_version}-neoforge-${minecraft_version}.jar` | CurseForge: Protection Pixel |
| `xaerominimap-neoforge-${minecraft_version}-${xaero_minimap_version}.jar` | CurseForge: Xaero's Minimap |
| `xaeroworldmap-neoforge-${minecraft_version}-${xaero_worldmap_version}.jar` | CurseForge: Xaero's World Map |
| `xaerolib-neoforge-${minecraft_version}-${xaero_lib_version}.jar` | CurseForge: XaeroLib |
| `jei-${minecraft_version}-neoforge-${jei_version}.jar` | CurseForge / Modrinth: Just Enough Items |

## Needed for the dev runs and `runGameTestServer`

Scorched Guns refuses to load without these three, so the gametest server needs them too.

| File | Source |
| --- | --- |
| `curios-neoforge-${curios_version}.jar` | CurseForge / Modrinth: Curios API |
| `geckolib-neoforge-${minecraft_version}-4.9.2.jar` | CurseForge / Modrinth: GeckoLib |
| `framework-neoforge-${minecraft_version}-0.13.11.jar` | CurseForge / Modrinth: Framework |

## Optional

`hookahmod-${hookah_version}.jar` only feeds the Curios hookah integration in `runClient`. It is
declared as a `fileTree` include, so the build and every gametest are fine without it.

## Moving these to Maven

Six of the nine are published to public Maven repositories at exactly the versions this project
pins. Each URL below was fetched and returned 200 on 2026-09-07, so the coordinates are confirmed:

```groovy
maven { url = 'https://chocolateminecraft.com/maven' }   // already declared
maven { url = 'https://maven.blamejared.com' }
maven { url = 'https://dl.cloudsmith.io/public/geckolib3/geckolib/maven/' }

compileOnly  "xaero.minimap:xaerominimap-neoforge-${minecraft_version}:${xaero_minimap_version}"
compileOnly  "xaero.map:xaeroworldmap-neoforge-${minecraft_version}:${xaero_worldmap_version}"
compileOnly  "xaero.lib:xaerolib-neoforge-${minecraft_version}:${xaero_lib_version}"
compileOnly  "mezz.jei:jei-${minecraft_version}-neoforge:${jei_version}"
localRuntime "top.theillusivec4.curios:curios-neoforge:${curios_version}"
localRuntime "software.bernie.geckolib:geckolib-neoforge-${minecraft_version}:4.9.2"
```

The migration is still not applied. It was attempted on 2026-09-07 and every one of those artifacts
failed with `Read timed out` from Gradle, including at a 180 second socket timeout, even though
`curl` reaches the same URLs from the same machine. Whoever has a link that can actually pull them
can drop the seven `files("libs/...")` lines those replace and delete the jars.

Create Numismatics publishes to `https://maven.ithundxr.dev/releases` under
`dev.ithundxr.createnumismatics:CreateNumismatics-neoforge-1.21.1`, but only `1.0.16-alpha`,
`1.0.17-beta` and `1.1.0` are there — not the `1.0.19integration` build this project pins, so it
cannot move without changing the dependency version.

Scorched Guns and Protection Pixel have no public Maven; reaching them needs `cursemaven.com` and
their CurseForge project and file ids.
