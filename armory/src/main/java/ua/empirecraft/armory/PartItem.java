package ua.empirecraft.armory;
import java.util.List;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.network.chat.Component;
public final class PartItem extends Item {
    public final String metal,form;
    public PartItem(String metal,String form,Properties properties){super(properties);this.metal=metal;this.form=form;}
    @Override public void appendHoverText(ItemStack stack,Level level,List<Component> lines,TooltipFlag flag){Parts.tooltip(stack,lines);}
}
