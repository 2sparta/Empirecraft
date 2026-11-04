package ua.empirecraft.contactsmoke;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import ua.empirecraft.townymap.contact.ContactMail;
import ua.empirecraft.townymap.contact.FairMailboxData;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/** Two real Mohist boots in contact_smoke.py's disposable world. Never shipped with the mod. */
@Mod("empirecraft_contact_smoke")
public final class ContactSmoke {
    MinecraftServer server;ServerLevel level;int ticks,checks;
    final UUID first=UUID.fromString("9a8cf3d0-9a27-4d28-a93b-f6d39a1b0800"),second=UUID.fromString("9a8cf3d0-9a27-4d28-a93b-f6d39a1b0801"),moved=UUID.fromString("9a8cf3d0-9a27-4d28-a93b-f6d39a1b0802"),replaced=UUID.fromString("9a8cf3d0-9a27-4d28-a93b-f6d39a1b0803"),unloaded=UUID.fromString("9a8cf3d0-9a27-4d28-a93b-f6d39a1b0804");
    public ContactSmoke(){MinecraftForge.EVENT_BUS.addListener(this::started);MinecraftForge.EVENT_BUS.addListener(this::tick);}
    void started(ServerStartedEvent event){server=event.getServer();level=server.overworld();}
    void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    Object data(){return ContactMail.data();}
    Object call(String name,Class<?>[] types,Object...args)throws Exception {return data().getClass().getMethod(name,types).invoke(data(),args);}
    SimpleContainer contents(UUID owner)throws Exception {return (SimpleContainer)call("getMailboxContents",new Class[]{UUID.class},owner);}
    void reset(UUID owner)throws Exception {call("resetMailboxContents",new Class[]{UUID.class},owner);}
    int amount(UUID owner)throws Exception {var contents=contents(owner);int n=0;for(int i=0;i<contents.getContainerSize();i++)if(!contents.getItem(i).isEmpty())n++;return n;}
    BlockPos box(int x){
        var pos=new BlockPos(x,70,0);Block block=ForgeRegistries.BLOCKS.getValue(new ResourceLocation("contact","white_mailbox"));check(block!=null&&block!=Blocks.AIR,"actual Contact mailbox block exists");
        level.setBlock(pos.below(),Blocks.STONE.defaultBlockState(),2);level.setBlock(pos,block.defaultBlockState().setValue(BlockStateProperties.DOUBLE_BLOCK_HALF,DoubleBlockHalf.LOWER),2);
        level.setBlock(pos.above(),block.defaultBlockState().setValue(BlockStateProperties.DOUBLE_BLOCK_HALF,DoubleBlockHalf.UPPER),2);return pos.above();
    }
    void bind(UUID owner,BlockPos top)throws Exception {reset(owner);call("setMailboxData",new Class[]{UUID.class,net.minecraft.resources.ResourceKey.class,BlockPos.class},owner,level.dimension(),top);}
    Map<String,Object> target(List<Map<String,Object>> all,UUID owner){return all.stream().filter(row->owner.toString().equals(row.get("person"))).findFirst().orElseThrow();}
    Map<String,Object> job(Map<String,Object> target,String template){var job=new HashMap<String,Object>(target);job.put("id",UUID.randomUUID().toString());job.put("template",template);job.put("send",true);return job;}
    String send(Map<String,Object> job){return ContactMail.deliver(server,List.of(job)).get(job.get("id")).toString();}
    ItemStack decode(String encoded)throws IOException {return ItemStack.of(NbtIo.readCompressed(new ByteArrayInputStream(Base64.getDecoder().decode(encoded))));}
    String template()throws Exception {
        UUID author=UUID.fromString("fd5c7672-66b9-4c73-b196-32346074e251");var player=FakePlayerFactory.get(level,new GameProfile(author,"FairAuthor"));
        for(var field:net.minecraft.server.players.PlayerList.class.getDeclaredFields())if(Map.class.isAssignableFrom(field.getType())&&field.getGenericType().getTypeName().contains("java.util.UUID")&&field.getGenericType().getTypeName().contains("net.minecraft.server.level.ServerPlayer")){field.setAccessible(true);((Map)field.get(server.getPlayerList())).put(author,player);}
        var result=ContactMail.handle(Map.of("action","template","actor",author.toString(),"sample",UUID.randomUUID().toString(),"text","Запрошуємо на Київський ярмарок!\nТовари, час і місце з фізичного зразка."));
        check(result.get("item")!=null,"native gateway returns the actual Bukkit postcard for the inventory journal");
        var bytes=new ByteArrayOutputStream();
        try(var out=(ObjectOutputStream)Class.forName("org.bukkit.util.io.BukkitObjectOutputStream").getConstructor(OutputStream.class).newInstance(bytes)){out.writeInt(1);out.writeObject(result.get("item"));}
        try(var in=(ObjectInputStream)Class.forName("org.bukkit.util.io.BukkitObjectInputStream").getConstructor(InputStream.class).newInstance(new ByteArrayInputStream(bytes.toByteArray()))){
            check(in.readInt()==1&&result.get("item").equals(in.readObject()),"actual Bukkit inventory journal serialization preserves the complete Contact sample");
        }
        ItemStack nativeCard=decode(result.get("template").toString());
        check(nativeCard.getOrCreateTag().getString("Sender").equals("FairAuthor"),"sender comes from the authenticated server player");
        var method=Class.forName("com.flechazo.contact.common.item.PostcardItem").getMethod("getText",ItemStack.class);
        check(method.invoke(null,nativeCard).toString().contains("Київський ярмарок"),"Contact's actual reader sees the invitation text");
        check(nativeCard.getOrCreateTag().contains("EmpirecraftFairSample"),"physical preview has a unique sample marker");return result.get("template").toString();
    }
    void firstBoot()throws Exception {
        level.setChunkForced(0,0,true);bind(first,box(0));bind(second,box(4));bind(moved,box(8));bind(replaced,box(12));
        ((Map<String,UUID>)data().getClass().getField("nameToUUID").get(data())).put("FirstAlias",first);
        ((Map<String,UUID>)data().getClass().getField("nameToUUID").get(data())).put("SecondAlias",first);
        var all=ContactMail.snapshot(server);check(all.size()==4,"aliases are deduplicated by mailbox owner UUID");check(data() instanceof FairMailboxData,"production Contact persistence mixin is active");
        String template=template();var delivered=job(target(all,first),template);check(send(delivered).equals("DELIVERED"),"real Contact mailbox accepts the invitation");check(amount(first)==1,"exactly one physical postcard is present");
        var received=contents(first).getItem(0);var sample=decode(template);
        check(received.getOrCreateTag().getString("Text").equals(sample.getOrCreateTag().getString("Text"))&&received.getHoverName().equals(sample.getHoverName()),"delivered text and visible item match the physical sample");
        check(!received.getOrCreateTag().contains("EmpirecraftFairSample")&&received.getOrCreateTag().contains("EmpirecraftFairDelivery"),"copy carries the receipt ID instead of an approval token");
        var full=contents(second);for(int i=0;i<24;i++)full.setItem(i,new ItemStack(Items.PAPER));call("setMailboxContents",new Class[]{UUID.class,SimpleContainer.class},second,full);
        var waiting=job(target(all,second),template);check(send(waiting).equals("WAITING"),"full native Contact mailbox defers without claiming delivery");reset(second);check(send(waiting).equals("DELIVERED"),"same job succeeds after freeing the native mailbox");check(amount(second)==1,"full-mailbox retry produces one item");
        var stale=job(target(all,moved),template);bind(moved,box(16));check(send(stale).equals("STALE"),"binding a different mailbox invalidates the old address");check(amount(moved)==0,"moved mailbox receives no stale invitation");
        var destroyed=job(target(all,replaced),template);level.setBlock(new BlockPos(12,71,0),Blocks.AIR.defaultBlockState(),2);box(12);
        check(send(destroyed).equals("STALE"),"replacement at identical coordinates is a different physical mailbox");check(amount(replaced)==0,"replacement receives no stale invitation");
        var far=new BlockPos(2000,71,2000);check(!level.hasChunkAt(far),"remote test chunk starts unloaded");bind(unloaded,far);var distant=target(ContactMail.snapshot(server),unloaded);
        check(send(job(distant,template)).equals("WAITING"),"unloaded mailbox waits");check(!level.hasChunkAt(far),"mail delivery never force-loads the remote chunk");
        CompoundTag saved=new CompoundTag();for(var entry:delivered.entrySet())if(entry.getValue() instanceof String text)saved.putString(entry.getKey(),text);
        NbtIo.writeCompressed(saved,new File("fair-smoke-terms.dat"));
        check(((FairMailboxData)data()).empirecraft$fairMail().getCompound("Receipts").contains(delivered.get("id").toString()),"receipt remains in the same Contact capability as its item");
    }
    void secondBoot()throws Exception {
        CompoundTag saved=NbtIo.readCompressed(new File("fair-smoke-terms.dat"));var job=new HashMap<String,Object>();for(String key:saved.getAllKeys())job.put(key,saved.getString(key));job.put("send",true);
        check(amount(first)==1,"actual second server boot restores the physical postcard");check(((FairMailboxData)data()).empirecraft$fairMail().getCompound("Receipts").contains(job.get("id").toString()),"actual second boot restores its receipt");
        check(send(job).equals("DELIVERED")&&amount(first)==1,"replaying after a real restart does not duplicate delivery");
        reset(first);check(send(job).equals("DELIVERED")&&amount(first)==0,"replaying after collection does not resend a postcard");
        var next=new HashMap<>(job);next.put("id",UUID.randomUUID().toString());check(send(next).equals("DELIVERED"),"same physical mailbox generation remains valid after restart");check(amount(first)==1,"new job still delivers normally");
        var tampered=new HashMap<>(job);tampered.put("template",ContactMail.encode(ContactMail.card("Other","sample","Different text")));
        boolean rejected=false;try{send(tampered);}catch(IllegalStateException expected){rejected=true;}check(rejected&&amount(first)==1,"job ID cannot be replayed with different invitation terms");
    }
    void tick(TickEvent.ServerTickEvent event){
        if(event.phase!=TickEvent.Phase.END||server==null||++ticks!=20)return;
        try{int phase=Integer.getInteger("empirecraft.contact.testPhase",1);if(phase==1)firstBoot();else secondBoot();System.out.println("CONTACT_FAIR_NATIVE_PASS phase="+phase+" assertions="+checks);}
        catch(Throwable ex){System.out.println("CONTACT_FAIR_NATIVE_FAIL");ex.printStackTrace();}
        finally{server.halt(false);server=null;}
    }
}
