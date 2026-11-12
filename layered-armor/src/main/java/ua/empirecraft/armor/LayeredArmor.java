package ua.empirecraft.armor;

import java.util.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.event.ItemAttributeModifierEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.registries.*;

@Mod(LayeredArmor.ID)
public final class LayeredArmor {
    public static final String ID="empirecraft_armor";
    private static final DeferredRegister<MenuType<?>> MENUS=DeferredRegister.create(ForgeRegistries.MENU_TYPES,ID);
    public static final RegistryObject<MenuType<LayerMenu>> MENU=MENUS.register("layers",()->IForgeMenuType.create((id,inv,data)->new LayerMenu(id,inv)));
    public static final SimpleChannel NETWORK=NetworkRegistry.newSimpleChannel(new ResourceLocation(ID,"main"),()->"2","2"::equals,"2"::equals);
    public record Toggle(boolean expanded) {}
    public LayeredArmor() {
        MENUS.register(FMLJavaModLoadingContext.get().getModEventBus());MinecraftForge.EVENT_BUS.register(this);
        NETWORK.registerMessage(0,Toggle.class,(packet,buffer)->buffer.writeBoolean(packet.expanded()),buffer->new Toggle(buffer.readBoolean()),(packet,context)->{
            var ctx=context.get();ctx.enqueueWork(()->{
                var player=ctx.getSender();
                if(player!=null&&player.isAlive()&&!player.isSpectator()&&player.containerMenu==player.inventoryMenu) {
                    ((LayerInventoryAccess)player.inventoryMenu).empirecraft$layers().expanded(packet.expanded());
                    player.inventoryMenu.broadcastChanges();
                }
            });ctx.setPacketHandled(true);
        },Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }
    @SubscribeEvent(priority=EventPriority.HIGH) public void use(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickItem event){ArmorUse.use(event);}
    @SubscribeEvent(priority=EventPriority.LOWEST) public void attributes(ItemAttributeModifierEvent event) {
        var stack=event.getItemStack();if(!LayerData.layered(stack)||event.getSlotType()!=LayerData.slot(stack))return;
        var all=LayerData.pieces(stack);
        for(int layer=0;layer<all.size()-1;layer++)for(var entry:all.get(layer).getAttributeModifiers(event.getSlotType()).entries()) {
            var original=entry.getValue();
            event.addModifier(entry.getKey(),new AttributeModifier(LayerData.modifier(event.getSlotType(),layer,original.getId()),"Empirecraft armor layer",original.getAmount(),original.getOperation()));
        }
    }
    @SubscribeEvent public void tooltip(ItemTooltipEvent event) {
        if(!LayerData.layered(event.getItemStack()))return;
        event.getToolTip().add(Component.translatable("empirecraft.armor.layers"));
        for(var item:LayerData.pieces(event.getItemStack()))event.getToolTip().add(Component.literal(" • ").append(item.getHoverName()));
    }
}
