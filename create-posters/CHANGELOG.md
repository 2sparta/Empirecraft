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
