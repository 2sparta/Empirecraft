package com.quintonc.vs_sails.compat;

import com.quintonc.vs_sails.blocks.SailBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;
import java.lang.reflect.*;

/** Copycats 3 and Create's original copycats, without a mandatory Copycats dependency. */
public final class CopycatMaterials {
    private CopycatMaterials() {}
    public record Part(String key,BlockState material,double volume) {}
    private static final Class<?> PHYSICS = physics();
    private static Class<?> physics() {
        try{return Class.forName("ua.empirecraft.shipphysics.BlockProperties");}
        catch(ClassNotFoundException e){return null;}
    }
    public static double mass(Level level,BlockPos pos,BlockState state,double fallback) {
        if(PHYSICS==null || !isCopycat(state))return fallback;
        try {
            Object properties=PHYSICS.getMethod("at",net.minecraft.world.level.BlockGetter.class,BlockPos.class,BlockState.class).invoke(null,level,pos,state);
            return (double)properties.getClass().getMethod("mass").invoke(properties);
        }catch(ReflectiveOperationException e){throw new IllegalStateException("Cannot read Ship Physics copycat mass",e);}
    }
    public static boolean isCopycat(BlockState state) {
        var id=BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return id.getNamespace().equals("copycats") || id.getNamespace().equals("create") && id.getPath().startsWith("copycat_");
    }
    public static List<Part> parts(Level level,BlockPos pos,BlockState state) {
        if(!isCopycat(state))return List.of();
        BlockEntity be=level.getBlockEntity(pos);if(be==null)return List.of();
        if(PHYSICS!=null)try {
            List<?> values=(List<?>)PHYSICS.getMethod("parts",net.minecraft.world.level.BlockGetter.class,BlockPos.class,BlockState.class).invoke(null,level,pos,state);
            List<Part> parts=new ArrayList<>();
            for(Object value:values) {
                Class<?> type=value.getClass();
                parts.add(new Part((String)type.getMethod("key").invoke(value),(BlockState)type.getMethod("material").invoke(value),(double)type.getMethod("volume").invoke(value)));
            }
            return List.copyOf(parts);
        }catch(ReflectiveOperationException e){throw new IllegalStateException("Cannot read Ship Physics copycat parts",e);}
        var decoded=decode(be.saveWithoutMetadata()).stream().filter(p->exists(state,p.key)).toList();
        double volume=0;for(var box:state.getShape(level,pos).toAabbs())volume+=box.getXsize()*box.getYsize()*box.getZsize();
        double fraction=Math.max(0,Math.min(1,volume))/Math.max(1,decoded.size());
        return decoded.stream().map(p->new Part(p.key,p.material,fraction)).toList();
    }
    public static List<Part> decode(CompoundTag data) {
        List<Part> result=new ArrayList<>();
        if(data.contains("material_data",Tag.TAG_COMPOUND)) {
            CompoundTag parts=data.getCompound("material_data");
            for(String key:parts.getAllKeys())add(result,key,parts.getCompound(key).getCompound("material"));
        }else if(data.contains("Material",Tag.TAG_COMPOUND))add(result,"",data.getCompound("Material"));
        return List.copyOf(result);
    }
    private static void add(List<Part> out,String key,CompoundTag data) {
        if(!data.contains("Name",Tag.TAG_STRING))return;
        BlockState material=NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(),data);
        var id=BuiltInRegistries.BLOCK.getKey(material.getBlock());
        if(!material.isAir() && !(id.getNamespace().equals("create") && id.getPath().equals("copycat_base")))out.add(new Part(key,material,1));
    }
    private static boolean exists(BlockState state,String key) {
        if(key.isEmpty())return true;
        try{return (boolean)state.getBlock().getClass().getMethod("partExists",BlockState.class,String.class).invoke(state.getBlock(),state,key);}
        catch(NoSuchMethodException e){return false;}
        catch(ReflectiveOperationException e){throw new IllegalStateException("Cannot inspect Copycats part",e);}
    }
    public static boolean allSailsFurled(net.minecraft.world.level.BlockGetter world,BlockPos pos,BlockState state) {
        BlockEntity be=world.getBlockEntity(pos);if(be==null)return false;
        CompoundTag tag=be.saveWithoutMetadata();
        List<Part> parts=decode(tag).stream().filter(p->exists(state,p.key)).toList();
        if(parts.isEmpty())return false;
        if(tag.contains("material_data",Tag.TAG_COMPOUND)) {
            long present=tag.getCompound("material_data").getAllKeys().stream().filter(key->exists(state,key)).count();
            if(present!=parts.size())return false;
        }
        return parts.stream().allMatch(p->p.material.getBlock() instanceof SailBlock && !p.material.getValue(SailBlock.SET));
    }
    public static boolean setSails(Level level,BlockPos pos,BlockState state,boolean open) {
        var parts=parts(level,pos,state);BlockEntity be=level.getBlockEntity(pos);boolean changed=false;
        if(be==null)return false;
        for(Part part:parts)if(part.material.getBlock() instanceof SailBlock) {
            BlockState next=part.material.setValue(SailBlock.SET,open).setValue(SailBlock.INVISIBLE,!open);
            if(next==part.material)continue;
            try {
                if(!be.saveWithoutMetadata().contains("material_data",Tag.TAG_COMPOUND))be.getClass().getMethod("setMaterial",BlockState.class).invoke(be,next);
                else be.getClass().getMethod("setMaterial",String.class,BlockState.class).invoke(be,part.key,next);
                changed=true;
            }catch(ReflectiveOperationException e){throw new IllegalStateException("Cannot update Copycats sail material",e);}
        }
        if(changed){be.setChanged();level.sendBlockUpdated(pos,state,state,3);}
        return changed;
    }
}
