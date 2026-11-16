package com.pockyl.handson_delight.station;
/** Output units, including stirring bonuses, are bounded per cooking cycle. */
public final class PortionRules {
    public static final int MAX_OUTPUT = 4;
    private PortionRules() { }
    public static int recipes(int resultCount) { return Math.max(1, MAX_OUTPUT / Math.max(1, resultCount)); }
}
