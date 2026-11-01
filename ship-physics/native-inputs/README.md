The MIT-licensed installed Ship Physics 1.2.0 JAR is the pinned overlay input.
SHA-256: `0a864ef88e62651804c428f5c5190d93bd03186a6808f94a7ec9eb070413e979`.

Its source was absent from the repository. `CollisionDamage.java` was recovered
with CFR 0.152 after remapping Minecraft symbols with the repository's existing
offline mappings, then repaired and changed here. The new `ImpactSpread.java`
and all build/test scripts are maintained source. The build preserves every
unmodified input entry except the version metadata/public constant; it does not
require a decompiler. `tools/PatchMassBounds.java` additionally corrects the six
inclusive-maximum operations in the pinned `MassSync$Job` bytecode and rejects
an unexpected input layout. The original license remains embedded in the output JAR.
