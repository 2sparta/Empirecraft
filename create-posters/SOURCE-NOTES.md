The Minecraft-facing sources use Forge stable SRG names and compile directly against an installed 1.20.1 SRG runtime. Only ua/posters classes and addon resources ship in the JAR; client and optional Copycats API declarations are compile-only.

ModelAssets reads model/texture resources from already installed mods. JsonModels handles static JSON block models. JavaModelReader interprets a bounded whitelist of model-builder instructions as geometry data, without loading or executing the Supplementaries client renderer. CopycatBridge isolates optional Copycats+ API linkage. No third-party models, textures or classes are redistributed in this addon.

Projection, Mesh and the compositors are CPU-only. ProjectionTest, CompatibilityTest, CBCRegressionTest and SupplementariesRegressionTest run headlessly; they do not prove graphical-client startup or world placement. The compatibility fixtures read the user's installed mod JARs when present.

CBCModels adds the installed CBC cannon-mount partial models using the transformations from its renderer. Common missing vanilla cube parents are generated procedurally. Opaque texture samplers preserve high-contrast features without bundling third-party model or texture files. The 1x3x3 regression preview is produced by the addon compositor from an analogous test construction, not from the user's schematic file.

In 1.4, JavaModelReader also retains texOffs and LayerDefinition atlas size, and emits ModelPart cuboid UVs with per-face atlas averages and alpha/contrast samplers. Texture pixel arrays are shared by the face samplers. SupplementariesRegressionTest reads the installed 64x64 cannon atlas and factory to verify the transparent outer muzzle, recessed bore, wood/metal separation, mounting poses and 9x6 poster output.
