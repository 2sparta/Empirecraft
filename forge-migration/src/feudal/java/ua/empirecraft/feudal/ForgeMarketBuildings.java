package ua.empirecraft.feudal;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
import ua.empirecraft.feudal.CityMarketService.Department;
import static ua.empirecraft.feudal.Database.*;
import static ua.empirecraft.feudal.Model.require;

final class ForgeMarketBuildings {
    private final ForgeFeudal feudal;

    ForgeMarketBuildings(ForgeFeudal feudal) {
        this.feudal = feudal;
    }

    void capture(String market, Department department) throws SQLException {
        var row = feudal.markets.department(market, department);
        var counter = position(row, "c");
        ServerLevel level = level(market);
        require(level != null && loaded(level, counter), "Стійка ринку недоступна.");
        require(matches(level.m_8055_(counter), string(row, "material")), "Зареєстрованої стійки немає.");
        var box = CityMarketService.box(row);
        require(box.y1() >= level.m_141937_() && box.y2() < level.m_151558_(), "Будівля виходить за висоту світу.");
        var random = new Random(0);
        var samples = new ArrayList<BlockPos>();
        int seen = 0;
        for (int x = box.x1(); x <= box.x2(); x++) {
            for (int z = box.z1(); z <= box.z2(); z++) {
                require(loaded(level, new BlockPos(x, box.y1(), z)), "Завантажте всі чанки будівлі.");
                for (int y = box.y1(); y <= box.y2(); y++) {
                    var pos = new BlockPos(x, y, z);
                    var state = level.m_8055_(pos);
                    if (state.m_60795_() || state.m_60734_() instanceof net.minecraft.world.level.block.LiquidBlock) continue;
                    seen++;
                    if (samples.size() < 256) samples.add(pos);
                    else {
                        int slot = random.nextInt(seen);
                        if (slot < 256) samples.set(slot, pos);
                    }
                }
            }
        }
        require(seen >= 8, "Відділ потребує готової будівлі.");
        feudal.db.execute("DELETE FROM cm_structure WHERE market=? AND kind=?", market, department.name());
        for (var pos : samples) {
            feudal.db.execute("INSERT INTO cm_structure VALUES(?,?,?,?,?,?)", market, department.name(),
                    pos.m_123341_(), pos.m_123342_(), pos.m_123343_(), blockId(level.m_8055_(pos)));
        }
    }

    boolean valid(String market, Department department) {
        try {
            var row = feudal.markets.department(market, department);
            ServerLevel level = level(market);
            if (row == null || level == null) return false;
            var counter = position(row, "c");
            if (loaded(level, counter) && !matches(level.m_8055_(counter), string(row, "material"))) return false;
            var samples = feudal.db.query("SELECT x,y,z FROM cm_structure WHERE market=? AND kind=?", market, department.name());
            long missing = 0;
            for (var sample : samples) {
                var pos = position(sample, "");
                if (loaded(level, pos) && level.m_8055_(pos).m_60795_()) missing++;
            }
            return samples.isEmpty() || missing * 2 < samples.size();
        } catch (SQLException failure) {
            throw new IllegalStateException("Не вдалося перевірити будівлю ринку.", failure);
        }
    }

    private ServerLevel level(String market) throws SQLException {
        return feudal.level(string(feudal.markets.market(market), "world"));
    }

    private static boolean loaded(ServerLevel level, BlockPos pos) {
        return level.m_6857_().m_61937_(pos);
    }

    private static BlockPos position(java.util.Map<String, Object> row, String prefix) {
        return new BlockPos((int) number(row, prefix + "x"), (int) number(row, prefix + "y"), (int) number(row, prefix + "z"));
    }

    static String blockId(BlockState state) {
        return Objects.toString(ForgeRegistries.BLOCKS.getKey(state.m_60734_()), "");
    }

    private static boolean matches(BlockState state, String stored) {
        String id = blockId(state);
        String legacyName = id.startsWith("minecraft:") ? id.substring(10) : id.replace(':', '_');
        return stored.equalsIgnoreCase(id) || stored.equals(legacyName.toUpperCase(Locale.ROOT));
    }
}
