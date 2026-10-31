import com.quintonc.vs_sails.protection.*;
import net.minecraft.nbt.CompoundTag;
import java.util.*;

public final class ShipRegistryTest {
    public static void main(String[] args) {
        UUID town=UUID.randomUUID(),person=UUID.randomUUID(),ally=UUID.randomUUID();var data=new ShipRegistry();
        var vessel=new ShipAccess.Vessel(town,person);vessel.offeredTo=ally;vessel.grants.towns.add(ally);vessel.grants.players.add(person);data.ships.put(42L,vessel);
        var fleet=new ShipAccess.Grants();fleet.players.add(person);data.fleets.put(town,fleet);
        var saved=data.save(new CompoundTag());var loaded=ShipRegistry.load(saved);var restored=loaded.ships.get(42L);
        if(!town.equals(restored.town) || !person.equals(restored.founder) || !ally.equals(restored.offeredTo)
                || !restored.grants.towns.contains(ally) || !restored.grants.players.contains(person)
                || !loaded.fleets.get(town).players.contains(person))throw new AssertionError("ownership and pending transfer lost on reload");
        restored.accept(ally);var second=ShipRegistry.load(loaded.save(new CompoundTag()));
        if(!second.ships.get(42L).town.equals(ally) || !second.ships.get(42L).grants.towns.isEmpty()
                || !second.ships.get(42L).grants.players.isEmpty())throw new AssertionError("transfer/reset not durable");
        if(!saved.equals(ShipRegistry.load(saved).save(new CompoundTag())))throw new AssertionError("registry roundtrip changed data");
        System.out.println("SHIP_REGISTRY_NBT_PASS");
    }
}
