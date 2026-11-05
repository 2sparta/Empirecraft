# 1.2.0

- Add a horse-sized cavalry wedge, two ranks, an open retreat corridor, per-slot width previews and mouse-wheel width control.
- Add hold-front pursuit limits, distance control, slow facing withdrawals, four movement speeds and explicit cancellation of movement, queued routes, manoeuvres and pending fire.
- Add up to eight waypoints with independent formation, facing and width. Preview future legs with unspawned probes; revalidate each leg and wait for surviving members before advancing.
- Add held fire, one-shot synchronized volleys, alternating rank fire and free fire for bows, crossbows, Musket and Boomsticks, on foot or on a controlled horse/camel. Check friendly infantry/mounts and blocks before consuming ammunition.
- Add mounted hit-and-run and continuous semicircle Carousel manoeuvres, coordinated mount navigation, imperfect velocity-based leading and discrete arrow trajectories.
- Add executed production fire/clearance tests, interception/cavalry/corridor tests, expanded packet bounds and real navigation/archer Mixin checks. Include matching English, Ukrainian and Russian UI strings. Graphical gameplay/TPS acceptance remains pending.

# 1.1.0

- Add an in-world targeting mode to the native command menu, server-validated green/red destination rectangles, yellow/red paths, 15-degree arrow-key facing and click confirmation.
- Add synchronized formation marches, outward-facing defensive squares, two-rank pike/shield walls and weapon-gated skirmish lines. Validate body clearance and actual collision surfaces, including slopes.
- Add native Supplementaries area/front bomb volleys, disk scatter, ballistic/fuse/obstacle checks, a persistent 18,000-tick cooldown and one shared bomb capacity across variants and insertion methods.
- Add real-client build preparation, tactical geometry/codec tests, executed inventory hook tests and exact-JAR server/client Mixin verification. Live graphical gameplay acceptance remains pending; see TACTICS.md.

# 1.0.4

- Reduce visibly short recruits to 2%; give 92% a player-like rendered height of 1.86–1.94 blocks, 5% a moderately taller 1.98–2.12, and 1% a rare 2.12–2.50.
- Apply the new deterministic heights to existing recruits on world join; preserve skin selection and the 1.0.3 renderer crash fix.
- Check 300,000 UUIDs for the new proportions and matching collision/eye scale; retain the existing client/server integration tests.
- Ship updated client/server JARs and ADM metadata in a separate branch.

# 1.0.3

- Fix the reported client registration crash `NoClassDefFoundError: org.spongepowered.asm.synthetic.args.Args$1`.
- Replace ModifyArgs with ordinary typed variable hooks; no generated Args class or per-frame argument array is required.
- Add a regression that fails on 1.0.2 and passes on 1.0.3, validates hook order, and executes the actual compiled scaling/restoration hooks in a CPU-only runtime harness.
- Preserve all 128 skins, stable weighted heights, matching hitboxes and existing gameplay behavior.
- Publish in a separate branch; no merge into main.

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
