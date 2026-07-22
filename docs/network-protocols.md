# Network protocols

All gameplay mutations are validated on the logical server. Client payload fields are requests and never authority for faction identity, archive ownership, prices, inventory contents, roles, claims, or dimension access.

## Faction protocol 8

### `kingdoms:start_research` — C2S

Fields: `tablePos: BlockPos`, `nodeName: UTF-8 string (max 32 characters)`. Sent only from an open faction-table research screen. The server rechecks the loaded table block, interaction distance, faction membership and role, node ID, prerequisites, active research, influence, configured tier cost, and the exact typed physical crystals in the player inventory. Influence and crystals are committed atomically; any failure consumes neither. Success or failure is returned through `kingdoms:faction_state` and a localized `kingdoms:faction_notice`. A completed/active node makes replay harmless.

### `kingdoms:faction_state` — S2C

Existing bounded faction snapshot, extended by six non-negative crystal tier costs. It is sent after opening the table and after every accepted or rejected research mutation. Clients use the costs for display only.

## Trader protocol 8

Every mutable request references a server-issued session UUID. The server checks the exact next sequence before a buy or sale and advances it once, so replay and concurrent duplicate requests cannot commit twice.

| Direction | ID | Fields and bounds | Server conditions and result |
| --- | --- | --- | --- |
| C2S | `kingdoms:trader_buy` | `traderId: UUID`, `sessionId: UUID`, `sequence: VarLong`, `offerId: UTF-8 <=64` | Rechecks live entity, role/event, session owner, expiry, distance <=8 blocks, faction/claim access, catalog offer, rolled server price, stock, daily limit, funds, and result capacity. Returns a fresh shop state; invalid/replayed requests have no effect. |
| C2S | `kingdoms:trader_sell` | Above plus `amount: VarInt`, clamped by service to 1..4096 batches | Rechecks the current server catalog and batch size, exact matching items, daily batch limit, sequence, and post-removal payout capacity. Item removal and strict in-inventory Numismatics payout are one transaction; failure rolls removal back and never drops currency into the world. |
| C2S | `kingdoms:trader_seller_refresh` | `traderId: UUID`, `sessionId: UUID` | Rechecks session/entity/distance and returns the server rotation; it cannot request arbitrary offers or prices. |
| C2S | `kingdoms:trader_close` | `traderId: UUID`, `sessionId: UUID` | Closes only the requesting player's matching session. |
| S2C | `kingdoms:trader_shop_state` | UUIDs, `acknowledgedSequence: VarLong`, title key <=128, buy offers <=8, sell offers <=18, trusted localized notice, success flag, refresh epoch | Full authoritative replacement state. Each offer has ID <=64, item ID <=128, batch count 1..64, price, remaining batch limit, and permanence flag. Internal overflow is rejected instead of truncated. |
| S2C | `kingdoms:seller_catalog` | sellers <=32; each has UUID, index, offers <=18, refresh epoch | Sent only from the catalog interaction and built from loaded server entities/data. |

Terminal trader errors are localized shop states: invalid/expired session, unavailable offer, access denied, insufficient funds/items, daily limit, inventory full, stale sequence, or unavailable trader.

## Dimension protocol 2

### `kingdoms:dimension_action` — C2S

Fields: `end: boolean`, `action: VarInt enum 0..3` (`open`, `close`, `schedule_wipe`, `cancel_wipe`). It is emitted only by the dimension-control screen. The server requires permission level 2, rejects unknown actions in the decoder, derives the dimension from the boolean, performs the action in `DimensionControlManager`, and returns `kingdoms:dimension_state`.

### `kingdoms:dimension_state` — S2C

Fields: Nether closed/wipe flags, player count, Moscow schedule-open flag, seconds until close, active session count, registered-portal flag; End closed/wipe flags and player count; trusted localized notice and success flag. Counts/times are server-derived.

Nether entry and return use no permissive C2S action. Server travel, portal, login, faction-membership, death, dimension-change, tick, and item-use hooks enforce the registered portal, Moscow window, two sessions, landing, death lock, and evacuation. The return sigil carries persistent player/session/nonce components and is consumed atomically after a server-side channel.

