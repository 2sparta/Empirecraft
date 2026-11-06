package ua.empirecraft.engagement;

import net.minecraft.nbt.*;
import java.util.*;

public final class CombatLedgerTest {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        var ledger=new CombatLedger();String commander="P:"+UUID.randomUUID(),company="C:"+UUID.randomUUID();
        ledger.tag(commander,1200,1000);ledger.tag(company,1200,1000);ledger.tag("S:42",1200,1000);ledger.flagPositions.put(42L,123456789L);
        ledger.tag(commander,1100,500);check(ledger.until.get(commander)==2200,"Old events cannot shorten a combat timer");
        var encoded=ledger.save(new CompoundTag());var restored=CombatLedger.load(encoded);
        check(restored.until.equals(ledger.until)&&restored.flagPositions.equals(ledger.flagPositions),"Real NBT preserves commanders, companies, ships and official flag positions");
        check(restored.active(company,2199)&&!restored.active(company,2200),"Exact expiry boundary");
        restored.expire(2200);check(restored.until.isEmpty()&&restored.flagPositions.containsKey(42L),"Expiry clears combat, not physical flag registration");
        restored.tag("S:42",3000,1000);restored.tag("S:42",3500,1000);check(restored.active("S:42",4499)&&!restored.active("S:42",4500),"Fresh attacks extend the same ship identity");
        var afterTransfer=CombatLedger.load(restored.save(new CompoundTag()));check(afterTransfer.active("S:42",4000),"Town/captain transfers cannot replace a ship's combat identity");
        System.out.println("COMBAT_LEDGER_NBT_PASS scenarios=6");
    }
}
