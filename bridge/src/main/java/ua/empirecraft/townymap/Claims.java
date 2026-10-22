package ua.empirecraft.townymap;

import java.util.Set;

/** An immutable snapshot; no live Bukkit/Towny objects may leave the server thread. */
record Claims(String id, String name, String dimension, int color, int cellSize,
              Set<ClaimGeometry.Cell> cells) {
    Claims { cells = Set.copyOf(cells); }
}
