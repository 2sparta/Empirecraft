# Source and attribution

Legendary Survival Overhaul by Sfiomn, credited in the uploaded binary to Sfiomn, Charles445 and Icey. Upstream: https://github.com/sfiomn/LegendarySurvivalOverhaul.

The exact user-provided 1.20.1-2.4.7 input is pinned in `sources.json`. Its metadata declares “GNU General Public License v3.1”; `LICENSE-LSO.txt` includes the GNU GPL version 3 text. Original notices and metadata are retained. This adaptation and its build sources are provided under GPL version 3.

`Patch.java` and `src/main/java/` are the preferred sources for the Empirecraft changes. `build.py` deterministically applies them to the exact pinned input using genuine Minecraft/Forge APIs. It does not ship replacement API classes.

The public 1.20.1 source branch at the recorded commit declares 2.3.23.1, so it is included as a reference and is not claimed to reproduce the uploaded 2.4.7 binary. `sources/LSO-2.4.7-inspection-source.zip` contains a CFR 0.152 decompilation of that exact uploaded binary; `sources/LSO-upstream-reference-source.zip` preserves the upstream sources, resources, credits and its existing Forge LGPL license file. The input JAR, all adaptation sources and both source archives accompany the source release. Textures/audio remain credited to their original authors.
