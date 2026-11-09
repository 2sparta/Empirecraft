package ua.empirecraft.armor;

import java.util.*;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;

public final class ArmorWear {
    private ArmorWear() {}
    public static ItemStack damageInner(Player player,ItemStack outer,DamageSource source,float amount) {
        if(amount<=0||!LayerData.layered(outer))return outer;
        var all=LayerData.pieces(outer);var retained=new ArrayList<ItemStack>();
        for(int i=0;i<all.size()-1;i++) {
            var inner=all.get(i);
            if(!source.is(DamageTypeTags.IS_FIRE)||!inner.getItem().isFireResistant())
                inner.hurtAndBreak(Math.max(1,(int)(amount/4)),player,p->p.broadcastBreakEvent(LayerData.slot(inner)));
            if(!inner.isEmpty())retained.add(inner);
        }
        retained.add(LayerData.plain(outer));
        // Keep the actual outer reference: vanilla will damage or destroy it next.
        var encoded=LayerData.wrap(retained);
        if(encoded.hasTag()&&encoded.getTag().contains(LayerData.KEY))outer.getOrCreateTag().put(LayerData.KEY,encoded.getTag().get(LayerData.KEY).copy());
        else if(outer.hasTag())outer.getTag().remove(LayerData.KEY);
        return outer;
    }
    public static ItemStack afterOuterBreak(ItemStack previous) {
        var all=LayerData.pieces(previous);if(!all.isEmpty())all.remove(all.size()-1);return LayerData.wrap(all);
    }
}
