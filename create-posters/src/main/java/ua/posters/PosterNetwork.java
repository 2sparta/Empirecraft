package ua.posters;

import java.util.*;
import java.util.function.*;
import com.simibubi.create.content.schematics.SchematicItem;
import com.simibubi.create.content.schematics.table.SchematicTableMenu;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;

public final class PosterNetwork {
    public static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(new ResourceLocation(CreatePosters.ID,"main"),()->"1","1"::equals,"1"::equals);
    private static final Map<ServerPlayer,Long> LAST=new WeakHashMap<>();
    public record Request(int container,int serial,int mask,String title,boolean print){
        static Request decode(FriendlyByteBuf b){return new Request(b.readInt(),b.readInt(),b.readUnsignedByte(),b.m_130136_(64),b.readBoolean());}
        void encode(FriendlyByteBuf b){b.writeInt(container);b.writeInt(serial);b.writeByte(mask);b.m_130072_(title,64);b.writeBoolean(print);}
    }
    public record Result(int container,int serial,String error,boolean printed,CompoundTag drawing){
        static Result decode(FriendlyByteBuf b){return new Result(b.readInt(),b.readInt(),b.m_130136_(32),b.readBoolean(),b.m_130260_());}
        void encode(FriendlyByteBuf b){b.writeInt(container);b.writeInt(serial);b.m_130072_(error,32);b.writeBoolean(printed);b.m_130079_(drawing);}
    }
    public static void register(){
        CHANNEL.registerMessage(0,Request.class,Request::encode,Request::decode,(r,c)->{
            var ctx=c.get();ctx.enqueueWork(()->handle(ctx.getSender(),r));ctx.setPacketHandled(true);
        },Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(1,Result.class,Result::encode,Result::decode,(r,c)->{
            var ctx=c.get();ctx.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->PosterClient.result(r)));ctx.setPacketHandled(true);
        },Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
    private static void reply(ServerPlayer p,Request r,String error,boolean printed,CompoundTag tag){CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new Result(r.container,r.serial,error,printed,tag));}
    private static void handle(ServerPlayer p,Request r){
        if(p==null)return;
        if(!(p.f_36096_ instanceof SchematicTableMenu menu)||menu.f_38840_!=r.container||!menu.m_6875_(p)||!menu.contentHolder.canPlayerUse(p)){reply(p,r,"table",false,null);return;}
        if(menu.contentHolder.isUploading){reply(p,r,"upload",false,null);return;}
        long now=System.nanoTime();Long last=LAST.get(p);
        if(last!=null&&now-last<400_000_000L){reply(p,r,"wait",false,null);return;}LAST.put(p,now);
        if(r.mask==0||(r.mask&~63)!=0){reply(p,r,"views",false,null);return;}
        ItemStack source=menu.contentHolder.inventory.getStackInSlot(1);
        if(!(source.m_41720_() instanceof SchematicItem))source=p.m_21205_();
        if(!(source.m_41720_() instanceof SchematicItem))source=p.m_21206_();
        if(!(source.m_41720_() instanceof SchematicItem)){reply(p,r,"schematic",false,null);return;}
        String title=r.title.strip().replaceAll("[\\p{Cntrl}§]","");
        if(title.isBlank()){String file=source.m_41784_().m_128461_("File");title=file.endsWith(".nbt")?file.substring(0,file.length()-4):file;}
        if(title.length()>64)title=title.substring(0,64);
        try{
            CompoundTag drawing=SchematicDrawing.create(source,r.mask,title);
            if(r.print){
                Inventory inv=p.m_150109_();boolean creative=p.m_150110_().f_35937_;
                if(!creative&&(count(inv,Items.f_42516_)<4||count(inv,Items.f_42532_)<1)){reply(p,r,"materials",false,drawing);return;}
                // Reserve a free main-inventory slot before consuming anything.
                if(inv.f_35974_.stream().noneMatch(ItemStack::m_41619_)){reply(p,r,"inventory",false,drawing);return;}
                if(!creative){consume(inv,Items.f_42516_,4);consume(inv,Items.f_42532_,1);}
                ItemStack poster=new ItemStack(CreatePosters.ITEM.get());poster.m_41784_().m_128365_("Drawing",drawing);
                inv.m_36054_(poster);inv.m_6596_();p.f_36096_.m_38946_();
            }
            reply(p,r,"",r.print,drawing);
        }catch(SchematicDrawing.Failure ex){reply(p,r,ex.code,false,null);}
    }
    static int count(Inventory inv,Item item){int n=0;for(ItemStack s:inv.f_35974_)if(s.m_150930_(item))n+=s.m_41613_();return n;}
    static void consume(Inventory inv,Item item,int n){for(ItemStack s:inv.f_35974_)if(s.m_150930_(item)){int take=Math.min(n,s.m_41613_());s.m_41774_(take);n-=take;if(n==0)return;}}
}
