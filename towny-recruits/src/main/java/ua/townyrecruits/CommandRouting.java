package ua.townyrecruits;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Preserve arguments byte-for-byte. Only disambiguate known command collisions. */
public final class CommandRouting {
    private static final Pattern FIRST=Pattern.compile("^\\s*@[pares](?=\\[|\\s|$)");
    private static final Pattern SECOND=Pattern.compile("^\\s*[^\\s]+\\s+@[pares](?=\\[|\\s|$)");
    private CommandRouting(){}
    public static String route(String input,boolean pending,String confirm,String cancel){
        if(input==null||!input.startsWith("/"))return input;
        int end=1;while(end<input.length()&&!Character.isWhitespace(input.charAt(end)))end++;
        String root=input.substring(1,end).toLowerCase(Locale.ROOT),args=input.substring(end);
        if(pending&&args.isBlank()){
            if(root.equals(confirm.toLowerCase(Locale.ROOT))||root.equals("towny:"+confirm.toLowerCase(Locale.ROOT)))return "/trconfirm";
            if(root.equals(cancel.toLowerCase(Locale.ROOT))||root.equals("towny:"+cancel.toLowerCase(Locale.ROOT)))return "/trcancel";
        }
        if(root.contains(":")||!Set.of("give","clear","tp","teleport").contains(root))return input;
        boolean selector=FIRST.matcher(args).find()||Set.of("tp","teleport").contains(root)&&SECOND.matcher(args).find();
        return selector?"/minecraft:"+root+args:input;
    }
}
