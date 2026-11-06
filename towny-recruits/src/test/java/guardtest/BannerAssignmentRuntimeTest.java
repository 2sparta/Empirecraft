package guardtest;

import java.lang.reflect.*;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.world.InteractionHand;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.*;
import ua.townyrecruits.RegimentalBanners.*;

/** Executes the compiled banner inventory transaction with small item/entity fixtures. */
public final class BannerAssignmentRuntimeTest {
    public static class Item {}
    public static final class Color { final int id; Color(int id){this.id=id;}public int m_41060_(){return id;} }
    public static final class Banner extends Item { final Color color;Banner(int color){this.color=new Color(color);}public Color m_40545_(){return color;} }
    public static final class Stack {
        final Item item;int count;final ListTag patterns;
        Stack(Item item,int count){this(item,count,new ListTag());}Stack(Item item,int count,ListTag patterns){this.item=item;this.count=count;this.patterns=patterns;}
        public boolean m_41619_(){return count==0;}public Item m_41720_(){return item;}public Stack m_41777_(){return new Stack(item,count,patterns.m_6426_());}
        public void m_41764_(int amount){count=amount;}public void m_41774_(int amount){count-=amount;}
    }
    public static final class BannerNbt { public static ListTag m_58487_(Stack stack){return stack.patterns;} }
    public static final class Player { Stack hand;public Stack m_21205_(){return hand;} }
    public static final class Recruit {
        final UUID id=UUID.randomUUID();boolean routed;Stack off=new Stack(new Item(),0);final Stack main=new Stack(new Item(),1),helmet=new Stack(new Item(),1);
        public Stack m_21206_(){return off;}public UUID m_20148_(){return id;}
        public void m_21008_(InteractionHand hand,Stack stack){assert hand==InteractionHand.OFF_HAND;off=stack;}
    }
    public static final class Morale { public static boolean routing(Recruit r){return r.routed;} }
    public static final class Data {
        public final Map<UUID,Design> banners=new HashMap<>();public final Map<UUID,UUID> bearers=new HashMap<>();public void m_77762_(){}
    }
    static final class Loader extends ClassLoader { Class<?> define(byte[] bytes){return defineClass(null,bytes,0,bytes.length);} }
    static java.lang.reflect.Method equip()throws Exception {
        String source="ua/townyrecruits/RegimentalBanners",fixture="guardtest/BannerAssignmentRuntimeTest$";Map<String,String> names=new HashMap<>();names.put(source,"guardtest/generated/Banners");
        names.put("net/minecraft/world/item/ItemStack",fixture+"Stack");names.put("net/minecraft/world/item/Item",fixture+"Item");names.put("net/minecraft/world/item/BannerItem",fixture+"Banner");names.put("net/minecraft/world/item/DyeColor",fixture+"Color");
        names.put("net/minecraft/world/level/block/entity/BannerBlockEntity",fixture+"BannerNbt");names.put("net/minecraft/server/level/ServerPlayer",fixture+"Player");names.put("com/talhanation/recruits/entities/AbstractRecruitEntity",fixture+"Recruit");
        names.put("ua/townyrecruits/MilitaryData",fixture+"Data");names.put("ua/townyrecruits/MoraleSystem",fixture+"Morale");
        ClassNode node=new ClassNode();try(var in=BannerAssignmentRuntimeTest.class.getClassLoader().getResourceAsStream(source+".class")){new ClassReader(in.readAllBytes()).accept(node,0);}
        node.methods.removeIf(m->!Set.of("equip","from","design","fallback").contains(m.name));node.fields.clear();node.nestMembers=null;
        ClassWriter writer=new ClassWriter(0);node.accept(new ClassRemapper(writer,new SimpleRemapper(names)));return new Loader().define(writer.toByteArray()).getMethod("equip",Data.class,UUID.class,Player.class,Recruit.class);
    }
    static void rejected(java.lang.reflect.Method equip,Data data,UUID regiment,Player player,Recruit r)throws Exception {
        try{equip.invoke(null,data,regiment,player,r);throw new AssertionError("Invalid banner transaction accepted");}catch(InvocationTargetException e){assert e.getCause() instanceof IllegalArgumentException:e.getCause();}
    }
    public static void main(String[] args)throws Exception {
        var equip=equip();Data data=new Data();UUID regiment=UUID.randomUUID();data.banners.put(regiment,new Design(12,List.of(new Layer("bs",2))));
        ListTag patterns=new ListTag();CompoundTag bottom=new CompoundTag();bottom.m_128359_("Pattern","bs");bottom.m_128405_("Color",2);patterns.add(bottom);
        Player player=new Player();player.hand=new Stack(new Banner(12),2,patterns);Recruit first=new Recruit();
        equip.invoke(null,data,regiment,player,first);assert player.hand.count==1&&first.off.count==1&&data.bearers.get(regiment).equals(first.id);
        equip.invoke(null,data,regiment,player,first);assert player.hand.count==1&&first.off.count==1;
        Recruit next=new Recruit();equip.invoke(null,data,regiment,player,next);assert player.hand.count==0&&first.off.count+next.off.count==2&&data.bearers.get(regiment).equals(next.id);
        Recruit shield=new Recruit();shield.off=new Stack(new Item(),1);player.hand=new Stack(new Banner(12),1,patterns);rejected(equip,data,regiment,player,shield);
        assert shield.off.count==1&&player.hand.count==1&&data.bearers.get(regiment).equals(next.id);
        Recruit mismatch=new Recruit();player.hand=new Stack(new Banner(3),1,patterns);rejected(equip,data,regiment,player,mismatch);assert mismatch.off.count==0&&player.hand.count==1;
        Recruit routed=new Recruit();routed.routed=true;player.hand=new Stack(new Banner(12),1,patterns);rejected(equip,data,regiment,player,routed);
        assert routed.off.count==0&&player.hand.count==1&&first.main.count==1&&first.helmet.count==1;
        System.out.println("PASS executed production banner transaction: real-item conservation, matching heraldry, no duplicate supply, reassigning leaves the old flag, shield/weapon/armor preservation, mismatch and rout rejection");
    }
}
