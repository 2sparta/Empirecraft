package ua.empirecraft.feudal;

import java.sql.SQLException;
import java.util.*;
import static ua.empirecraft.feudal.Database.*;
import static ua.empirecraft.feudal.Model.*;

/** Personal wallet choices and read-only quotes using the same rules as /cu exchange. */
final class TradeCurrencies {
    record Currency(String code,String name,long balance) {}
    private final FeudalService service;
    TradeCurrencies(FeudalService service){this.service=service;}
    List<Currency> owned(UUID player)throws SQLException {
        return service.db.query("SELECT c.code,c.name,a.balance FROM currencies c JOIN accounts a ON a.code=c.code WHERE c.backing='GOLD' AND a.owner=? AND a.balance>0 ORDER BY c.code","p:"+player)
            .stream().map(r->new Currency(string(r,"code"),string(r,"name"),number(r,"balance"))).toList();
    }
    List<String> targets(String source)throws SQLException {
        return service.db.query("SELECT code FROM currencies WHERE backing='GOLD' AND code<>? ORDER BY code",source).stream().map(r->string(r,"code")).toList();
    }
    Currency personal(UUID player,String code)throws SQLException {
        return code.isEmpty()?new Currency("",LocalText.tr("Немає монет"),0):new Currency(code,string(service.currency(code),"name"),service.balance("p:"+player,code));
    }
    String name(String code)throws SQLException {return string(service.currency(code),"name");}
    String initial(UUID player)throws SQLException {
        var owned=owned(player);return owned.isEmpty()?"":owned.get(0).code;
    }
    static String next(List<String> codes,String selected){return codes.isEmpty()?"":codes.get((codes.indexOf(selected)+1)%codes.size());}
    String quote(String source,String target)throws SQLException {
        if(source.isEmpty() || target.isEmpty())return LocalText.tr("Для курсу оберіть дві валюти міст.");
        if(!"GOLD".equals(string(service.currency(source),"backing")) || !"GOLD".equals(string(service.currency(target),"backing")))
            return LocalText.tr("Курс недоступний: потрібне золоте забезпечення обох валют.");
        try{return LocalText.tr("За 1.00 ")+source+LocalText.tr(" можна купити ")+format(service.currencies.quote(100,source,target).coins())+" "+target;}
        catch(IllegalArgumentException unavailable){return LocalText.tr("Курс недоступний: ")+unavailable.getMessage();}
    }
}
