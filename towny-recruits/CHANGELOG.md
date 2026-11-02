# 1.0.2

- Add 128 original 64x64 human skins and a preview gallery; stable UUID-based selection with no biome pool restriction.
- Assign stable weighted heights from 1.5 to 2.5 blocks, mostly near player height and approximately 1% tall.
- Match standing collision height, eye height and human renderer scale; preserve width, equipment and native animation layers.
- Refresh dimensions on entity join/pose changes on both sides. No extra per-tick AI scans or custom synchronization packets.
- Include deterministic asset generation and statistical/client-renderer regression checks.

# 1.0.1

- Skip zero-length entity region files; still reject non-empty truncated files.
- Commit initial census discoveries only after a complete successful scan.
- Add `/townyrecruits census`; `/sync` retries a failed census and forces faction reconciliation.
- Show the actual census error in `/status` and distinct hire refusal reasons.
- Defer Towny event reconciliation until the mutation completes.
- Repair stale managed factions even when the Towny snapshot has not changed; remove old `ec_` bridge factions and associated relations, embargoes, treaties, scoreboard entries and Recruits claims.
- Preserve saved nation overrides and unit tracking during the update.

Validated with real runtime libraries and the supplied world-file snapshot. Live server/client validation is still required.
