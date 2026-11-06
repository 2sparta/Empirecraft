package guardtest;

import java.lang.reflect.Proxy;
import java.util.*;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.*;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.display.*;
import journeymap.api.v2.client.model.MapImage;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import ua.townyrecruits.RegimentalBanners.Design;
import ua.townyrecruits.client.ArmyMapClient;
import ua.townyrecruits.client.JourneyMapArmyLayer;
import ua.townyrecruits.map.ArmyMapData.*;

/** Real JourneyMap overlay/model types and production synchronization, without a graphics context. */
public final class JourneyMapArmyLayerTest {
    static Unit unit(UUID id,double x,UUID reg,boolean bearer,Relation relation){return new Unit(id,new Vec3(x,65,0),90,relation,Kind.PIKE,reg,"Nation","Company","Commander",bearer,false);}
    /** Only registry boot is isolated: a genuine dimension key without a Forge launcher/AT bootstrap. */
    public static final class Dimensions {
        public static final net.minecraft.resources.ResourceKey<net.minecraft.core.Registry<net.minecraft.world.level.Level>> f_256858_=key();
        @SuppressWarnings("unchecked") static net.minecraft.resources.ResourceKey<net.minecraft.core.Registry<net.minecraft.world.level.Level>> key() {
            try {var ctor=net.minecraft.resources.ResourceKey.class.getDeclaredConstructor(ResourceLocation.class,ResourceLocation.class);ctor.setAccessible(true);
                return (net.minecraft.resources.ResourceKey<net.minecraft.core.Registry<net.minecraft.world.level.Level>>)ctor.newInstance(new ResourceLocation("minecraft:root"),new ResourceLocation("minecraft:dimension"));
            } catch(ReflectiveOperationException e){throw new AssertionError(e);}
        }
    }
    static final class Loader extends ClassLoader {Class<?> define(byte[] bytes){return defineClass(null,bytes,0,bytes.length);}}
    static ArmyMapClient.Layer layer(IClientAPI api,JourneyMapArmyLayer.Icons icons)throws Exception {
        String source="ua/townyrecruits/client/JourneyMapArmyLayer";ClassNode node=new ClassNode();
        try(var in=JourneyMapArmyLayerTest.class.getClassLoader().getResourceAsStream(source+".class")){new ClassReader(in.readAllBytes()).accept(node,0);}
        node.nestMembers=null;ClassWriter writer=new ClassWriter(0);
        node.accept(new ClassRemapper(writer,new SimpleRemapper(Map.of(source,"guardtest/generated/ArmyLayer","net/minecraft/core/registries/Registries","guardtest/JourneyMapArmyLayerTest$Dimensions"))));
        return (ArmyMapClient.Layer)new Loader().define(writer.toByteArray()).getConstructor(IClientAPI.class,JourneyMapArmyLayer.Icons.class,java.util.function.Function.class)
            .newInstance(api,icons,(java.util.function.Function<String,String>)(k->k));
    }
    public static void main(String[] args)throws Exception {
        Map<String,Displayable> shown=new HashMap<>();int[] clear={0};
        IClientAPI api=(IClientAPI)Proxy.newProxyInstance(IClientAPI.class.getClassLoader(),new Class[]{IClientAPI.class},(p,m,a)->{
            return switch(m.getName()) {
                case "show"->{var overlay=(Displayable)a[0];shown.put(overlay.getGuid(),overlay);yield null;}
                case "remove"->{shown.remove(((Displayable)a[0]).getGuid());yield null;}
                case "removeAll"->{shown.values().removeIf(d->d.getModId().equals(a[0]));yield null;}
                case "toString"->"JourneyMap API fixture";
                default->throw new AssertionError("Unexpected JourneyMap mutation: "+m.getName());
            };
        });
        var foreign=new MarkerOverlay("empirecraft_townymap",new net.minecraft.core.BlockPos(0,64,0),new MapImage(new ResourceLocation("test:foreign.png"),16,16));shown.put(foreign.getGuid(),foreign);
        JourneyMapArmyLayer.Icons icons=new JourneyMapArmyLayer.Icons(){
            public MapImage unit(Unit unit){return new MapImage(new ResourceLocation("test:unit.png"),18,18).setColor(unit.relation().color);}
            public MapImage flag(Style style,Relation relation){return new MapImage(new ResourceLocation("test:flag.png"),28,32).setColor(relation.color);}
            public void clear(){clear[0]++;}
        };
        var layer=layer(api,icons);UUID regiment=UUID.randomUUID(),id=UUID.randomUUID();var dim=new ResourceLocation("minecraft:overworld");
        Style style=new Style(regiment,"First_Regiment",new Design(12,List.of()));
        List<Unit> units=List.of(unit(id,1,regiment,false,Relation.ALLY),unit(UUID.randomUUID(),3,regiment,false,Relation.ALLY),unit(UUID.randomUUID(),1000,regiment,true,Relation.ALLY));
        layer.update(new Snapshot(dim,1,List.of(style),units));assert shown.size()==5;
        MarkerOverlay first=shown.values().stream().filter(d->d instanceof MarkerOverlay m&&m.getDisplayOrder()==90&&m.getPoint().m_123341_()==1).map(d->(MarkerOverlay)d).findFirst().orElseThrow();String guid=first.getGuid();
        MarkerOverlay flag=shown.values().stream().filter(d->d.getDisplayOrder()==100).map(d->(MarkerOverlay)d).findFirst().orElseThrow();
        assert flag.getPoint().m_123341_()==1000 : "A visible standard bearer determines the flag position";
        assert (first.getIcon().getColor()&0xffffff)==Relation.ALLY.color && first.getTitle().contains("Commander");
        layer.update(new Snapshot(dim,2,List.of(style),List.of(unit(id,7,regiment,false,Relation.ENEMY))));
        assert shown.size()==3 && shown.containsKey(guid);assert ((MarkerOverlay)shown.get(guid)).getPoint().m_123341_()==7;
        assert (((MarkerOverlay)shown.get(guid)).getIcon().getColor()&0xffffff)==Relation.ENEMY.color;
        var anchor=JourneyMapArmyLayer.flagAnchor(List.of(unit(id,1,regiment,false,Relation.ALLY),unit(UUID.randomUUID(),2,regiment,false,Relation.ALLY),unit(UUID.randomUUID(),1000,regiment,false,Relation.ALLY)));
        assert anchor.position().f_82479_<32 : "A split regiment cannot gain a fictitious midpoint flag";
        layer.update(new Snapshot(dim,3,List.of(),List.of()));assert shown.size()==1&&shown.containsKey(foreign.getGuid());
        layer.clear();assert clear[0]==1&&shown.size()==1;layer.reload();assert clear[0]==2&&shown.size()==1;
        System.out.println("PASS production JourneyMap layer with real API objects: stable moving markers, diplomacy recoloring, actual bearer/visible troop flags, disappearance, scoped cleanup, reload and untouched Towny overlays/waypoints");
    }
}
