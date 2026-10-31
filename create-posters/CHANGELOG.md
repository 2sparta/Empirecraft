# 1.1.0

- Muted material colours and a monochrome mode.
- Weighted contours, softer internal edges, adaptive fine joints and antialiased composition.
- Automatic trimming of empty schematic margins and a wider main elevation for three views.
- Shared/per-view fitting and physical scales 1:16, 1:32, 1:64, 1:128, 1:256.
- Wall sheets 3×2, 6×4, 9×6, 12×8 with matching hitboxes, placement checks, saved dimensions and renderer size.
- Higher projection and texture resolution for larger sheets; bounded compressed raster storage and texture caching.
- Size-dependent paper and ink cost; reject a scale that does not fit before consuming materials.
- Preserve version 1 poster data and rendering. Both client and server must install 1.1.0.
- 66 automated checks, including the largest six-view NBT payload. New graphical-client and full-server startup checks have not been run.
