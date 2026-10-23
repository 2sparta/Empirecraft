package com.palmergames.bukkit.towny;

import java.util.Collection;
import java.util.List;

public final class TownyAPI {
    public static TownyAPI instance;
    public static int lookups;
    public static TownyAPI getInstance() { lookups++; return instance; }
    public Collection<?> getTowns() { return List.of(); }
}

