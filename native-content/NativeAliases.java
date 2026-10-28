package ua.empirecraft.noend;

import java.util.IdentityHashMap;
import java.util.Map;

/** Shared by the existing native guard and the registration adapters embedded in mod JARs. */
public final class NativeAliases {
    private static final Map<Object,Object> SOURCES=new IdentityHashMap<>();
    public static synchronized Object track(Object holder,Object source) {
        SOURCES.put(holder,source);return holder;
    }
    public static synchronized Object sourceId(Object holder) {return SOURCES.get(holder);}
    private NativeAliases() {}
}
