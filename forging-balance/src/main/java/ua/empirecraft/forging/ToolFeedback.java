package ua.empirecraft.forging;

import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.event.entity.player.*;
import net.minecraftforge.event.level.BlockEvent;


/** Common-side listeners; no client class loading and no permanent fake 'broken' quality NBT. */
public final class ToolFeedback {
    private static final ToolNoticeGate notices=new ToolNoticeGate();
    private static final Set<String> qualityKeys=Set.of("tooltip.overgeared.poor","tooltip.overgeared.well",
        "tooltip.overgeared.expert","tooltip.overgeared.perfect","tooltip.overgeared.master","tooltip.overgeared.item_broken");
    private ToolFeedback() {}
    public static boolean tool(ItemStack stack){return !stack.m_41619_() && (stack.m_41720_() instanceof DiggerItem || stack.m_41720_() instanceof ShearsItem);}
    public static void mining(PlayerEvent.BreakSpeed event){
        var player=event.getEntity();var stack=player.m_21205_();
        if(player.m_7500_() || !tool(stack))return;
        event.setNewSpeed(EquipmentQuality.broken(stack) ? 0 : MiningRules.speed(EquipmentQuality.quality(stack),event.getNewSpeed()));

    }
    public static void equipment(LivingEquipmentChangeEvent event){
        if(!(event.getEntity() instanceof ServerPlayer player) || event.getSlot()!=EquipmentSlot.MAINHAND && event.getSlot()!=EquipmentSlot.OFFHAND)return;
        ItemStack before=event.getFrom(),after=event.getTo();
        if(tool(before) && tool(after) && before.m_41720_()==after.m_41720_() && !EquipmentQuality.broken(before) && EquipmentQuality.broken(after)){
            player.m_6330_(SoundEvents.f_11668_,SoundSource.PLAYERS,0.45f,1.4f);
            player.m_5661_(Component.m_237115_("message.empirecraft.tool.broken").m_130940_(ChatFormatting.RED).m_130940_(ChatFormatting.BOLD),true);
        }
    }
    private static void warn(ServerPlayer player){
        if(!notices.allow(player.m_20148_(),System.nanoTime()/1_000_000L))return;
        player.m_5661_(Component.m_237115_("message.empirecraft.tool.cannot_mine").m_130940_(ChatFormatting.RED),true);
        player.m_6330_(SoundEvents.f_11668_,SoundSource.PLAYERS,0.25f,0.7f);
    }
    public static void leftClick(PlayerInteractEvent.LeftClickBlock event){
        var player=event.getEntity();var stack=player.m_21205_();
        if(player.m_7500_() || !tool(stack) || !EquipmentQuality.broken(stack))return;
        event.setCanceled(true);
        if(player instanceof ServerPlayer server && (event.getAction()==PlayerInteractEvent.LeftClickBlock.Action.START || event.getAction()==PlayerInteractEvent.LeftClickBlock.Action.CLIENT_HOLD))warn(server);
    }
    public static void breakBlock(BlockEvent.BreakEvent event){
        var player=event.getPlayer();var stack=player.m_21205_();
        if(!player.m_7500_() && tool(stack) && EquipmentQuality.broken(stack)){
            event.setCanceled(true);if(player instanceof ServerPlayer server)warn(server);
        }
    }
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event){notices.forget(event.getEntity().m_20148_());}
    public static void tooltip(ItemTooltipEvent event){
        var stack=event.getItemStack();if(!tool(stack) && !EquipmentQuality.equipment(stack))return;
        var lines=event.getToolTip();
        if(EquipmentQuality.broken(stack)){
            lines.removeIf(line->line.m_214077_() instanceof TranslatableContents text && qualityKeys.contains(text.m_237508_()));
            int at=Math.min(1,lines.size());
            lines.add(at++,Component.m_237115_("tooltip.empirecraft.tool.broken_quality").m_130940_(ChatFormatting.RED).m_130940_(ChatFormatting.BOLD));
            String quality=EquipmentQuality.quality(stack);
            if(Set.of("poor","well","expert","perfect","master").contains(quality))
                lines.add(at++,Component.m_237110_("tooltip.empirecraft.tool.repair_quality",Component.m_237115_("tooltip.overgeared."+quality)).m_130940_(ChatFormatting.GRAY));
            lines.add(at,Component.m_237115_("tooltip.empirecraft.tool.repair").m_130940_(ChatFormatting.GRAY));
        }else if(tool(stack)){
            String quality=EquipmentQuality.quality(stack);
            if(quality.equals("perfect") || quality.equals("master"))lines.add(Component.m_237115_("tooltip.empirecraft.tool.mining_"+quality).m_130940_(ChatFormatting.GRAY));
        }
    }
}
