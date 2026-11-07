package ua.empirecraft.locks;

import java.security.SecureRandom;
import java.util.Observable;
import java.util.Observer;
import java.util.UUID;
import melonslise.locks.common.container.LockPickingContainer;
import melonslise.locks.common.init.*;
import melonslise.locks.common.util.Lockable;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.IContainerFactory;
import net.minecraftforge.network.PacketDistributor;

@SuppressWarnings("deprecation")
public final class SteelPickingMenu extends LockPickingContainer implements Observer {
    private static final SecureRandom RANDOM = new SecureRandom();
    public final UUID nonce;
    private final Level openedLevel;
    private final ItemStack pick;
    private final PickingSession session;
    private boolean invalidated, committing, completed;
    private int lastSequence;
    private PickPackets.Result lastReply;

    public SteelPickingMenu(int id, Player player, InteractionHand hand, Lockable target, UUID nonce) {
        super(id, player, hand, target);
        this.nonce = nonce; openedLevel = player.level(); pick = player.getItemInHand(hand);
        session = openedLevel.isClientSide ? null : new PickingSession(target.lock.getLength(), complexity, RANDOM);
        if (!openedLevel.isClientSide) target.lock.addObserver(this);
    }

    public static IContainerFactory<LockPickingContainer> factory() {
        return (id, inv, buf) -> {
            InteractionHand hand = buf.readEnum(InteractionHand.class);
            Lockable target = Lockable.fromBuf(buf);
            if (target.lock.getLength() < 1 || target.lock.getLength() > 20) throw new IllegalArgumentException("Invalid lock length");
            return new SteelPickingMenu(id, inv.player, hand, target, buf.readUUID());
        };
    }

    public static boolean canReach(Player player, Lockable target) {
        return player.isAlive() && !player.isSpectator() && player.distanceToSqr(target.bb.center()) <= 36.0
                && target.getLockState(player.level()) != null;
    }

    @Override public boolean isValidPick(ItemStack stack) { return !stack.isEmpty() && stack.getItem() instanceof SteelLockPickItem; }

    @Override public boolean stillValid(Player actor) {
        if (actor != player || invalidated || actor.level() != openedLevel || !canReach(actor, lockable)) return false;
        if (openedLevel.isClientSide) return completed || lockable.lock.isLocked();
        var handler = openedLevel.getCapability(LocksCapabilities.LOCKABLE_HANDLER).orElse(null);
        if (handler == null || handler.getLoaded().get(lockable.id) != lockable) return false;
        if (completed) return !lockable.lock.isLocked();
        return lockable.lock.isLocked() && actor.getItemInHand(hand) == pick && isValidPick(pick)
                && ClaimPermission.allowed(actor, lockable);
    }

    // The legacy byte-only request cannot act on this menu. All actions use the checked session protocol.
    @Override public void tryPin(int ignored) {}
    @Override public void handlePin(boolean correct, boolean reset) {}
    @Override public boolean isOpen() { return completed; }

    public void receive(PickPackets.Attempt request) {
        if (!(player instanceof ServerPlayer server) || request.menu() != containerId || !nonce.equals(request.nonce())) return;
        if (!stillValid(player)) { player.closeContainer(); return; }
        if (lastReply != null && request.sequence() == lastSequence) { send(server, lastReply); return; }
        if (request.sequence() != lastSequence + 1 || request.pin() < 0 || request.pin() >= lockable.lock.getLength()) return;
        lastSequence = request.sequence();
        var outcome = session.attempt(request.pin(), openedLevel.getGameTime());
        boolean wrong = outcome == PickingSession.Outcome.WRONG, broke = false;
        if (wrong) {
            pick.hurtAndBreak(PickingSession.wear(complexity, sturdy), player, p -> p.broadcastBreakEvent(hand));
            broke = pick.isEmpty();
            if (shocking > 0) {
                player.hurt(LocksDamageSources.getDamageSource(openedLevel, LocksDamageSources.SHOCK), Math.min(shocking, 3) * 1.5f);
                sound(LocksSoundEvents.SHOCK.get());
            }
            sound(LocksSoundEvents.PIN_FAIL.get());
        } else if (outcome == PickingSession.Outcome.CORRECT) sound(LocksSoundEvents.PIN_MATCH.get());
        currIndex = session.progress();
        if (!completed && session.finished()) {
            committing = true;
            try { lockable.lock.setLocked(false); completed = true; }
            finally { committing = false; }
            sound(LocksSoundEvents.LOCK_OPEN.get());
        }
        lastReply = new PickPackets.Result(containerId, nonce, lastSequence, request.pin(), currIndex,
                outcome == PickingSession.Outcome.CORRECT, wrong, broke, completed,
                session.remaining(openedLevel.getGameTime()), Math.max(0, pick.getMaxDamage() - pick.getDamageValue()));
        send(server, lastReply);
        broadcastChanges();
    }

    private static void send(ServerPlayer player, PickPackets.Result reply) { LocksNetwork.MAIN.send(PacketDistributor.PLAYER.with(() -> player), reply); }
    private void sound(SoundEvent sound) { openedLevel.playSound(null, pos.x, pos.y, pos.z, sound, SoundSource.BLOCKS, 1, 1); }
    @Override public void update(Observable ignored, Object data) { if (!committing) invalidated = true; }

    public void clientProgress(int progress, boolean open) { currIndex = progress; completed = open; }
    @Override public void removed(Player player) {
        lockable.lock.deleteObserver(this);
        // Old removed() unlocks when isOpen(). Completion has already committed once on the server.
        completed = false; currIndex = 0; super.removed(player);
    }

    public record Provider(InteractionHand hand, Lockable target, UUID nonce) implements MenuProvider {
        @Override public Component getDisplayName() { return TITLE; }
        @Override public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) { return new SteelPickingMenu(id, player, hand, target, nonce); }
    }
}
