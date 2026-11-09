package ua.townyrecruits;
import java.util.*;

/** Brigadier's unquoted readString accepts ASCII only; Towny names also use Ukrainian. */
public final class CommandWords {
    private CommandWords(){}
    public static List<String> parse(String text){
        List<String> result=new ArrayList<>();int cursor=0;
        while(cursor<text.length()){
            while(cursor<text.length()&&Character.isWhitespace(text.charAt(cursor)))cursor++;
            if(cursor==text.length())break;
            char first=text.charAt(cursor);StringBuilder word=new StringBuilder();
            if(first=='"'||first=='\''){
                char quote=first;cursor++;boolean closed=false;
                while(cursor<text.length()){
                    char c=text.charAt(cursor++);if(c==quote){closed=true;break;}
                    if(c=='\\'){if(cursor==text.length())throw new IllegalArgumentException("Незавершений запис у лапках.");char escaped=text.charAt(cursor++);if(escaped!=quote&&escaped!='\\')throw new IllegalArgumentException("У лапках дозволено екранувати лише лапки або зворотну скісну риску.");c=escaped;}word.append(c);
                }
                if(!closed||cursor<text.length()&&!Character.isWhitespace(text.charAt(cursor)))throw new IllegalArgumentException("Перевірте лапки та пробіли в команді.");
            }else while(cursor<text.length()&&!Character.isWhitespace(text.charAt(cursor)))word.append(text.charAt(cursor++));
            result.add(word.toString());
        }
        return result;
    }
}
