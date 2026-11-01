package com.quintonc.vs_sails.config;
import java.util.HashMap;
public class ConfigTest {
    private static int assertions;
    private static void rejects(String key,String value) {
        ConfigUtils.config=new HashMap<>();ConfigUtils.config.put(key,value);
        try { ConfigUtils.validateConfig(); throw new AssertionError("accepted "+key+"="+value); }
        catch(IllegalArgumentException expected) { assertions++; }
    }
    public static void main(String[] args) {
        for(String value:new String[]{"0","-1","361","NaN","Infinity","2.5"}) rejects("wheel-interval",value);
        for(String value:new String[]{"0","360","NaN"}) rejects("no-sail-zone",value);
        rejects("max-assemble-blocks","0");rejects("sail-power","-1");rejects("realistic-rudder","yes");
        for(String key:new String[]{"rudder-power","keel-power","air-density","buoy-float-strength"}) rejects(key,"NaN");
        for(String value:new String[]{"-1","21","NaN","Infinity"})rejects("water-stability-strength",value);
        ConfigUtils.config=new HashMap<>();ConfigUtils.config.put("wheel-interval","7");ConfigUtils.validateConfig();
        if(!ConfigUtils.config.get("wheel-interval").equals("7")) throw new AssertionError("non-divisor step rejected");
        assertions++;
        System.out.println("PASS: "+assertions+" configuration assertions");
    }
}
