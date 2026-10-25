package ua.empirecraft.feudal;

import java.util.UUID;

/** Only the synchronous authenticated famine deletion may trigger a native capital succession. */
final class FamineAuthority {
    private FamineAuthority(){}
    static boolean allows(String estate,UUID town,UUID nation){
        if(town==null || estate==null)return false;
        return estate.equals("t:"+town) || nation!=null && estate.equals("n:"+nation);
    }
}
