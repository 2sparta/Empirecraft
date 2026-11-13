package ua.empirecraft.chat;

/** New title catalogs can contain entries predating their optional bitmap artwork. */
public final class CosmeticDisplay {
    public static String text(String glyph,String label,String key) {
        if(glyph!=null&&!glyph.isBlank())return glyph;
        return "["+(label==null||label.isBlank()?key:label)+"]";
    }
    public static boolean bitmap(String value) {
        return !value.isEmpty()&&value.codePoints().allMatch(c->c>=0xe900&&c<0xf000);
    }
}
