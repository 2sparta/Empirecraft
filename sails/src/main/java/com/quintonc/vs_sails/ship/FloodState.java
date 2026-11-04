package com.quintonc.vs_sails.ship;

import java.util.HashMap;
import java.util.Map;

/** Sparse persistent water ledger; no runtime graph, chunks or queues enter ship saves. */
public final class FloodState {
    public Map<Long,Integer> water=new HashMap<>();
    public long[] interior=new long[0];
    public int reserve;
}
