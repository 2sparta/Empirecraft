package ua.posters;

import java.util.List;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;

public final class PosterItem extends Item {
    public PosterItem(Properties p){super(p);}
    @Override public InteractionResult m_6225_(UseOnContext c){
        Player p=c.m_43723_();Level level=c.m_43725_();Direction face=c.m_43719_();ItemStack s=c.m_43722_();
        if(face.m_122434_()==Direction.Axis.Y)return InteractionResult.FAIL;
        BlockPos pos=c.m_8083_().m_121945_(face);
        if(p==null||!p.m_36204_(pos,face,s))return InteractionResult.FAIL;
        CompoundTag data=s.m_41784_().m_128469_("Drawing");if(!DrawingData.valid(data))return InteractionResult.FAIL;
        if(level.f_46443_)return InteractionResult.SUCCESS;
        PosterEntity poster=new PosterEntity(level,pos,face,data);
        if(!poster.m_7088_()){p.m_5661_(Component.m_237110_("message.create_posters.wall",DrawingData.options(data).width(),DrawingData.options(data).height()),true);return InteractionResult.FAIL;}
        if(!level.m_7967_(poster))return InteractionResult.FAIL;
        level.m_142346_(p,GameEvent.f_157810_,pos);
        if(!p.m_150110_().f_35937_)s.m_41774_(1);
        return InteractionResult.CONSUME;
    }
    @Override public void m_7373_(ItemStack stack,Level level,List<Component> lines,TooltipFlag flag){
        CompoundTag d=stack.m_41784_().m_128469_("Drawing");
        if(DrawingData.headerValid(d)){lines.add(Component.m_237113_(d.m_128461_("Title")));lines.add(Component.m_237110_("tooltip.create_posters.views",d.m_128437_("Views",10).size()));}
        lines.add(Component.m_237110_("tooltip.create_posters.wall",DrawingData.options(d).width(),DrawingData.options(d).height()));
    }
}
