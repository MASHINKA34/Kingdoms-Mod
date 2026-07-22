# Network protocols

All gameplay mutations are validated on the logical server. Client payload fields are requests, never authority for faction identity, archive ownership, prices, inventory contents, or dimension access.

## Faction protocol 8

Research uses the existing `kingdoms:start_research` C2S request and `kingdoms:faction_state` S2C snapshot. The server derives the node price from configuration, atomically checks and removes physical typed crystals, commits influence/research state, and then broadcasts the new snapshot. Repeated or stale requests cannot consume twice after completion.

## Trader protocol 8

Every mutable trader C2S payload contains the server-issued session UUID and an exact next sequence number. Registered C2S payloads are `kingdoms:trader_buy`, `kingdoms:trader_sell`, `kingdoms:trader_seller_refresh`, and `kingdoms:trader_close`. S2C state is sent through `kingdoms:trader_shop_state` or `kingdoms:seller_catalog`. The server rechecks entity role, event UUID, expiry, faction/claim access, catalog membership, price, stock, inventory space, and sequence before committing.

## Dimension protocol 2

`kingdoms:dimension_action` is operator-only C2S control for open, close, schedule-wipe, and cancel-wipe actions. `kingdoms:dimension_state` is the S2C operator snapshot and includes closed/wipe/player state for Nether and End plus the Moscow window state, seconds until close, active faction sessions, and registered-portal state.

Nether entry and return do not trust a custom C2S payload. They are enforced by server travel, portal, death, login, dimension-change, tick, and item-use hooks. Return authorization is a persistent player/session/nonce data component consumed atomically on the server.

## Xaero archive protocol 1

The archive channel is registered in both directions only for Xaero World Map 1.43.0-compatible data. The server derives server identity, faction UUID, dimension scope, and storage path.

| Direction | Payload | Purpose |
| --- | --- | --- |
| C2S | `kingdoms:xaero_archive_upload_begin` | Begin a bounded upload with sorted region descriptors and whole-transfer hash. |
| C2S | `kingdoms:xaero_archive_upload_part` | Send one ordered 24 KiB-or-smaller part with sequence, region, offset, and SHA-256. |
| C2S | `kingdoms:xaero_archive_upload_finish` | Request verification and atomic tile merge. |
| C2S | `kingdoms:xaero_archive_download_request` | Request the current faction/dimension manifest near a claimed archive block. |
| C2S | `kingdoms:xaero_archive_cancel` | Cancel only the requesting player's matching session. |
| C2S | `kingdoms:xaero_archive_stats_request` | Request bounded manifest statistics for the archive GUI. |
| S2C | `kingdoms:xaero_archive_download_begin` | Declare verified server-derived identity and region metadata. |
| S2C | `kingdoms:xaero_archive_download_part` | Stream one ordered checksummed region part. |
| S2C | `kingdoms:xaero_archive_status` | Report phase, progress, terminal state, and localization key. |
| S2C | `kingdoms:xaero_archive_stats` | Return compressed/raw sizes and region/tile counts. |

Transfers have per-region, per-session, part-count, player-session, global-session, rate, distance, loaded-anchor, timeout, and path limits. Access is rechecked at upload commit and on every download tick. Disk reads/writes, compression, hashing, merge, cleanup, and Xaero reload preparation run outside the server/client main thread; the main thread only validates small payload metadata and sends prepared chunks.
