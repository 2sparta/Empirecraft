# Weapon claim guard validation — 2026-10-23

Result: build, policy/reflection tests and exact-JAR Mixin integration passed. Live Mohist startup and in-game checks were not performed. Production worlds/configuration were not loaded or changed.

- Minecraft 1.20.1, Mohist 71a84ff7, Forge 47.4.23.
- Compiler: JDK 17.0.20, `--release 17`; integration tests: Temurin 21.0.13+9.
- 45 policy assertions passed, including claim boundary/world selection, permission changes, offline/unknown owner, PvP changes and fail-closed/recovery paths.
- Mixin 0.8.5 successfully transformed 10 pinned Minecraft/Supplementaries/Siege Machines classes. ASM BasicVerifier verified all transformed method stacks/data flow; 23 guard calls present.
- Reflection contracts verified against the real Towny 0.101.2.0 and Mohist Level bytecode. Policy tests use separate test-only Bukkit/Towny fixtures; fixtures and test Mixin services are not packaged in the mod.

| File | SHA-256 |
| --- | --- |
| empirecraft-claim-guard-1.0.0.jar | `517ce855fec19b6e7658f110cefab6350dbe11918e9d75e9ebb7d0f77b20b58d` |
| supplementaries-1.20-3.1.44-forge.jar | `13c07f2b290f9f3dc8fcab8287ad699b0af16974291ad43c7cc95f8cf235a90b` |
| siege-machines-1.20.1-forge-1.39 (2).jar | `6a24c20ff92a9b804656bdb3dc1d06b601bc258d0c5b6fb644ccfc680555d6a1` |
| Towny-0.101.2.0.jar | `c21dd5c8e63401fe5f14be77a33e96afa727b9c8214a7f85c2c4f67b761645a6` |
| forge-1.20.1-47.4.23-server.jar | `28292cfb022ccd8f2235185b529b51a6c6fad13c6dc9edb0cbb4c8a4effe42b1` |

Reproduce using `claim-guard/build.py` and `claim-guard/test.py`. Full in-game acceptance checklist and deployment steps are in `claim-guard/README.md`.
