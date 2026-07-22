Kingdoms is a server-authoritative NeoForge 1.21.1 faction mod tailored to the KAL modpack.

Developer references:

- [Network protocols](docs/network-protocols.md)
- Server configuration is generated in `world/serverconfig/kingdoms-server.toml`.
- Trader catalogs are data-pack resources under `data/kingdoms/trader_catalogs`.

Verification:

```text
gradlew test build runGameTestServer --no-daemon
```
