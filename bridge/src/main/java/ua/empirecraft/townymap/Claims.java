package ua.empirecraft.townymap;

import java.util.Set;

/** An immutable snapshot; no live Bukkit/Towny objects may leave the server thread. */
record Claims(String id, String name, String dimension, int color, int cellSize,
              Set<ClaimGeometry.Cell> cells, String detail, int layer, String banditNation) {
    Claims { cells = Set.copyOf(cells); java.util.Objects.requireNonNull(banditNation); }
    Claims(String id, String name, String dimension, int color, int cellSize,
           Set<ClaimGeometry.Cell> cells, String detail, int layer) {
        this(id, name, dimension, color, cellSize, cells, detail, layer, "");
    }
    Claims(String id,String name,String dimension,int color,int cellSize,Set<ClaimGeometry.Cell> cells) {
        this(id,name,dimension,color,cellSize,cells,name,0);
    }
}

