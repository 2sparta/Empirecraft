package ua.empirecraft.feudal;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import ua.empirecraft.townyforge.mixin.FeudalPlayerSave;
import static ua.empirecraft.feudal.Model.require;

final class ForgeFeudalItems {
    private static final String FORMAT = "forge1:";
    private static final int SLOTS = 36;

    record Transfer(CommerceService.InventoryChange intent, List<ItemStack> items) {}

    static String prototype(ItemStack item) {
        require(!item.m_41619_(), "Візьміть предмет у головну руку.");
        return FORMAT + item.m_255036_(1).m_41739_(new CompoundTag());
    }

    static ItemStack prototype(String encoded) {
        return ItemStack.m_41712_(tag(encoded));
    }

    private static CompoundTag tag(String encoded) {
        require(encoded.startsWith(FORMAT), "Цей предмет потребує перетворення з формату Bukkit.");
        try {
            return TagParser.m_129359_(encoded.substring(FORMAT.length()));
        } catch (CommandSyntaxException failure) {
            throw new IllegalArgumentException("Збережені дані предмета пошкоджені.", failure);
        }
    }

    static Transfer change(ServerPlayer player, ItemStack item, long count, boolean give) {
        return change(player, new Movement(item, count, give));
    }

    record Movement(ItemStack item, long count, boolean give) {}

    static Transfer change(ServerPlayer player, Movement... movements) {
        require(player.f_36096_.m_142621_().m_41619_(), "Спочатку звільніть предмет на курсорі.");
        var before = items(player);
        var after = new ArrayList<>(before);
        for (var movement : movements) move(after, movement);
        var intent = new CommerceService.InventoryChange(player.m_20148_().toString(), FORMAT + encode(before), FORMAT + encode(after));
        return new Transfer(intent, List.copyOf(after));
    }

    private static void move(List<ItemStack> after, Movement movement) {
        var item = movement.item();
        var count = movement.count();
        boolean give = movement.give();
        require(!item.m_41619_() && count > 0 && count <= 36L * 99, "Недоступний предмет або неправильна кількість.");
        String expected = prototype(item);
        long left = count;
        int phases = give ? 2 : 1;
        for (int phase = 0; phase < phases && left > 0; phase++) {
            for (int slot = 0; slot < SLOTS && left > 0; slot++) {
                ItemStack stack = after.get(slot);
                if (phase == 0 && (stack.m_41619_() || !expected.equals(prototype(stack)))) continue;
                if (phase == 1 && !stack.m_41619_()) continue;
                int used = (int) Math.min(left, give ? item.m_41741_() - stack.m_41613_() : stack.m_41613_());
                if (used <= 0) continue;
                after.set(slot, item.m_255036_(stack.m_41613_() + (give ? used : -used)));
                left -= used;
            }
        }
        require(left == 0, give ? "Звільніть місце в інвентарі." : "Недостатньо однакових предметів в інвентарі.");
    }

    static void commit(ServerPlayer player, CommerceService commerce, String operation, Transfer transfer) throws IOException {
        commit(player, operation, transfer, applied -> commerce.finish(operation, applied));
    }

    static void commit(ServerPlayer player, String operation, Transfer transfer, Consumer<Boolean> finish) throws IOException {
        if (!encode(items(player)).equals(tag(transfer.intent().before()))) {
            finish.accept(false);
            throw new IllegalArgumentException("Інвентар змінився. Повторіть передачу.");
        }
        var inventory = player.m_150109_();
        for (int slot = 0; slot < SLOTS; slot++) inventory.f_35974_.set(slot, transfer.items().get(slot));
        inventory.m_6596_();
        player.f_36096_.m_38946_();
        ((FeudalPlayerSave) player.m_20194_().m_6846_()).empirecraft$savePlayer(player);
        if (!saved(player).equals(tag(transfer.intent().after()))) {
            throw new IOException("Inventory journal " + operation + " requires reconciliation");
        }
        finish.accept(true);
    }

    static void recover(ServerPlayer player, CommerceService commerce, String operation, String before, String after) throws IOException {
        recover(player, before, after, applied -> commerce.finish(operation, applied));
    }

    static void recover(ServerPlayer player, String before, String after, Consumer<Boolean> finish) throws IOException {
        var persisted = saved(player);
        var live = encode(items(player));
        require(persisted.equals(live), "Збережений і поточний інвентарі різні. Потрібна перевірка адміністратора.");
        if (persisted.equals(tag(after))) finish.accept(true);
        else if (persisted.equals(tag(before))) finish.accept(false);
        else throw new IllegalArgumentException("Інвентар змінився після передачі. Потрібна перевірка адміністратора.");
    }

    private static List<ItemStack> items(ServerPlayer player) {
        return List.copyOf(player.m_150109_().f_35974_);
    }

    private static CompoundTag encode(List<ItemStack> items) {
        var result = new CompoundTag();
        for (int slot = 0; slot < items.size(); slot++) {
            if (!items.get(slot).m_41619_()) result.m_128365_(Integer.toString(slot), items.get(slot).m_41739_(new CompoundTag()));
        }
        return result;
    }

    private static CompoundTag saved(ServerPlayer player) throws IOException {
        var path = player.m_20194_().m_129843_(LevelResource.f_78176_).resolve(player.m_20148_() + ".dat");
        var inventory = NbtIo.m_128937_(path.toFile()).m_128437_("Inventory", 10);
        var saved = new CompoundTag();
        for (int i = 0; i < inventory.size(); i++) {
            var entry = inventory.m_128728_(i);
            int slot = Byte.toUnsignedInt(entry.m_128445_("Slot"));
            if (slot >= SLOTS) continue;
            entry.m_128473_("Slot");
            saved.m_128365_(Integer.toString(slot), entry);
        }
        return saved;
    }
}
