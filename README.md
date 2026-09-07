# Kingdoms

A server-authoritative NeoForge 1.21.1 faction mod built for the KAL modpack. Factions claim chunks,
run a treasury, research a bonus tree, declare wars that roll the terrain back when they end, and
trade through Create Numismatics. Every price, permission, teleport and item grant is decided on the
logical server; client payloads are requests and are re-validated before anything is committed.

## Building

Java 21, Gradle wrapper included. A clone builds as it is: the third-party jars the mixins compile
against are in `libs/` where their licence allows it, and Xaero's three, which it does not, resolve
from Xaero's Maven. See [libs/README.md](libs/README.md). `compileJava` names any missing file
instead of failing inside the mixins.

```text
gradlew build                  compile and run the unit tests
gradlew runGameTestServer      run the in-world gametests and exit
gradlew verifyGameTestServer   the above, failing the build if no suite reported
gradlew runClient              dev client (runClient2 for a second one)
gradlew runServer              dev server
```

CI runs `build verifyGameTestServer` on every push.

## Layout

`com.geydev.kalfactions` splits by subsystem rather than by layer. The pieces worth knowing before
touching anything:

| Package | What lives there |
| --- | --- |
| `faction` | `FactionManager`, the overworld-attached `SavedData` holding factions, members and the claim index |
| `net` | Payload definitions, the registrars, and `FactionServerHooks`, the single validation gate every faction C2S goes through |
| `protection` | Claim, sanctuary and machine protection; `ClaimBoundary` decides whether two positions may interact |
| `war` | `WarManager` plus the copy-on-write `WarChunkSnapshot` that reverts a war's terrain; snapshots live in `kingdoms/wars/` via `WarSnapshotStore`, not in the save file |
| `data` | `SavedDataFormat`, the shared version stamp every persisted manager uses |
| `mixin` | Vanilla and third-party patches; `KingdomsMixinPlugin` skips the ones whose target mod is absent |
| `gametest` | In-world tests, excluded from the published jar |

Server configuration is generated per world in `world/serverconfig/kingdoms-server.toml`. Trader
catalogs are data-pack resources under `data/kingdoms/trader_catalogs`.

## References

- [Network protocols](docs/network-protocols.md) — payload fields, bounds and what the server rechecks
- [Third-party jars](libs/README.md) — where each `libs/` file comes from
