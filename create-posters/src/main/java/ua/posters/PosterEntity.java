package ua.posters;

import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraftforge.entity.IEntityAdditionalSpawnData;
import net.minecraftforge.network.NetworkHooks;

public final class PosterEntity extends HangingEntity implements IEntityAdditionalSpawnData {
    private static final EntityDataAccessor<CompoundTag> DRAWING=SynchedEntityData.m_135353_(PosterEntity.class,EntityDataSerializers.f_135042_);
    public PosterEntity(EntityType<? extends PosterEntity> type,Level level){super(type,level);}
    public PosterEntity(Level level,BlockPos pos,Direction direction,CompoundTag drawing){
        super(CreatePosters.POSTER.get(),level,pos);f_19804_.m_135381_(DRAWING,drawing.m_6426_());m_6022_(direction);
    }
    @Override protected void m_8097_(){super.m_8097_();f_19804_.m_135372_(DRAWING,new CompoundTag());}
    public CompoundTag drawing(){return f_19804_.m_135370_(DRAWING);}
    @Override public int m_7076_(){return 48;}
    @Override public int m_7068_(){return 32;}
    @Override public void m_7380_(CompoundTag tag){super.m_7380_(tag);tag.m_128365_("Drawing",drawing());tag.m_128405_("Facing",f_31699_.m_122416_());}
    @Override public void m_7378_(CompoundTag tag){super.m_7378_(tag);CompoundTag d=tag.m_128469_("Drawing");if(DrawingData.valid(d))f_19804_.m_135381_(DRAWING,d);m_6022_(Direction.m_122407_(tag.m_128451_("Facing")));}
    public ItemStack stack(){ItemStack s=new ItemStack(CreatePosters.ITEM.get());s.m_41784_().m_128365_("Drawing",drawing().m_6426_());return s;}
    @Override public ItemStack m_142340_(){return stack();}
    @Override public void m_5553_(Entity breaker){
        if(!m_9236_().m_46469_().m_46207_(GameRules.f_46137_))return;
        if(breaker instanceof Player p && p.m_150110_().f_35937_)return;
        m_5552_(stack(),0);
    }
    @Override public void m_7084_(){}
    @Override public Packet<ClientGamePacketListener> m_5654_(){return NetworkHooks.getEntitySpawningPacket(this);}
    @Override public void writeSpawnData(FriendlyByteBuf b){b.m_130064_(f_31698_);b.writeByte(f_31699_.m_122416_());b.m_130079_(drawing());}
    @Override public void readSpawnData(FriendlyByteBuf b){f_31698_=b.m_130135_();int facing=b.readUnsignedByte();CompoundTag d=b.m_130260_();if(d!=null&&DrawingData.valid(d))f_19804_.m_135381_(DRAWING,d);m_6022_(Direction.m_122407_(facing));}
}
