package ua.empirecraft.forging;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

@Mod("empirecraft_forging")
public final class ForgingMod {
    public ForgingMod() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::creative);
        MinecraftForge.EVENT_BUS.addListener(this::tick);
        MinecraftForge.EVENT_BUS.addListener(this::join);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, this::hurt);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, ToolFeedback::mining);
        MinecraftForge.EVENT_BUS.addListener(ToolFeedback::equipment);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ToolFeedback::leftClick);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ToolFeedback::breakBlock);
        MinecraftForge.EVENT_BUS.addListener(ToolFeedback::logout);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, ToolFeedback::tooltip);
    }
    private void creative(BuildCreativeModeTabContentsEvent event) {
        var removed = new java.util.ArrayList<ItemStack>();
        for (var entry : event.getEntries()) {
            var stack = entry.getKey();
            if (EquipmentContent.removed(stack.m_41720_()) || EquipmentContent.potionArrow(stack.m_41720_(), stack.m_41783_())) removed.add(stack);
        }
        removed.forEach(event.getEntries()::remove);
    }
    private static void clean(ItemStack stack) {
        if (!stack.m_41619_()) EquipmentContent.clean(stack, stack.m_41720_(), stack.m_41783_());
    }
    private void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.m_9236_().f_46443_) return;
        var player = event.player;
        // Includes worn gear, offhand and containers currently open on the server.
        for (var slot : player.f_36095_.f_38839_) clean(slot.m_7993_());
        for (var slot : player.f_36096_.f_38839_) clean(slot.m_7993_());
    }
    private void join(EntityJoinLevelEvent event) {
        if (event.getLevel().f_46443_) return;
        if (event.getEntity() instanceof ItemEntity item) clean(item.m_32055_());
        if (event.getEntity() instanceof LivingEntity living)
            for (var slot : EquipmentSlot.values()) clean(living.m_6844_(slot));
    }
    private void hurt(LivingHurtEvent event) {
        var direct = event.getSource().m_7640_();
        if (direct == null || direct.m_9236_().f_46443_) return;
        var data = direct.getPersistentData();
        if (data.m_128425_(RangedQuality.KEY, 6)) event.setAmount(RangedQuality.damage(event.getAmount(), data.m_128459_(RangedQuality.KEY)));
    }
}

