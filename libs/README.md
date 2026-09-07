# Third-party jars

Other people's mods, needed because the mixins and integrations compile against their classes.
Versions come from `gradle.properties`.

Whether a file is committed here is decided by its licence, not by convenience.

| File | Licence | Committed | Source |
| --- | --- | --- | --- |
| `CreateNumismatics-${numismatics_version}.jar` | LGPL | yes | CurseForge / Modrinth: Create Numismatics |
| `ScorchedGuns-1.5.jar` | GPL-3.0-or-later | yes | CurseForge: Scorched Guns |
| `protection_pixel-${protection_pixel_version}-neoforge-${minecraft_version}.jar` | AFL-3.0 | yes | CurseForge: Protection Pixel |
| `jei-${minecraft_version}-neoforge-${jei_version}.jar` | MIT | yes | CurseForge / Modrinth: Just Enough Items |
| `curios-neoforge-${curios_version}.jar` | LGPL-3.0-or-later | yes | CurseForge / Modrinth: Curios API |
| `geckolib-neoforge-${minecraft_version}-4.9.2.jar` | MIT | yes | CurseForge / Modrinth: GeckoLib |
| `framework-neoforge-${minecraft_version}-0.13.11.jar` | LGPL-2.1 | yes | CurseForge / Modrinth: Framework |
| `xaerominimap-neoforge-${minecraft_version}-${xaero_minimap_version}.jar` | All Rights Reserved | **no** | Maven, see below |
| `xaeroworldmap-neoforge-${minecraft_version}-${xaero_worldmap_version}.jar` | All Rights Reserved | **no** | Maven, see below |
| `xaerolib-neoforge-${minecraft_version}-${xaero_lib_version}.jar` | All Rights Reserved | **no** | Maven, see below |
| `hookahmod-${hookah_version}.jar` | All Rights Reserved | **no** | CurseForge: Hookah Mod |

The four GPL and LGPL jars are redistributed unmodified and their sources are the upstream projects
linked above, which is what those licences ask for.

## Xaero's three jars

They are the only compile dependencies that are not in this repository, so `build.gradle` resolves
each one from a local file when it exists and from Xaero's own Maven otherwise:

```groovy
xaero.minimap:xaerominimap-neoforge-1.21.1:26.3.0
xaero.map:xaeroworldmap-neoforge-1.21.1:1.43.0
xaero.lib:xaerolib-neoforge-1.21.1:1.6.0
```

A clone therefore builds with no manual step. Those Maven jars are byte-identical to the CurseForge
releases except that they do not bundle XaeroLib in `META-INF/jarjar`, which does not matter here
because XaeroLib is declared separately.

If the download hangs with `Read timed out` while `curl` fetches the same URL fine, the JVM is the
problem, not Gradle: `chocolateminecraft.com` is behind Cloudflare, the response headers arrive and
the body then stalls on this kind of network. Drop the release jars into this folder by hand and the
build stops going to the network at all.

## The rest

`compileJava` checks for `CreateNumismatics`, `ScorchedGuns`, `protection_pixel` and `jei` before it
starts and names any that are absent, so a missing file shows up as one line rather than a wall of
"cannot find symbol" inside the mixins. Scorched Guns additionally refuses to load without Curios,
GeckoLib and Framework, so the dev runs and `runGameTestServer` need those three as well.

`hookahmod-${hookah_version}.jar` only feeds the Curios hookah integration in `runClient`. It is
declared as a `fileTree` include, so the build and every gametest are fine without it.

## Not moved to Maven

Create Numismatics publishes to `https://maven.ithundxr.dev/releases` under
`dev.ithundxr.createnumismatics:CreateNumismatics-neoforge-1.21.1`, but only `1.0.16-alpha`,
`1.0.17-beta` and `1.1.0` are there, not the `1.0.19integration` build this project pins.

Scorched Guns 1.5 for 1.21.1 and Protection Pixel have no public Maven at all; reaching them would
mean `cursemaven.com` and their CurseForge project and file ids. Both are redistributable, so they
stay checked in.
