package ua.empirecraft.feudal;

import com.google.gson.*;
import java.time.Instant;
import java.util.*;

/** Bounded Discord embeds. Player text cannot mention users, roles or everyone. */
final class DiscordMessage {
    private DiscordMessage(){}
    static String text(String value,int limit){
        if(value==null||value.isBlank())return "—";
        String safe=value.replaceAll("[\\p{Cntrl}&&[^\\n\\t]]","").replace("`","ˋ");
        if(safe.isBlank())return "—";if(safe.length()<=limit)return safe;
        int end=limit-1;if(Character.isHighSurrogate(safe.charAt(end-1)))end--;return safe.substring(0,end)+"…";
    }
    static String create(String title,String description,Map<String,String> fields,long now){
        var embed=new JsonObject();embed.addProperty("title",text(title,200));embed.addProperty("description",text(description,800));
        embed.addProperty("color",0x36a66b);embed.addProperty("timestamp",Instant.ofEpochMilli(now).toString());
        var items=new JsonArray();int count=0;for(var field:fields.entrySet()){
            if(++count>8)break;var item=new JsonObject();item.addProperty("name",text(field.getKey(),80));item.addProperty("value",text(field.getValue(),500));items.add(item);
        }embed.add("fields",items);var footer=new JsonObject();footer.addProperty("text","Empirecraft • Реєстрації • Розгляд у Minecraft");embed.add("footer",footer);
        var embeds=new JsonArray();embeds.add(embed);var message=new JsonObject();message.add("embeds",embeds);
        var mentions=new JsonObject();mentions.add("parse",new JsonArray());message.add("allowed_mentions",mentions);return message.toString();
    }
    static String nonce(String payload,long id){var message=JsonParser.parseString(payload).getAsJsonObject();message.addProperty("nonce","ec"+Long.toUnsignedString(id));message.addProperty("enforce_nonce",true);return message.toString();}
}
