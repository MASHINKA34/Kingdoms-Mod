# Third-party jars

These are other people's mods. They are not redistributable, so `libs/*.jar` is gitignored and each
file has to be downloaded locally. Versions come from `gradle.properties`.

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

`hookahmod-${hookah_version}.jar` only feeds the Curios hookah integration in `runClient`. Everything
builds and every gametest passes without it.

## Moving these to Maven

Three of them are published to public Maven repositories, which would take them out of `libs`
entirely. The coordinates below were verified to exist but the migration is not applied, because
Gradle could not reach those hosts from the machine this was prepared on.

```groovy
maven { url = 'https://maven.blamejared.com' }
maven { url = 'https://dl.cloudsmith.io/public/geckolib3/geckolib/maven/' }

compileOnly  "mezz.jei:jei-${minecraft_version}-neoforge:${jei_version}"
localRuntime "mezz.jei:jei-${minecraft_version}-neoforge:${jei_version}"
localRuntime "top.theillusivec4.curios:curios-neoforge:${curios_version}"
localRuntime "software.bernie.geckolib:geckolib-neoforge-${minecraft_version}:4.9.2"
```

Scorched Guns and Protection Pixel have no public Maven; reaching them needs `cursemaven.com` and
their CurseForge project and file ids.
