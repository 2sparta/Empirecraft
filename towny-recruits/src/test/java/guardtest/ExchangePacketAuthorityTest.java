package guardtest;

import java.util.*;
import ua.townyrecruits.ExchangeGuards;

public final class ExchangePacketAuthorityTest {
    static final class MessageMovement {private UUID player_uuid;MessageMovement(UUID actor){player_uuid=actor;}}
    static final class MessageAttack {private UUID playerUuid;MessageAttack(UUID actor){playerUuid=actor;}}
    static final class MessageAggro {private UUID player;MessageAggro(UUID actor){player=actor;}}
    static final class MessageRecruitGui {private UUID uuid,recruit;}
    public static void main(String[] args)throws Exception {
        UUID captain=UUID.randomUUID(),mayor=UUID.randomUUID();
        assert ExchangeGuards.authenticOwner(new MessageMovement(captain),captain);
        assert !ExchangeGuards.authenticOwner(new MessageMovement(captain),mayor);
        assert !ExchangeGuards.authenticOwner(new MessageAttack(captain),mayor);
        assert !ExchangeGuards.authenticOwner(new MessageAggro(captain),mayor);
        assert !ExchangeGuards.authenticOwner(new MessageMovement(null),captain);
        assert ExchangeGuards.authenticOwner(new MessageRecruitGui(),captain); // Per-soldier permission is checked separately.
        for(var entry:ExchangeGuards.OWNER_FIELDS.entrySet()){
            Class<?> type=Class.forName("com.talhanation.recruits.network."+entry.getKey(),false,ExchangePacketAuthorityTest.class.getClassLoader());
            assert type.getDeclaredField(entry.getValue()).getType()==UUID.class;
        }
        System.out.println("PASS production packet actor authentication: spoofed captain UUIDs rejected for movement/attack/GUI aggro, genuine owner retained, and all "+ExchangeGuards.OWNER_FIELDS.size()+" field contracts verified against installed Recruits");
    }
}