## Xaero archive protocol 1

The channel accepts only Xaero World Map `1.43.0` and Minimap `26.3.0` compatible archives. The server derives server identity, faction UUID, dimension/wipe scope, and all storage paths. SHA-256 strings are exactly 64 hexadecimal characters. Region names match `-?digits_-?digits.zip`, maximum 48 characters.

| Direction | ID | Fields and maximum sizes | Conditions, responses, and errors |
| --- | --- | --- | --- |
| C2S | `kingdoms:xaero_archive_upload_begin` | `sessionId: UUID`, `anchor: BlockPos`, `dimension: ResourceLocation`, `xaeroVersion: UTF-8 <=32`, compressed/uncompressed `VarLong`, `totalParts: VarInt <=131072`, whole SHA-256, sorted descriptors <=2048. Descriptor: name <=48, sizes, tile count, SHA-256. | Requires online faction member, loaded archive block within 8 blocks, same dimension and claimed chunk owned by that faction, compatible version, unique session, valid non-negative totals, per-region <=32 MiB compressed/uncompressed, session <=256/768 MiB, and global/player capacity. Returns nonterminal upload status or terminal localized rejection. |
| C2S | `kingdoms:xaero_archive_upload_part` | UUID, ordered `sequence`, declared total, region index, `offset: VarLong`, part SHA-256, byte array <=24576 | Requires exact next sequence/region/offset, matching total, checksum, rate <=4 MiB/s, live unexpired session, and declared bounds. Data is written on bounded archive I/O workers. Returns progress or terminal failure. |
| C2S | `kingdoms:xaero_archive_upload_finish` | UUID and whole SHA-256 | Requires all declared parts and bytes. Files, ZIP structure, Xaero header, sizes, and hashes are verified asynchronously. Immediately before commit the main thread rechecks the live player/session, cancellation, faction, claim, anchor, distance, and dimension. Aggregate union remains <=2048 regions and <=256/768 MiB. Success atomically replaces the manifest and garbage-collects unreferenced blobs. |
| C2S | `kingdoms:xaero_archive_download_request` | `sessionId: UUID`, anchor, dimension | Same derived access checks and session caps. A verified immutable snapshot is prepared asynchronously, then registered only if the same server lifecycle and access remain valid. Returns `download_begin`, ordered parts and terminal status. |
| C2S | `kingdoms:xaero_archive_cancel` | `sessionId: UUID` | Cancels only the requesting player's matching upload/download; temporary files are removed asynchronously. |
| C2S | `kingdoms:xaero_archive_stats_request` | `requestId: UUID`, anchor, dimension | Same access checks. Returns bounded server-derived stats or a localized error; stale server-lifecycle continuations are discarded. |
| S2C | `kingdoms:xaero_archive_download_begin` | UUID, server identity <=64, dimension, faction UUID, sizes, parts <=131072, whole SHA-256, descriptors <=2048 | Starts one verified client receive session. Client rejects identity/dimension/faction/order/size mismatches before import. |
| S2C | `kingdoms:xaero_archive_download_part` | UUID, sequence, total, region index, offset, SHA-256, bytes <=24576 | Client requires exact order, bounds, and hash; writes are serialized on a bounded client I/O worker. Any mismatch cancels the transfer. |
| S2C | `kingdoms:xaero_archive_status` | UUID, `phase: UTF-8 <=24`, completed/total `VarLong`, terminal/success flags, localization key <=128 | Phases include upload, merge, download, complete, cancelled, and failed. Terminal failures cover access, limits, malformed metadata/order, checksum, timeout, rate, I/O, compatibility, cancellation, and lifecycle shutdown. |
| S2C | `kingdoms:xaero_archive_stats` | request UUID, dimension, compressed/uncompressed sizes, region/tile counts, success flag, localization key <=128 | Replaces the archive-screen statistics for the matching request only. |

Archive limits also include two concurrent sessions per player, sixteen globally, a 120-second inactivity timeout, and four outgoing parts per server tick. Access is rechecked on upload commit and every download tick. Compression, hashing, merging, cleanup, disk reads/writes, and Xaero reload preparation do not run on the server/client main thread.
