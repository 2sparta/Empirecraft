package ua.empirecraft.armory;
import java.util.List;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
public final class WorkItem extends Item {
    public WorkItem(Properties properties){super(properties);}
    @Override public void appendHoverText(ItemStack s,Level level,List<Component> lines,TooltipFlag flag){if(s.hasTag()){var tag=s.getTag();lines.add(Component.translatable("armory.work_target",Component.translatable(tag.getString("targetName"))).withStyle(ChatFormatting.GOLD));lines.add(Component.translatable("armory.work_stage",tag.getInt("stage"),tag.getInt("stages")).withStyle(ChatFormatting.GRAY));}lines.add(Component.translatable("armory.work_saved").withStyle(ChatFormatting.DARK_GRAY));}
}
