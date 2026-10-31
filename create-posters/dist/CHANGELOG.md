# 1.4.0

- Fix the Supplementaries cannon's flat atlas-average appearance by preserving each Java-model cube's texOffs, LayerDefinition atlas dimensions and ModelPart face UV layout.
- Carry opaque texture details and alpha cutouts into those surfaces. The outer muzzle rim now reveals the real recessed dark bore.
- Compute colour and texture contrast per face so the wooden support and metal barrel remain distinct. Use a muted metal wash while reserving dark graphite for its recesses.
- Preserve deformations, saved yaw/pitch, all six mounting directions, large posters and the existing CBC/mirroring fixes. Protocol and poster data stay compatible; reprint to regenerate stored images.
- Add 34 actual-resource Supplementaries regression checks and a preview, including the 9x6 poster case from the report. 181 automated checks pass overall; graphical-client/full-server checks have not been run.

# 1.3.0

- Fix the gap between a CBC cannon barrel and its mount: include the actual rotating frame, support rails, carriage axle, pitch shaft and half yaw shaft that CBC renders separately from the base block.
- Apply saved CannonYaw/CannonPitch and floor/ceiling mounting transforms to those partial models.
- Retain high-contrast features from opaque model textures: muzzle bores, grooves, recesses and coloured insets. Keep fine grain as a subdued wash and simplify detail below a readable projection scale.
- Print dark bores/insets with stronger graphite in both colour and monochrome modes.
- Generate common vanilla cube parent templates when dedicated-server assets omit them; actual CBC JSON model coverage increases from 100/119 to 112/119.
- Add a real-asset 1x3x3 cannon regression fixture with 20 checks and an exported preview. It reproduces the missing support with the old base-only geometry.
- 147 automated checks passed (66 core, 61 compatibility, 20 CBC). Graphical-client and full-server startup checks have not been run.
- Keep protocol 3, v1/v2 drawing compatibility, poster dimensions, print scales and the 1.2 wall-orientation fix. Reprint drawings to regenerate their stored geometry image.

# 1.2.0

- Fix the wall renderer's front-face rotation, outward normal and UV handedness on all four wall directions. Existing posters use the corrected renderer automatically.
- Read static model geometry from installed mod JSON assets, including inherited models/textures, blockstate variants, multipart models, element rotations and transparent texture cutouts.
- Project actual triangles with interpolated depth; suppress artificial seams across a smooth sloped face.
- Read the installed Supplementaries cannon model-builder definition as data without loading its client renderer. Apply saved yaw, pitch and mounting direction.
- Render CBC barrels, breeches, mounts, autocannon parts and other supported JSON-model blocks from their installed model files.
- Read Create/Copycats+ saved materials, including multistate material_data. Use Copycats+ part coordinates to clip and colour individual pieces.
- Use a read-only schematic neighbourhood and lazy block-entity NBT for shape fallback.
- Keep existing v1/v2 poster data, sizes, colours and physical print scales. Client/server protocol is now 3: both sides must update.
- 127 automated checks passed (66 core and 61 compatibility), including actual installed mod resources. Graphical-client and full-server startup checks have not been run.
