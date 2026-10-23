package ua.empirecraft.townymap;

import java.util.Set;

/** An immutable snapshot; no live Bukkit/Towny objects may leave the server thread. */
record Claims(String id, String name, String dimension, int color, int cellSize,
              Set<ClaimGeometry.Cell> cells, String detail, int layer) {
    Claims { cells = Set.copyOf(cells); }
    Claims(String id,String name,String dimension,int color,int cellSize,Set<ClaimGeometry.Cell> cells) {
        this(id,name,dimension,color,cellSize,cells,name,0);
    }
}

