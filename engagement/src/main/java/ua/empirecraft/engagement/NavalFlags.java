package ua.empirecraft.engagement;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.*;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import com.quintonc.vs_sails.protection.ShipProtection;
import static ua.empirecraft.engagement.CombatPolicy.*;

public final class NavalFlags {
    private static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(ForgeRegistries.BLOCKS,EngagementMod.ID);
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(ForgeRegistries.ITEMS,EngagementMod.ID);
    private static final DeferredRegister<BlockEntityType<?>> TILES=DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES,EngagementMod.ID);
    public static final RegistryObject<Block> BLOCK=BLOCKS.register("naval_flag",FlagBlock::new);
    public static final RegistryObject<Item> ITEM=ITEMS.register("naval_flag",()->new BlockItem(BLOCK.get(),new Item.Properties()));
    public static final RegistryObject<BlockEntityType<FlagEntity>> TYPE=TILES.register("naval_flag",()->BlockEntityType.Builder.of(FlagEntity::new,BLOCK.get()).build(null));
    private record Valid(long tick,Flag flag){}
    private static final Map<Long,Valid> cache=new HashMap<>();
    private NavalFlags(){}
    public static void register(IEventBus bus){BLOCKS.register(bus);ITEMS.register(bus);TILES.register(bus);bus.addListener((net.minecraftforge.event.BuildCreativeModeTabContentsEvent event)->{if(event.getTabKey().equals(CreativeModeTabs.FUNCTIONAL_BLOCKS))event.accept(ITEM.get());});}
    public static final class FlagBlock extends BaseEntityBlock {
        public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
        FlagBlock(){super(Properties.of().strength(1.5f).noOcclusion());registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH));}
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING);}
        @Override public BlockState getStateForPlacement(BlockPlaceContext context){return defaultBlockState().setValue(FACING,context.getHorizontalDirection().getOpposite());}
        @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new FlagEntity(pos,state);}
        @Override public RenderShape getRenderShape(BlockState state){return RenderShape.ENTITYBLOCK_ANIMATED;}
        @Override public VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return Block.box(6,0,6,10,16,10);}
        @Override public InteractionResult use(BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
            if(level.isClientSide)return InteractionResult.SUCCESS;
            try{var ship=ShipProtection.ship(level,pos);if(ship==null)throw new IllegalArgumentException("Розмістіть морський прапор на щоглі корабля.");
                Government.call("open",Map.of("player",player.getUUID().toString(),"ship",ship.getId()));return InteractionResult.CONSUME;
            }catch(RuntimeException error){player.displayClientMessage(net.minecraft.network.chat.Component.translatable("engagement.flag.error",net.minecraft.network.chat.Component.translatable(Objects.toString(error.getMessage(),""))),true);return InteractionResult.FAIL;}
        }
    }
    public static final class FlagEntity extends BlockEntity {
        public Flag mode=Flag.PIRATE,effective=Flag.PIRATE;public String kind="",payload="",designHash="",nation="";public int width=2;public long ship=-1;
        public FlagEntity(BlockPos pos,BlockState state){super(TYPE.get(),pos,state);}
        @Override public void load(CompoundTag t){super.load(t);try{mode=Flag.valueOf(t.getString("Mode"));}catch(IllegalArgumentException e){mode=Flag.PIRATE;}if(mode==Flag.NONE)mode=Flag.PIRATE;
            try{effective=Flag.valueOf(t.getString("Effective"));}catch(IllegalArgumentException ignored){effective=Flag.PIRATE;}
            kind=t.getString("Kind");payload=t.getString("Payload");designHash=t.getString("Hash");nation=t.getString("Nation");width=Math.max(2,Math.min(8,t.getInt("Width")));ship=t.contains("Ship")?t.getLong("Ship"):-1;}
        @Override protected void saveAdditional(CompoundTag t){super.saveAdditional(t);t.putString("Mode",mode.name());t.putString("Effective",effective.name());t.putString("Kind",kind);t.putString("Payload",payload);t.putString("Hash",designHash);t.putString("Nation",nation);t.putInt("Width",width);t.putLong("Ship",ship);}
        @Override public CompoundTag getUpdateTag(){return saveWithoutMetadata();}
        @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
        @Override public AABB getRenderBoundingBox(){return new AABB(worldPosition).inflate(10);}
        public void changed(){setChanged();if(level!=null){level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);invalidate(level,worldPosition);}}
    }
    public static void invalidate(Level level,BlockPos pos){if(!level.isClientSide){var ship=ShipProtection.ship(level,pos);if(ship!=null)cache.remove(ship.getId());}}
    public static void clear(){cache.clear();}
    public static FlagEntity official(ServerLevel level,LoadedServerShip ship){
        var pos=CombatLedger.get(level.getServer()).flagPositions.get(ship.getId());if(pos==null)return null;BlockPos point=BlockPos.of(pos);
        if(!level.hasChunkAt(point)||!(level.getBlockEntity(point) instanceof FlagEntity flag)||flag.ship!=ship.getId())return null;
        var managing=ShipProtection.ship(level,point);return managing!=null&&managing.getId()==ship.getId()?flag:null;
    }
    public static Flag effective(ServerLevel level,LoadedServerShip ship,Map<String,Object> government){
        long now=EngagementRuntime.now(level);var old=cache.get(ship.getId());if(old!=null&&old.tick==now)return old.flag;
        Flag mode=Flag.PIRATE;var flag=official(level,ship);
        if(flag!=null){
            var b=ship.getShipAABB();int width=CombatPolicy.flagWidth(b.maxX()-b.minX(),b.maxZ()-b.minZ());
            if(flag.width!=width){flag.width=width;flag.changed();}
            boolean state=true;
            if(flag.mode==Flag.STATE){
                Object raw=government.get("design");if(!(raw instanceof Map<?,?> design)||design.isEmpty()||!Objects.equals(government.get("nation"),flag.nation))state=false;
                else if(!Objects.equals(design.get("hash"),flag.designHash)){
                    flag.kind=Objects.toString(design.get("kind"),"");flag.payload=Objects.toString(design.get("payload"),"");flag.designHash=Objects.toString(design.get("hash"),"");flag.changed();
                }
                if(state)state=PaintingDesign.valid(flag.kind,flag.payload,flag.designHash);
            }
            if(state&&visible(level,ship,flag))mode=flag.mode;
        }
        if(flag!=null&&flag.effective!=mode){flag.effective=mode;flag.changed();}cache.put(ship.getId(),new Valid(now,mode));return mode;
    }
    public static boolean visible(ServerLevel level,LoadedServerShip ship,FlagEntity flag){
        Direction forward=flag.getBlockState().getValue(FlagBlock.FACING),right=forward.getClockWise();int height=flag.width/2;Set<BlockPos> visited=new HashSet<>();
        for(int x=1;x<=flag.width;x++)for(int y=0;y<height;y++){
            BlockPos point=flag.getBlockPos().relative(right,x).below(y);var bounds=ship.getShipAABB();boolean own=point.getX()>=bounds.minX()&&point.getX()<bounds.maxX()&&point.getY()>=bounds.minY()&&point.getY()<bounds.maxY()&&point.getZ()>=bounds.minZ()&&point.getZ()<bounds.maxZ();if((own||level.hasChunkAt(point))&&!air(level,point,visited))return false;
            for(Direction direction:new Direction[]{Direction.UP,right,forward,forward.getOpposite()}){
                BlockPos p=point;int distance=0;var b=ship.getShipAABB();
                while(distance++<256){p=p.relative(direction);
                    boolean outside=p.getX()<b.minX()||p.getX()>=b.maxX()||p.getY()>=b.maxY()||p.getZ()<b.minZ()||p.getZ()>=b.maxZ();
                    // VS updates its hull bounds asynchronously. Read already loaded cells
                    // just beyond them too, so a newly placed canopy cannot hide an ensign.
                    if((!outside||level.hasChunkAt(p))&&!air(level,p,visited))return false;
                    if(outside&&distance>=2)break;
                }
                if(distance>=256)return false;
                Vec3 start=CombatRaycast.worldPoint(ship,Vec3.atCenterOf(point)),end=CombatRaycast.worldPoint(ship,Vec3.atCenterOf(p));
                var obstruction=CombatRaycast.firstIgnoring(level,null,start,end,true,s->false,ship.getId());if(obstruction!=null)return false;
            }
        }return true;
    }
    private static boolean air(ServerLevel level,BlockPos pos,Set<BlockPos> seen){
        if(!seen.add(pos))return true;if(seen.size()>16384||!level.hasChunkAt(pos))return false;
        return level.getBlockState(pos).isAir();
    }
    public static FlagEntity selected(ServerLevel level,LoadedServerShip ship,ServerPlayer player){
        var hit=player.pick(8,0,false);if(hit instanceof BlockHitResult block&&level.getBlockEntity(block.getBlockPos()) instanceof FlagEntity flag){
            var managing=ShipProtection.ship(level,flag.getBlockPos());if(managing!=null&&managing.getId()==ship.getId())return flag;
        }
        var flag=official(level,ship);if(flag!=null&&worldDistance(ship,flag.getBlockPos(),player.position())<=64)return flag;
        throw new IllegalArgumentException("Наведіться на морський прапор цього корабля в межах восьми блоків.");
    }
    private static double worldDistance(LoadedServerShip ship,BlockPos pos,Vec3 player){return CombatRaycast.worldPoint(ship,Vec3.atCenterOf(pos)).distanceToSqr(player);}
    public static Map<String,Object> configure(ServerPlayer player,LoadedServerShip ship,Flag mode,boolean apply){
        var level=player.serverLevel();var authority=Government.read(level,"controls",Map.of("player",player.getUUID().toString(),"ship",ship.getId()));
        if(!Boolean.TRUE.equals(authority.get("allow"))||Boolean.TRUE.equals(authority.get("locked")))throw new IllegalArgumentException("Прапор може змінювати лише уповноважений екіпаж корабля, який не виставлено на продаж.");
        var flag=selected(level,ship,player);var government=Government.read(level,"ship",Map.of("ship",ship.getId()));var ledger=CombatLedger.get(level.getServer());
        if(mode!=Flag.PIRATE&&(ledger.active(Combatants.shipKey(ship.getId()),EngagementRuntime.now(level))||ShotOrigins.incoming(level,ship)))throw new IllegalArgumentException("Не можна відновити захист прапором під час бою або наближення снаряда.");
        if(mode==Flag.STATE){var design=(Map<?,?>)government.getOrDefault("design",Map.of());if(design.isEmpty())throw new IllegalArgumentException("Держава ще не затвердила морський прапор.");if(!PaintingDesign.valid(Objects.toString(design.get("kind"),""),Objects.toString(design.get("payload"),""),Objects.toString(design.get("hash"),"")))throw new IllegalArgumentException("Державний прапор недоступний або невидимий.");}
        var b=ship.getShipAABB();int width=CombatPolicy.flagWidth(b.maxX()-b.minX(),b.maxZ()-b.minZ());int before=flag.width;flag.width=width;boolean visible=visible(level,ship,flag);if(!apply)flag.width=before;
        if(!visible&&mode!=Flag.PIRATE)throw new IllegalArgumentException("Звільніть усе полотно, простір зверху й із трьох боків. Прихований прапор вважається піратським.");
        if(apply){
            flag.mode=mode;flag.ship=ship.getId();flag.nation=Objects.toString(government.get("nation"),"");flag.kind="";flag.payload="";flag.designHash="";
            if(mode==Flag.STATE){var design=(Map<?,?>)government.get("design");flag.kind=design.get("kind").toString();flag.payload=design.get("payload").toString();flag.designHash=design.get("hash").toString();}
            flag.effective=visible?mode:Flag.PIRATE;ledger.flagPositions.put(ship.getId(),flag.getBlockPos().asLong());ledger.setDirty();flag.changed();
        }
        return Map.of("mode",mode.name(),"width",width,"height",width/2,"visible",visible,"pos",flag.getBlockPos().asLong(),"nation",Objects.toString(government.get("nation"),""),"design",government.getOrDefault("design",Map.of()));
    }
    public static void tick(ServerLevel level){
        if(EngagementRuntime.now(level)%20!=0)return;
        for(long id:List.copyOf(CombatLedger.get(level.getServer()).flagPositions.keySet())){
            var ship=org.valkyrienskies.mod.common.VSGameUtilsKt.getShipObjectWorld(level).getLoadedShips().getById(id);
            if(ship!=null&&ship.getChunkClaimDimension().equals(org.valkyrienskies.mod.common.VSGameUtilsKt.getDimensionId(level)))try{effective(level,ship,Government.read(level,"ship",Map.of("ship",id)));}catch(RuntimeException unavailable){EngagementRuntime.warn(unavailable);}
        }
    }
}
