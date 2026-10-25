package guardtest;

import net.minecraft.nbt.CompoundTag;
import ua.empirecraft.calendar.CalendarData;
import ua.empirecraft.calendar.RealTimeline;

public final class CalendarDataTest {
    private static void nativeMethod(String type,String name,String descriptor)throws Exception {
        try(var input=CalendarDataTest.class.getClassLoader().getResourceAsStream(type+".class")){
            assert input!=null:type;
            var node=new org.objectweb.asm.tree.ClassNode();new org.objectweb.asm.ClassReader(input).accept(node,0);
            assert node.methods.stream().anyMatch(m->m.name.equals(name) && m.desc.equals(descriptor)):type+"."+name;
        }
    }
    public static void main(String[] args)throws Exception{
        // Check the exact native accessors without initializing unrelated Minecraft registries/interfaces.
        nativeMethod("net/minecraft/world/item/ItemStack", "m_41720_", "()Lnet/minecraft/world/item/Item;");
        nativeMethod("net/minecraft/world/item/Item", "m_41473_", "()Lnet/minecraft/world/food/FoodProperties;");
        nativeMethod("net/minecraft/world/food/FoodProperties", "m_38744_", "()I");
        long epoch=1_800_000_000_000L;
        var data=new CalendarData();data.migrated=true;
        data.wallClock=RealTimeline.start(epoch,0,1234,false,false).observe(epoch+9999);
        var tag=data.m_7176_(new CompoundTag());var loaded=CalendarData.load(tag);
        assert loaded.migrated && !loaded.invalidClock;
        assert loaded.wallClock.equals(data.wallClock);
        assert loaded.wallClock.project(epoch+4*86_400_000L).equals(data.wallClock.project(epoch+4*86_400_000L));
        tag.m_128356_("wall_epoch",0);assert CalendarData.load(tag).invalidClock;
        tag.m_128405_("wall_clock_schema",2);assert CalendarData.load(tag).invalidClock;
        assert CalendarData.load(new CompoundTag()).wallClock==null;
        System.out.println("PASS actual Minecraft NBT saved epoch, restart projection, legacy data and corrupt/unknown schema rejection");
    }
}
