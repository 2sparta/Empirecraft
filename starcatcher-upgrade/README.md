# Starcatcher Forge 1.20.1 upgrade

The official Modrinth API was checked on 2026-11-07. The newest compatible
release was **3.3.10-FORGE-1.20.1**, version `5prntqi9`, published 2026-10-16:
https://modrinth.com/mod/starcatcher/version/5prntqi9 .

This replaces 2.3.19 and installs its required external dependencies on both
client and server: Selling Bin 1.4.3, Fancy Tab Sections 6.2.3 and LibTooltips
2.3.2. Starcatcher's own JarJar providers are retained; Forge selects the
compatible nested wdUtils, NeoBackports and Tiny Multiblock Library versions.

`sources.json` pins the original download URL, version ID and SHA-512 of every
official input. Download those files to a directory and reproduce the installed
bytes with JDK 17:

```
python3 starcatcher-upgrade/build.py --inputs /path/to/official-jars
python3 native-content/test.py
```

The existing native-content registration/resource guards preserve the pack's
previous excluded content and bronze policy. The new chorus minnow model is
also excluded. This update does not restore the previously removed End or
Nether fishing content. Canonical Starcatcher 3 dimension groups are retained as restriction metadata. The updated `multiverse-compat` pack adds world_europe to the Overworld group without old fish definition overrides. New plaques and Selling Bin also have reviewed block-mass assignments.

The change report is in `native-content/audit/build.json`;
original SHA-256 inputs remain pinned in `native-content/inputs.json`.

The upstream Java license notice is preserved in `UPSTREAM-LICENSE.txt` and
inside the modified Starcatcher JAR. It permits this Forge modification; assets
retain their upstream All Rights Reserved status. Upstream source and notice:
https://github.com/wdiscute/starcatcher .

Municipal tournaments use the separate server-only `civic-events` integration;
Starcatcher's existing personal tournament system remains available. City
rules, contribution escrow, genuine server catches, optional entrance fees and
Contact invitations are described in [CIVIC-EVENTS.md](../feudal/CIVIC-EVENTS.md).
