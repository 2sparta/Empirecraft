package ua.empirecraft.townymap.contact;

import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;

/** Actual Contact 1.2.3 API. Reflection keeps Contact optional and never crosses client classes. */
public final class ContactMail {
    public static final String KEY="EmpirecraftFairMail";
    private static final String INSTANCE="EmpirecraftFairMailboxInstance";
    private static final Set<ServerLevel> BOX_WRITES=Collections.newSetFromMap(new IdentityHashMap<>());
    private ContactMail(){}
    private static final class API {
        static final Class<?> DATA=type("com.flechazo.contact.common.storage.PlayerMailboxData"),BOX=type("com.flechazo.contact.common.tileentity.MailboxBlockEntity");
        static final Method GET=method(type("com.flechazo.contact.platform.PlatformHelper"),"data"),POS=method(DATA,"getMailboxPos",UUID.class),OWNER=method(DATA,"getMailboxOwner",ResourceKey.class,BlockPos.class),ADD=method(DATA,"addMailboxContents",UUID.class,ItemStack.class),UPDATE=method(type("com.flechazo.contact.common.handler.MailboxManager"),"updateState",UUID.class,DATA);
        static Class<?> type(String name){try{return Class.forName(name);}catch(ClassNotFoundException ex){throw new IllegalStateException("Contact 1.2.3 is unavailable",ex);}}
        static Method method(Class<?> type,String name,Class<?>...args){try{return type.getMethod(name,args);}catch(ReflectiveOperationException ex){throw new IllegalStateException("Unsupported Contact API: "+name,ex);}}
    }
    static Object invoke(Method method,Object target,Object...args){try{return method.invoke(target,args);}catch(ReflectiveOperationException ex){throw new IllegalStateException("Contact call failed: "+method.getName(),ex);}}
    public static Object data(){Object data=invoke(API.GET,null);if(!(data instanceof FairMailboxData))throw new IllegalStateException("Contact receipt mixin is unavailable");return data;}
    static CompoundTag child(CompoundTag parent,String key){if(!parent.contains(key,Tag.TAG_COMPOUND))parent.put(key,new CompoundTag());return parent.getCompound(key);}
    static CompoundTag root(Object data){return ((FairMailboxData)data).empirecraft$fairMail();}
    public static void bindingChanged(Object data,UUID owner){child(root(data),"Bindings").remove(owner.toString());}
    static GlobalPos position(Object data,UUID owner){return (GlobalPos)invoke(API.POS,data,owner);}
    static String address(GlobalPos pos){return pos.dimension().location()+";"+pos.pos().getX()+";"+pos.pos().getY()+";"+pos.pos().getZ();}
    static CompoundTag binding(Object data,UUID owner,GlobalPos pos,MinecraftServer server){
        CompoundTag bindings=child(root(data),"Bindings"),entry=child(bindings,owner.toString());String address=address(pos);
        if(!address.equals(entry.getString("Address"))||!entry.hasUUID("Id")){entry=new CompoundTag();entry.putUUID("Id",UUID.randomUUID());entry.putString("Address",address);bindings.put(owner.toString(),entry);}
        var level=server.getLevel(pos.dimension());
        if(level!=null&&level.hasChunkAt(pos.pos())){
            var box=level.getBlockEntity(pos.pos());
            if(API.BOX.isInstance(box)){
                CompoundTag persistent=box.getPersistentData();if(!persistent.hasUUID(INSTANCE)){persistent.putUUID(INSTANCE,UUID.randomUUID());box.setChanged();BOX_WRITES.add(level);}
                String instance=persistent.getUUID(INSTANCE).toString(),previous=entry.getString("Instance");
                if(!previous.isEmpty()&&!previous.equals(instance))entry.putUUID("Id",UUID.randomUUID());
                entry.putString("Instance",instance);
            }
        }
        return entry;
    }
    public static List<Map<String,Object>> snapshot(MinecraftServer server){
        Object data=data();Set<UUID> owners=new TreeSet<>();
        try{owners.addAll(((Map<UUID,?>)API.DATA.getField("uuidToContents").get(data)).keySet());owners.addAll(((Map<?,UUID>)API.DATA.getField("nameToUUID").get(data)).values());}catch(ReflectiveOperationException ex){throw new IllegalStateException(ex);}
        var result=new ArrayList<Map<String,Object>>();for(UUID owner:owners){var pos=position(data,owner);if(pos==null||!owner.equals(invoke(API.OWNER,data,pos.dimension(),pos.pos())))continue;
            var level=server.getLevel(pos.dimension());if(level!=null&&level.hasChunkAt(pos.pos())&&!API.BOX.isInstance(level.getBlockEntity(pos.pos())))continue;
            var binding=binding(data,owner,pos,server);result.add(Map.of("person",owner.toString(),"address",address(pos),"generation",binding.getUUID("Id").toString(),"instance",binding.getString("Instance")));
        }
        flush(server);var persisted=persisted(server).getCompound("Bindings");
        for(var target:result)if(!target.get("generation").equals(persisted.getCompound(target.get("person").toString()).getUUID("Id").toString()))throw new IllegalStateException("Contact address did not reach disk");
        return result;
    }
    static void flush(MinecraftServer server){for(var level:BOX_WRITES)if(level.getServer()==server)level.getChunkSource().save(false);BOX_WRITES.clear();server.overworld().getDataStorage().save();}
    static CompoundTag persisted(MinecraftServer server){
        var file=server.getWorldPath(LevelResource.ROOT).resolve("data/capabilities.dat").toFile();
        try{return NbtIo.readCompressed(file).getCompound("data").getCompound("contact:mailbox_data").getCompound(KEY);}catch(IOException ex){throw new IllegalStateException("Contact storage could not be verified",ex);}
    }
    public static ItemStack card(String sender,String sample,String text){
        if(text.isBlank()||text.length()>16000)throw new IllegalArgumentException("Invalid fair invitation length");
        var item=ForgeRegistries.ITEMS.getValue(new ResourceLocation("contact","postcard"));if(item==null)throw new IllegalStateException("Contact postcard unavailable");
        ItemStack stack=new ItemStack(item);var tag=stack.getOrCreateTag();tag.putString("Sender",sender);tag.putString("Text",text);tag.putString("EmpirecraftFairSample",sample);
        stack.setHoverName(Component.literal("Запрошення на ярмарок"));return stack;
    }
    public static String encode(ItemStack item){try{var out=new ByteArrayOutputStream();NbtIo.writeCompressed(item.save(new CompoundTag()),out);return Base64.getEncoder().encodeToString(out.toByteArray());}catch(IOException ex){throw new IllegalStateException(ex);}}
    static ItemStack decode(String encoded){try{if(encoded.length()>262144)throw new IllegalArgumentException("Oversized postcard");return ItemStack.of(NbtIo.readCompressed(new ByteArrayInputStream(Base64.getDecoder().decode(encoded))));}catch(IOException ex){throw new IllegalStateException(ex);}}
    static String digest(Map<String,Object> job){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((job.get("person")+"|"+job.get("address")+"|"+job.get("generation")+"|"+job.get("instance")+"|"+job.get("template")).getBytes(StandardCharsets.UTF_8)));}catch(Exception ex){throw new IllegalStateException(ex);}}
    public static Map<String,Object> deliver(MinecraftServer server,List<Map<String,Object>> jobs){
        if(jobs.size()>8)throw new IllegalArgumentException("Oversized mail batch");
        Object data=data();CompoundTag receipts=child(root(data),"Receipts");var result=new LinkedHashMap<String,Object>();
        for(var job:jobs){String id=job.get("id").toString();UUID.fromString(id);String digest=digest(job);
            if(receipts.contains(id)){if(!digest.equals(receipts.getString(id)))throw new IllegalStateException("Mail job ID reused with different terms");result.put(id,"DELIVERED");continue;}
            if(!Boolean.TRUE.equals(job.get("send"))){result.put(id,"CANCELLED");continue;}
            UUID owner=UUID.fromString(job.get("person").toString());var pos=position(data,owner);
            if(pos==null||!address(pos).equals(job.get("address"))||!owner.equals(invoke(API.OWNER,data,pos.dimension(),pos.pos()))){result.put(id,"STALE");continue;}
            var binding=binding(data,owner,pos,server);
            if(!binding.getUUID("Id").toString().equals(job.get("generation"))||!job.get("instance").toString().isEmpty()&&!binding.getString("Instance").equals(job.get("instance"))){result.put(id,"STALE");continue;}
            var level=server.getLevel(pos.dimension());if(level==null||!level.hasChunkAt(pos.pos())){result.put(id,"WAITING");continue;}
            if(!API.BOX.isInstance(level.getBlockEntity(pos.pos()))){bindingChanged(data,owner);result.put(id,"STALE");continue;}
            ItemStack item=decode(job.get("template").toString());if(!new ResourceLocation("contact","postcard").equals(ForgeRegistries.ITEMS.getKey(item.getItem())))throw new IllegalArgumentException("Not a Contact postcard");
            item.getOrCreateTag().remove("EmpirecraftFairSample");item.getOrCreateTag().putString("EmpirecraftFairDelivery",id);
            if(!Boolean.TRUE.equals(invoke(API.ADD,data,owner,item))){result.put(id,"WAITING");continue;}
            receipts.putString(id,digest);invoke(API.UPDATE,null,owner,data);result.put(id,"DELIVERED");
        }
        // Forge's LevelCapabilityData is always dirty. Contact contents and these
        // receipts share capabilities.dat; flush once per batch, before SQL ACK.
        flush(server);
        if(result.containsValue("DELIVERED")){
            CompoundTag saved=persisted(server).getCompound("Receipts");
            for(var job:jobs)if("DELIVERED".equals(result.get(job.get("id")))&&!digest(job).equals(saved.getString(job.get("id").toString())))throw new IllegalStateException("Contact delivery is awaiting durable acknowledgement");
        }
        return result;
    }
    public static Map<String, Object> handleNative(Map<String, Object> request) {
        if (!"template".equals(request.get("action"))) return handle(request);
        ItemStack card = requestedCard(server(), request);
        return Map.of("item", card, "template", encode(card));
    }

    private static MinecraftServer server() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || !server.isSameThread() || !ModList.get().isLoaded("contact")) {
            throw new IllegalStateException("Contact is unavailable on this server thread");
        }
        return server;
    }

    private static ItemStack requestedCard(MinecraftServer server, Map<String, Object> request) {
        UUID author = UUID.fromString(request.get("actor").toString());
        var player = server.getPlayerList().getPlayer(author);
        if (player == null) throw new IllegalArgumentException("Invitation author is offline");
        return card(player.getGameProfile().getName(), request.get("sample").toString(), request.get("text").toString());
    }

    public static Map<String,Object> handle(Map<String,Object> request){
        MinecraftServer server = server();
        return switch(request.get("action").toString()){
            case "snapshot"->Map.of("recipients",snapshot(server));
            case "template"->{
                ItemStack card = requestedCard(server, request);
                try{Object bukkit=Class.forName("org.bukkit.Bukkit").getMethod("getServer").invoke(null);Object item=Class.forName(bukkit.getClass().getPackageName()+".inventory.CraftItemStack").getMethod("asBukkitCopy",ItemStack.class).invoke(null,card);yield Map.of("item",item,"template",encode(card));}catch(ReflectiveOperationException ex){throw new IllegalStateException("Bukkit item bridge unavailable",ex);}}
            case "deliver"->deliver(server,(List<Map<String,Object>>)request.get("jobs"));
            default->throw new IllegalArgumentException("Unknown Contact operation");
        };
    }
}
