# Recruit contracts — EmpirecraftFeudal 1.15.0 / Towny Recruits 1.4.0

All 28 Feudal Java suites pass with the real pinned H2, Spigot/Towny APIs and Java 21 runtime. Production compilation also passes against the actual installed Mohist 47.4.23 API, targeting Java 17. Standalone Bukkit proxy tests use the genuine upstream API before Forge boot; no Bukkit/Towny API stand-ins are packaged.

The new market suite passes 12 real-SQL scenarios: permanent sales and chosen commanders; exactly-once payment/projection intents; incorrect authority, capacity and stale roster rejection; own-city/trade-republic bazaars; physical counter access; treasury budget checks; captain consent and locked contracts; complete refunds including both fees; attendance payout; finite battle expiry; cancellation/refusal/timeouts; overlapped assets; SQL rollback; unavailable native projection and retained company/parent-regiment reservations until acknowledged return; and a real H2 file restart with preserved escrow, citizenship snapshot and verdict.

The citizenship test executes the compiled production adapter with type-substituted Towny boundary fixtures and real Gson JSON. It checks retained plots, temporary rank removal, exact home/rank/title/surname/join-date restoration, replay, compensated save failure, membership events and real cancellable-event handler contracts. This is not a complete Towny server/gameplay boot.

Towny Recruits passes 27 Java programs, real NBT projection/partial-file recovery, executed ownership decisions and the actual installed-JAR Mixin transformations. Tests reject forged captain UUIDs across 20 packet field contracts and preserve missing/dead/deserted records. Existing tactics, bombs, morale, skins and map tests still pass.

The exact paired Feudal JAR passed a fresh isolated Mohist fleet compatibility regression with 61 live-server assertions and 12 after restart. Evidence: [city-fleet-17.json](../validation/city-fleet-17.json).

A graphical client and the complete live mercenary battle cycle have not been run. Acceptance scenarios and administration commands: [CONTRACTS.md](../towny-recruits/CONTRACTS.md). The structured report and current JAR checksums are in [recruit-contracts-1.4.0.json](../validation/recruit-contracts-1.4.0.json).
