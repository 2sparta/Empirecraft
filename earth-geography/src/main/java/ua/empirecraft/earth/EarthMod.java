package ua.empirecraft.earth;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.SaplingGrowTreeEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.registries.ForgeRegistries;
import net.dries007.tfc.util.calendar.Calendars;

@Mod(EarthMod.ID)
public final class EarthMod {
    public static final String ID="empirecraft_earth";
    public static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(ID);
    private static final String DEFAULTS=readDefaults();
    public static volatile Geography data=new Geography(new StringReader(DEFAULTS));
    private static String serverJson=DEFAULTS;
    private static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(new ResourceLocation(ID,"settings"),()->"1","1"::equals,"1"::equals);
    public record SettingsPacket(String json) {}
    public EarthMod(){
        CHANNEL.registerMessage(0,SettingsPacket.class,(p,b)->b.writeUtf(p.json,262144),b->new SettingsPacket(b.readUtf(262144)),(p,s)->{
            var c=s.get();c.enqueueWork(()->data=new Geography(new StringReader(p.json)));c.setPacketHandled(true);
        },Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        new RegionalDeposits();
        var bus=MinecraftForge.EVENT_BUS;
        bus.addListener((net.minecraftforge.event.server.ServerAboutToStartEvent e)->load());
        bus.addListener((net.minecraftforge.event.server.ServerStoppedEvent e)->{data=new Geography(new StringReader(DEFAULTS));serverJson=DEFAULTS;});
        bus.addListener((PlayerEvent.PlayerLoggedInEvent e)->{if(e.getEntity() instanceof ServerPlayer p)sync(p);});
        bus.addListener(EventPriority.LOWEST,this::spawn);
        bus.addListener(EventPriority.LOWEST,(BlockEvent.CropGrowEvent.Pre e)->{
            if(e.getLevel() instanceof ServerLevel l&&!canGrow(l,e.getPos(),ForgeRegistries.BLOCKS.getKey(e.getState().getBlock()).toString()))e.setResult(Event.Result.DENY);
        });
        bus.addListener(EventPriority.LOWEST,(SaplingGrowTreeEvent e)->{
            if(e.getLevel() instanceof ServerLevel l&&!canGrow(l,e.getPos(),ForgeRegistries.BLOCKS.getKey(l.getBlockState(e.getPos()).getBlock()).toString()))e.setResult(Event.Result.DENY);
        });
        bus.addListener((net.minecraftforge.event.entity.player.BonemealEvent e)->{
            if(e.getLevel() instanceof ServerLevel l&&!canGrow(l,e.getPos(),ForgeRegistries.BLOCKS.getKey(e.getBlock().getBlock()).toString())){e.setResult(Event.Result.DENY);e.setCanceled(true);}
        });
        bus.addListener(this::commands);
    }
    private static String readDefaults(){try(var in=EarthMod.class.getResourceAsStream("/earth-defaults.json")){return new String(Objects.requireNonNull(in).readAllBytes(),StandardCharsets.UTF_8);}catch(IOException e){throw new ExceptionInInitializerError(e);}}
    private static void load(){
        Path path=FMLPaths.CONFIGDIR.get().resolve("empirecraft-earth.json");
        try{
            if(!Files.exists(path))Files.writeString(path,DEFAULTS,StandardCharsets.UTF_8);
            String text=Files.readString(path,StandardCharsets.UTF_8);
            var effective=SoilRegionDefaults.merge(text,DEFAULTS);
            String effectiveJson=new Gson().toJson(effective);
            var parsed=new Geography(new StringReader(effectiveJson));
            validateMaterials(parsed);
            if(effectiveJson.length()>262144)throw new IllegalArgumentException("Settings exceed sync limit");
            serverJson=effectiveJson;
            data=parsed;
        }catch(Exception e){throw new IllegalStateException("Invalid Earth configuration: "+path,e);}
    }
    private static void validateMaterials(Geography d){
        Set<String> rocks=new HashSet<>(List.of("granite"));Set<String> ores=new HashSet<>();
        for(var f:d.features){
            if(f.layer().equals("geology"))for(var rock:f.values().getAsJsonArray("rocks")){String r=rock.getAsString();requireBlock("tfc:rock/raw/"+r);rocks.add(r);}
            if(f.layer().equals("ecology"))requireBlock("tfc:dirt/"+f.text("tfc_soil","loam"));
            if(f.layer().equals("deposit"))for(var ore:f.values().getAsJsonArray("ores"))ores.add(ore.getAsString());
        }
        for(String ore:ores)for(String rock:rocks)requireBlock("tfc:ore/"+RegionalDeposits.oreName(ore)+"/"+rock);
    }
    private static void requireBlock(String name){if(!ForgeRegistries.BLOCKS.containsKey(new ResourceLocation(name)))throw new IllegalArgumentException("Missing configured block "+name);}
    private static void sync(ServerPlayer p){CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new SettingsPacket(serverJson));}
    public static boolean enabled(Level l){return data.dimensions.contains(l.dimension().location().toString());}
    public static int month(Level l){return Calendars.get(l).getCalendarMonthOfYear().ordinal();}
    public static double fractionalMonth(Level l){var c=Calendars.get(l);return c.getCalendarMonthOfYear().ordinal()+c.getCalendarFractionOfMonth();}
    public static double temperature(Level l,BlockPos p){return data.temperature(p.getX(),p.getY(),p.getZ(),fractionalMonth(l));}
    public static boolean canGrow(Level l,BlockPos p,String id){return !enabled(l)||data.plantAllowed(id,data.coordinates(p.getX(),p.getZ()),temperature(l,p),month(l));}
    public static String habitat(net.minecraft.world.level.ServerLevelAccessor l,BlockPos p){
        if(!l.getFluidState(p).is(FluidTags.WATER))return "land";
        var biome=l.getBiome(p);
        if(biome.is(BiomeTags.IS_RIVER))return "fresh";
        if(biome.is(BiomeTags.IS_OCEAN))return "marine";
        // Existing map's physical waterline refines broad geographic basin polygons.
        int surface=l.getHeight(Heightmap.Types.WORLD_SURFACE,p.getX(),p.getZ())-1;
        return Math.abs(surface-data.seaLevel)<=2&&data.at("marine",data.coordinates(p.getX(),p.getZ()))!=null?"marine":"fresh";
    }
    private void spawn(MobSpawnEvent.PositionCheck e){
        if(e.getSpawnType()!=MobSpawnType.NATURAL&&e.getSpawnType()!=MobSpawnType.CHUNK_GENERATION&&e.getSpawnType()!=MobSpawnType.EVENT)return;
        var l=e.getLevel().getLevel();if(!enabled(l))return;
        var p=e.getEntity().blockPosition();var point=data.coordinates(p.getX(),p.getZ());
        String id=ForgeRegistries.ENTITY_TYPES.getKey(e.getEntity().getType()).toString();
        String habitat=habitat(e.getLevel(),p);int surface=e.getLevel().getHeight(Heightmap.Types.WORLD_SURFACE,p.getX(),p.getZ())-1;
        boolean allowed=data.aquaticAllowed(id,point,habitat,Math.max(0,surface-p.getY()));
        if(!allowed)e.setResult(Event.Result.DENY);
    }
    private void commands(RegisterCommandsEvent e){
        e.getDispatcher().register(net.minecraft.commands.Commands.literal("empireearth")
            .then(net.minecraft.commands.Commands.literal("here").executes(ctx->{
                var s=ctx.getSource();var pos=BlockPos.containing(s.getPosition());var p=data.coordinates(pos.getX(),pos.getZ());
                var c=data.climate(p);var sea=data.at("marine",p);var rock=data.at("geology",p);
                s.sendSuccess(()->Component.literal(String.format(Locale.ROOT,"%.4f°, %.4f° | %s | %.1f °C | %s / %s | %s | %s | approximate regional data",p.lat(),p.lon(),c.id(),temperature(s.getLevel(),pos),data.zone(p),data.soilClass(p),rock==null?"unmapped geology":rock.label(),habitat(s.getLevel(),pos).equals("marine")&&sea!=null?sea.label():"land/freshwater")),false);return 1;
            }))
            .then(net.minecraft.commands.Commands.literal("reload").requires(s->s.hasPermission(2)).executes(ctx->{
                load();ctx.getSource().getServer().getPlayerList().getPlayers().forEach(EarthMod::sync);
                ctx.getSource().sendSuccess(()->Component.literal("Earth configuration reloaded; reconnect to refresh JourneyMap layer"),false);return 1;
            })));
    }
}
