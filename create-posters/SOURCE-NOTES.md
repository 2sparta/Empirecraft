The Minecraft-facing sources use Forge stable SRG names and compile directly against an installed 1.20.1 SRG runtime. Only ua/posters classes and addon resources ship in the JAR; client and optional Copycats API declarations are compile-only.

ModelAssets reads model/texture resources from already installed mods. JsonModels handles static JSON block models. JavaModelReader interprets a bounded whitelist of model-builder instructions as geometry data, without loading or executing the Supplementaries client renderer. CopycatBridge isolates optional Copycats+ API linkage. No third-party models, textures or classes are redistributed in this addon.

Projection, Mesh and the compositors are CPU-only. ProjectionTest and CompatibilityTest run headlessly; they do not prove graphical-client startup or world placement. The compatibility fixtures read the user's installed mod JARs when present.
