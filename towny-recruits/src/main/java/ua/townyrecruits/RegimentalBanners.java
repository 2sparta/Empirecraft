package ua.townyrecruits;

import com.talhanation.recruits.entities.AbstractRecruitEntity;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.phys.Vec3;

/** Vanilla heraldry shared by map flags and actual banner items; assigning a bearer never creates an item. */
public final class RegimentalBanners {
    public static final int MAX_LAYERS = 16;
    public record Layer(String pattern, int color) {
        public boolean sane() { return pattern != null && pattern.matches("[a-z0-9_:/.-]{1,48}") && color >= 0 && color < 16; }
    }
    public record Design(int base, List<Layer> layers) {
        public boolean sane() { return base >= 0 && base < 16 && layers != null && layers.size() <= MAX_LAYERS && layers.stream().allMatch(l -> l != null && l.sane()); }
        public CompoundTag save() {
            CompoundTag tag = new CompoundTag(); tag.m_128405_("base",base); ListTag list = new ListTag();
            for (Layer layer : layers) { CompoundTag t = new CompoundTag(); t.m_128359_("pattern",layer.pattern()); t.m_128405_("color",layer.color()); list.add(t); }
            tag.m_128365_("layers",list); return tag;
        }
        public static Design load(CompoundTag tag) {
            ListTag list = tag.m_128437_("layers",10); if (list.size() > MAX_LAYERS) return null;
            List<Layer> layers = new ArrayList<>();
            for (int i=0;i<list.size();i++) { var t=list.m_128728_(i); layers.add(new Layer(t.m_128461_("pattern"),t.m_128451_("color"))); }
            Design design = new Design(tag.m_128451_("base"),List.copyOf(layers)); return design.sane() ? design : null;
        }
    }
    private RegimentalBanners() {}
    public static Design fallback(UUID regiment) { return new Design(Math.floorMod(regiment.hashCode(),16),List.of()); }
    public static Design design(MilitaryData data, UUID regiment) { return data.banners.getOrDefault(regiment,fallback(regiment)); }
    public static Design from(ItemStack item) {
        if (item.m_41619_() || !(item.m_41720_() instanceof BannerItem banner)) return null;
        ListTag list = BannerBlockEntity.m_58487_(item); List<Layer> layers = new ArrayList<>();
        if (list != null) {
            if (list.size() > MAX_LAYERS) return null;
            for (int i=0;i<list.size();i++) { var t=list.m_128728_(i); layers.add(new Layer(t.m_128461_("Pattern"),t.m_128451_("Color"))); }
        }
        Design design = new Design(banner.m_40545_().m_41060_(),List.copyOf(layers)); return design.sane() ? design : null;
    }
    public static boolean carrying(MilitaryData data, UUID regiment, AbstractRecruitEntity r) {
        return regiment != null && r.getIsOwned() && r.m_6084_() && r.m_20148_().equals(data.bearers.get(regiment))
            && design(data,regiment).equals(from(r.m_21206_()));
    }
    static AbstractRecruitEntity lookedAt(MilitarySystem army, MilitaryData.Regiment regiment, ServerPlayer player) {
        Vec3 eye = player.m_20299_(1), end = eye.m_82549_(player.m_20252_(1).m_82490_(16));
        return army.bridge.loaded.stream().filter(r -> r.getIsOwned() && r.m_6084_() && r.m_20193_() == player.m_20193_()
            && army.data.companies.containsKey(r.getGroup()) && army.data.companies.get(r.getGroup()).regiment().equals(regiment.id())
            && r.m_20191_().m_82400_(0.25).m_82371_(eye,end).isPresent() && player.m_142582_(r))
            .min(Comparator.comparingDouble(player::m_20280_)).orElseThrow(() -> new IllegalArgumentException("Наведіться на бійця цього полку в межах 16 блоків."));
    }
    public static void set(MilitarySystem army, MilitaryData.Regiment regiment, ServerPlayer player) {
        Design design = from(player.m_21205_()); if (design == null) throw new IllegalArgumentException("Тримайте в основній руці прапор Minecraft (до 16 візерунків).");
        army.data.banners.put(regiment.id(),design); army.data.m_77762_();
    }
    public static void assign(MilitarySystem army, MilitaryData.Regiment regiment, ServerPlayer player) {
        AbstractRecruitEntity r = lookedAt(army,regiment,player);
        equip(army.data,regiment.id(),player,r);
    }
    public static void equip(MilitaryData data, UUID regiment, ServerPlayer player, AbstractRecruitEntity r) {
        if (MoraleSystem.routing(r)) throw new IllegalArgumentException("Боєць утікає й не приймає знамено.");
        Design design = design(data,regiment);
        if (!design.equals(from(r.m_21206_()))) {
            if (!r.m_21206_().m_41619_()) throw new IllegalArgumentException("Звільніть другу руку рекрута; його спорядження не буде замінено.");
            ItemStack held = player.m_21205_();
            if (!design.equals(from(held))) throw new IllegalArgumentException("Тримайте прапор із зареєстрованим дизайном полку або спочатку задайте /nation regiment banner <полк>.");
            ItemStack one = held.m_41777_(); one.m_41764_(1); r.m_21008_(InteractionHand.OFF_HAND,one); held.m_41774_(1);
        }
        data.bearers.put(regiment,r.m_20148_()); data.m_77762_();
    }
}
