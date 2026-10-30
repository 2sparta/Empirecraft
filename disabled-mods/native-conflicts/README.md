# Duplicate Create Big Cannons provider

The identical `medieval.3` JAR was present in both `mods/` and `server/mods/` alongside the native-patched `medieval.2` provider pinned in `native-content/inputs.json`. It is preserved here (Git blob `fea9095f277729645a394b990f93759275bd147a`) rather than loaded twice.

The isolated Forge run with both providers registered `createbigcannons:molten_nethersteel` and restored `createbigcannons:medieval/bronze`, violating the current native policy. Keep one active CBC provider on each side. Any future migration to this archived version must preserve the native exclusions, bronze route, migration behavior and side parity and pass `native-content/smoke.py` before installation.
