package ua.empirecraft.feudal;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import static ua.empirecraft.feudal.Database.*;
import static ua.empirecraft.feudal.Model.*;

/** Persisted per-player command and menu languages, independent of client settings. */
public final class CommandPreferences {
    final Database db;
    final Map<UUID,String> values=new ConcurrentHashMap<>();
    final Map<UUID,String> menus=new ConcurrentHashMap<>();
    public CommandPreferences(Database db)throws SQLException {
        this.db=db;
        db.execute("CREATE TABLE IF NOT EXISTS command_languages(person VARCHAR(36) PRIMARY KEY,language VARCHAR(2) NOT NULL CHECK(language IN('en','uk','ru')))");
        for(var row:db.query("SELECT * FROM command_languages"))values.put(UUID.fromString(string(row,"person")),string(row,"language"));
        db.execute("CREATE TABLE IF NOT EXISTS menu_languages(person VARCHAR(36) PRIMARY KEY,language VARCHAR(2) NOT NULL CHECK(language IN('en','uk','ru')))");
        for(var row:db.query("SELECT * FROM menu_languages"))menus.put(UUID.fromString(string(row,"person")),string(row,"language"));
    }
    public String get(UUID player){return values.getOrDefault(player,"en");}
    // Preserve the previous behavior until the player explicitly selects a menu language.
    public String getMenu(UUID player){return menus.getOrDefault(player,get(player));}
    public static String code(String value){
        String code=value.toLowerCase(Locale.ROOT).replace('-','_');
        return switch(code){case "en","en_us","en_gb","english"->"en";case "uk","uk_ua","українська"->"uk";case "ru","ru_ru","русский"->"ru";default->throw new IllegalArgumentException("Language: en, uk, ru. /language en|uk|ru");};
    }
    public void set(UUID player,String value){String language=code(value);
        db.transaction(()->{db.execute("MERGE INTO command_languages KEY(person) VALUES(?,?)",player.toString(),language);return null;});
        values.put(player,language);
    }
    public void setMenu(UUID player,String value){String language=code(value);
        db.transaction(()->{db.execute("MERGE INTO menu_languages KEY(person) VALUES(?,?)",player.toString(),language);return null;});
        menus.put(player,language);
    }
}
