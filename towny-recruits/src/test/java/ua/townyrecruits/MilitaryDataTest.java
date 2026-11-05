package ua.townyrecruits;

import java.util.*;
import net.minecraft.nbt.CompoundTag;
import ua.townyrecruits.MilitaryData.*;

public final class MilitaryDataTest {
    public static void main(String[] args) {
        UUID nation = UUID.randomUUID(), other = UUID.randomUUID(), king = UUID.randomUUID(), old = UUID.randomUUID(), commander = UUID.randomUUID(), stranger = UUID.randomUUID();
        UUID regiment = UUID.randomUUID(), group = UUID.randomUUID(), loaded = UUID.randomUUID(), unloaded = UUID.randomUUID();
        var n = new Policy.Nation(nation,"N",king,"King",1,Map.of(king,"King",old,"Old",commander,"New"),Set.of(),Set.of());
        var x = new Policy.Nation(other,"X",stranger,"Enemy",1,Map.of(stranger,"Enemy"),Set.of(),Set.of());
        var snapshot = new Policy.Snapshot(Map.of(nation,n,other,x),Map.of(king,nation,old,nation,commander,nation,stranger,other));
        assert MilitaryPolicy.manages(nation,king,null,false,snapshot);
        assert MilitaryPolicy.manages(nation,old,old,false,snapshot);
        assert MilitaryPolicy.manages(nation,commander,null,true,snapshot);
        assert !MilitaryPolicy.manages(nation,stranger,stranger,true,snapshot) : "Officer rights never cross national borders";
        assert !MilitaryPolicy.manages(nation,old,null,false,snapshot);
        ArmyData army = new ArmyData(); army.put(loaded,new ArmyData.Unit(old,nation,false)); army.put(unloaded,new ArmyData.Unit(old,nation,false));
        MilitaryData data = new MilitaryData(); var r = new Regiment(regiment,nation,"Перший_полк",king); data.regiments.put(regiment,r);
        data.companies.put(group,new Company(regiment,old)); data.members.put(loaded,group); data.members.put(unloaded,group);
        var s = data.squad(group); s.morale = 21.5; s.initial = 10; s.lost = 6; s.deaths = 9; s.deserted = 2; s.routing = true; s.combat = 1234; s.lastLoss = 1230;
        s.routSize = 4; s.escaped = 3; s.escapeX = 80; s.escapeY = 65; s.escapeDimension = "minecraft:overworld";
        MilitaryData restored = MilitaryData.load(data.m_7176_(new CompoundTag()));
        assert restored.regiments.equals(data.regiments) && restored.companies.equals(data.companies) && restored.members.equals(data.members);
        s = restored.squad(group); assert s.routing && s.morale == 21.5 && s.initial == 10 && s.lost == 6 && s.deaths == 9 && s.deserted == 2 && s.combat == 1234;
        assert s.routSize == 4 && s.escaped == 3 && s.escapeX == 80 && s.escapeY == 65 && s.escapeDimension.equals("minecraft:overworld");
        MilitaryPolicy.transfer(army,restored,group,r,commander,snapshot);
        assert army.units.get(loaded).owner().equals(commander) && army.units.get(unloaded).owner().equals(commander);
        assert army.counts.get(nation) == 2 && restored.companies.get(group).commander().equals(commander);
        army.reconcile(new Policy.Snapshot(snapshot.nations(),Map.of(king,nation,commander,nation,stranger,other)));
        assert army.counts.get(nation) == 2 : "The old commander's departure cannot retire reassigned offline soldiers";
        try { MilitaryPolicy.transfer(army,restored,group,r,stranger,snapshot); throw new AssertionError("Foreign commander accepted"); }
        catch (IllegalArgumentException expected) {}
        assert army.units.get(unloaded).owner().equals(commander) && restored.companies.get(group).commander().equals(commander);
        UUID foreign = UUID.randomUUID(); army.put(foreign,new ArmyData.Unit(stranger,other,false)); restored.members.put(foreign,group);
        try { MilitaryPolicy.transfer(army,restored,group,r,old,snapshot); throw new AssertionError("Foreign soldier stolen"); }
        catch (IllegalArgumentException expected) {}
        assert army.units.get(loaded).owner().equals(commander) && army.units.get(foreign).owner().equals(stranger) : "A failed transfer must be atomic";
        var afterRestart = ArmyData.load(army.m_7176_(new CompoundTag())); assert afterRestart.units.get(unloaded).owner().equals(commander);
        afterRestart.reconcile(Policy.Snapshot.empty()); assert afterRestart.counts.isEmpty();
        assert MilitaryData.load(new CompoundTag()).regiments.isEmpty() : "Older worlds need no migration or reset";
        System.out.println("PASS national/commander authority, real military NBT, persistent rout/casualty/deserter statistics, atomic same-nation transfers including offline units, commander departure and nation deletion");
    }
}
