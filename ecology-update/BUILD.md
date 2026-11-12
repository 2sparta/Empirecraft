# Building and checking this update

Build from a full checkout of this repository with its pinned `server/libraries`, installed mod inputs and native retirement policy. The source archive contains the changed modules; shared tools and the Minecraft/Forge runtime come from that checkout. Use a complete JDK 17 for compilation and Java 21 for the disposable Mohist worlds.

Prepare the actual named/SRG Minecraft SDK using `tfc-integration/build.py` or the existing `sails/build.py` workflow. Set `JAVA_HOME` and put that JDK's `bin` directory first on `PATH`; the shared native overlay builder invokes `java` from `PATH`.

Build staged artifacts before replacing installed files:

```sh
python fauna/build.py --sdk /path/to/sdk --junit /path/to/junit-platform-console-standalone.jar
python seasonal-leaves/build.py --server-home server
python seasonal-calendar/build.py --staged
python world-rules/build.py --staged
EMPIRECRAFT_ARMOR_SDK=/path/to/sdk python layered-armor/build.py
```

Run the module checks with the same JDK. `seasonal-calendar/test.py --staged` checks the new calendar before installation. `world-rules/test.py --classpath-dir server` checks its policies, native NBT, Towny boundary behavior and production Mixin targets. `fauna/build.py` runs its JUnit suite. `layered-armor/test.py` uses the same `EMPIRECRAFT_ARMOR_SDK`. Save the output of `seasonal-leaves/test.py` in `work/seasonal-leaves/unit-result.txt`.

Run real game behavior separately from the unit and bytecode checks:

```sh
python fauna/smoke.py --java /path/to/java21/bin/java
python ecology-update/build_probe.py --sdk /path/to/sdk --java-home /path/to/jdk17
python ecology-update/smoke.py --java-home /path/to/java21 --runtime /workspace/work/ecology-fixture
```

The probe uses actual registered blocks, seed items, crops, mob navigation, gun sounds, explosions and bullet entities. Its world clock is advanced between native plant-renewal attempts to test eligibility without changing the production delays or rarity. Mushroom plots use mycelium, pineapple plots use sand and jungle grapes get existing jungle-log supports. Both worlds are generated scratch worlds, and no production save is read. The ecology fixture includes every server mod except the AntiXray packet filter; it has no connecting clients or chunk packets. Towny runs with its fixture economy disabled. The production configurations are preserved.

After both native suites pass for the current artifact hashes, install and package:

```sh
python ecology-update/release.py --install
python ecology-update/release.py --check
python validation/check_installed_releases.py
```

The release script preserves existing fauna settings, updates both installed sides and ADM, includes all five tested artifacts, and rejects stale native-test hashes. `ecology-update/build/empirecraft-ecology-test.jar` and `fauna-smoke.jar` are test fixtures and must never be installed in a real server.

## Combined gameplay update 2

After the ecology module checks, stage the other four artifacts:

```sh
python tfc-muskets/build.py --input tfc-muskets/native-inputs/tfc_muskets-1.20.1-forge-1.0.3.jar
python tfc-integration/build.py --tfc /path/TerraFirmaCraft-Forge-1.20.1-3.2.30.jar --sdk /path/to/sdk --java-home /path/to/jdk17
python tfc-integration/build.py --fixture --tfc /path/TerraFirmaCraft-Forge-1.20.1-3.2.30.jar --sdk /path/to/sdk --java-home /path/to/jdk17
python sails/build_hotfix.py --sdk /path/to/sdk --java-home /path/to/jdk17
python sails/test_hotfix.py --java-home /path/to/jdk17
python handbook/build.py
python tfc-integration/smoke.py --full-pack --current-update --java-home /path/to/java21 --runtime /workspace/work/gameplay-fixture
python ecology-update/release_full.py --install
```

The final command verifies the exact hashes used in both native launches before replacing files, preserving existing settings, reconciling installed metadata/ADM and producing the nine-mod installer and source archive. The native TFC and VS JARs are unchanged. Module-local old release packages remain historical snapshots; use the combined installer for the current pack.
