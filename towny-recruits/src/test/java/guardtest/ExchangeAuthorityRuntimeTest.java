package guardtest;

import java.util.*;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.*;

/** Executes the production owner/lock decision; mayor membership never grants a second controller. */
public final class ExchangeAuthorityRuntimeTest {
    public static final class Level {boolean client;public boolean m_5776_(){return client;}}
    public static final class Recruit {final Level level=new Level();UUID owner=UUID.randomUUID(),group=UUID.randomUUID();boolean owned=true,alive=true;public boolean getIsOwned(){return owned;}public boolean m_6084_(){return alive;}public UUID getOwnerUUID(){return owner;}public UUID getGroup(){return group;}public Level m_20193_(){return level;}}
    public static final class Bridge {public static Bridge INSTANCE;public boolean ready=true,index=true;public boolean censusReady(){return index;}}
    static final class Loader extends ClassLoader {Class<?> define(byte[] bytes){return defineClass(null,bytes,0,bytes.length);}}
    public static void main(String[] args)throws Exception {
        String source="ua/townyrecruits/RegimentExchange",fixture="guardtest/ExchangeAuthorityRuntimeTest$";ClassNode node=new ClassNode();try(var in=ExchangeAuthorityRuntimeTest.class.getClassLoader().getResourceAsStream(source+".class")){new ClassReader(in.readAllBytes()).accept(node,0);}
        node.methods.removeIf(m->!Set.of("mayControl","locked","<clinit>").contains(m.name));node.nestMembers=null;node.fields.removeIf(f->!Set.of("locks","regimentLocks","syncing").contains(f.name));
        var writer=new ClassWriter(0);node.accept(new ClassRemapper(writer,new SimpleRemapper(Map.of(source,"guardtest/generated/ExchangeAccess","ua/townyrecruits/Bridge",fixture+"Bridge","com/talhanation/recruits/entities/AbstractRecruitEntity",fixture+"Recruit","net/minecraft/world/level/Level",fixture+"Level"))));
        Class<?> access=new Loader().define(writer.toByteArray());var method=access.getMethod("mayControl",Recruit.class,UUID.class);var locks=access.getDeclaredField("locks");locks.setAccessible(true);Recruit r=new Recruit();Bridge.INSTANCE=new Bridge();
        assert (boolean)method.invoke(null,r,UUID.fromString(r.owner.toString()));assert !(boolean)method.invoke(null,r,UUID.randomUUID());assert !(boolean)method.invoke(null,r,null);
        locks.set(null,Set.of(r.group));assert !(boolean)method.invoke(null,r,r.owner);locks.set(null,Set.of());Bridge.INSTANCE.ready=false;assert !(boolean)method.invoke(null,r,r.owner);Bridge.INSTANCE.ready=true;Bridge.INSTANCE.index=false;assert !(boolean)method.invoke(null,r,r.owner);Bridge.INSTANCE.index=true;
        r.owned=false;assert !(boolean)method.invoke(null,r,r.owner);r.owned=true;r.alive=false;assert !(boolean)method.invoke(null,r,r.owner);r.alive=true;
        Bridge.INSTANCE=null;r.level.client=true;assert (boolean)method.invoke(null,r,r.owner);assert !(boolean)method.invoke(null,r,UUID.randomUUID());
        System.out.println("PASS executed production authority: equal UUID captain only, employer mayor/other players denied, listing lock, unavailable ledger, unowned/dead units and client-side selection");
    }
}
