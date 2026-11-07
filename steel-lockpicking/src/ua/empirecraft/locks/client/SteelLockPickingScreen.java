package ua.empirecraft.locks.client;

import melonslise.locks.client.gui.LockPickingScreen;
import melonslise.locks.client.gui.sprite.action.MoveAction;
import melonslise.locks.common.container.LockPickingContainer;
import melonslise.locks.common.init.LocksNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import ua.empirecraft.locks.PickPackets;
import ua.empirecraft.locks.SteelPickingMenu;

/** Keep the original animated pin/spring mechanism and steel textures. */
public final class SteelLockPickingScreen extends LockPickingScreen {
    private final SteelPickingMenu steel;
    private int sequence, pending, waitTicks, cooldown, durability;
    private boolean breaking;
    private Component status = Component.empty();

    public SteelLockPickingScreen(LockPickingContainer menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        steel = (SteelPickingMenu)menu;
        var stack = inventory.player.getItemInHand(hand);
        durability = stack.getMaxDamage() - stack.getDamageValue();
    }

    @Override protected boolean pullPin(int pin) {
        if (pin < 0 || pin >= pins.length || pins[pin] || pending != 0 || cooldown > 0 || steel.isOpen()) return false;
        currPin = pin; pending = ++sequence; waitTicks = 0;
        lockPick.speedX = 0;
        LocksNetwork.MAIN.sendToServer(new PickPackets.Attempt(steel.containerId, steel.nonce, pending, pin));
        return true;
    }

    public void receive(PickPackets.Result result) {
        if (result.menu() != steel.containerId || !steel.nonce.equals(result.nonce()) || result.sequence() != pending
                || pending == 0 || result.pin() != currPin || result.progress() < 0 || result.progress() > pins.length) return;
        pending = 0; cooldown = Math.max(0, Math.min(16, result.cooldown())); durability = Math.max(0, result.durability());
        steel.clientProgress(result.progress(), result.open());
        breaking = result.broken();
        super.handlePin(result.correct(), result.reset());
        status = Component.translatable(result.open() ? "locks.gui.lockpicking.open" : result.correct()
                ? "locks.gui.lockpicking.correct" : result.reset() ? "locks.gui.lockpicking.reset" : "locks.gui.lockpicking.wait");
    }

    @Override public void reset() {
        if (breaking) { super.reset(); return; }
        for (int i = 0; i < pins.length; i++) {
            if (!pins[i]) continue;
            pins[i] = false;
            upperPins[i].execute(MoveAction.to(upperPins[i], upperPins[i].posX, pinTumblers[i].posY - UPPER_PIN_TEX.height, 2));
        }
    }

    @Override public void containerTick() {
        super.containerTick();
        if (cooldown > 0) cooldown--;
        if (pending != 0 && ++waitTicks > 60) {
            if (minecraft.player != null) minecraft.player.displayClientMessage(Component.translatable("locks.status.timeout"), true);
            onClose();
        }
    }

    @Override protected void renderLabels(GuiGraphics graphics, int x, int y) {
        graphics.drawString(font, title, 0, -12, 0xffffff, false);
        var options = minecraft.options;
        graphics.drawString(font, Component.translatable("locks.gui.lockpicking.controls", options.keyLeft.getTranslatedKeyMessage(),
                options.keyRight.getTranslatedKeyMessage(), options.keyUp.getTranslatedKeyMessage()), 0, imageHeight + 8, 0xdddddd, false);
        graphics.drawString(font, Component.translatable("locks.gui.lockpicking.progress", countPins(), length, durability), 0, imageHeight + 20, 0xdddddd, false);
        graphics.drawString(font, status, 0, imageHeight + 32, steel.isOpen() ? 0x66ff66 : 0xffdd88, false);
    }
    private int countPins() { int count = 0; for (boolean pin : pins) if (pin) count++; return count; }
}
