package ua.empirecraft.feudal;

import java.sql.SQLException;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import ua.empirecraft.feudal.FeudalService.Actor;
import ua.empirecraft.townyforge.TownyForge;
import static ua.empirecraft.feudal.Database.*;
import static ua.empirecraft.feudal.Model.require;

final class ForgeCastles {
    private static final Set<String> STONE = Set.of("stone", "brick", "slate", "andesite", "granite", "diorite", "basalt",
            "shale", "gabbro", "marble", "schist", "phyllite", "quartzite", "dolomite", "chert", "chalk", "limestone",
            "rhyolite", "dacite", "conglomerate", "mudstone");
    private final ForgeFeudal f;

    ForgeCastles(ForgeFeudal f) throws SQLException {
        this.f = f;
        f.db.execute("CREATE TABLE IF NOT EXISTS political_castle_selections(state VARCHAR(80),corner INT,"
                + "world VARCHAR(100),x INT,y INT,z INT,PRIMARY KEY(state,corner))");
    }

    void select(Actor actor, String estate, ServerPlayer player, int corner) {
        f.db.transaction(() -> {
            f.politics.ruler(actor, estate);
            var pos = player.m_20183_();
            var place = ForgeFeudal.place(player);
            var territory = TownyForge.view().at(ForgeFeudal.dimension(player.m_284548_()), pos.m_123341_(), pos.m_123343_());
            require(territory != null && f.politics.personal(estate).contains(ForgeFeudal.townId(territory.town())),
                    "Кут має бути у власній вотчині.");
            f.db.execute("MERGE INTO political_castle_selections KEY(state,corner) VALUES(?,?,?,?,?,?)", estate, corner,
                    place.world(), pos.m_123341_(), pos.m_123342_(), pos.m_123343_());
            f.db.execute("DELETE FROM political_castles WHERE state=?", estate);
            return null;
        });
    }

    void inspect(Actor actor, String estate) throws SQLException {
        f.politics.ruler(actor, estate);
        var points = f.db.query("SELECT * FROM political_castle_selections WHERE state=? ORDER BY corner", estate);
        require(points.size() == 2, "Позначте два кути двору командами pos1 та pos2.");
        var a = points.get(0);
        var b = points.get(1);
        String world = string(a, "world");
        int y = (int) number(a, "y");
        require(world.equals(string(b, "world")) && y == number(b, "y"), "Кути мають бути в одному світі й на однаковій висоті.");
        var level = f.level(world);
        int height = f.politics.rules.threshold("castle-wall-height", 4);
        int span = f.politics.rules.threshold("castle-max-span", 64);
        require(level != null && y > level.m_141937_() && y + height < level.m_151558_(), "Висота двору поза світом.");
        int x0 = (int) Math.min(number(a, "x"), number(b, "x")), x1 = (int) Math.max(number(a, "x"), number(b, "x"));
        int z0 = (int) Math.min(number(a, "z"), number(b, "z")), z1 = (int) Math.max(number(a, "z"), number(b, "z"));
        require(x1 - x0 + 1L <= span && z1 - z0 + 1L <= span, "Двір завеликий для перевірки.");
        var personal = f.politics.personal(estate);
        var view = TownyForge.view();
        String dimension = ForgeFeudal.dimension(level);
        for (int x = (x0 - 1) >> 4; x <= (x1 + 1) >> 4; x++) {
            for (int z = (z0 - 1) >> 4; z <= (z1 + 1) >> 4; z++) {
                var chunk = view.territory().get(new ua.empirecraft.townyforge.TownyView.Chunk(dimension, x, z));
                require(chunk != null && personal.contains(ForgeFeudal.townId(chunk.town()))
                        && level.m_6857_().m_61937_(new BlockPos(x << 4, y, z << 4)),
                        "Увесь периметр має бути у завантаженому приваті вотчини.");
            }
        }
        var scan = CastleGeometry.scan((x, by, z) -> {
            var pos = new BlockPos(x, by, z);
            var state = level.m_8055_(pos);
            String id = ForgeMarketBuildings.blockId(state);
            boolean passable = state.m_60812_(level, pos).m_83281_();
            boolean solid = state.m_280296_();
            boolean stone = solid && STONE.stream().anyMatch(id::contains);
            var block = state.m_60734_();
            return new CastleGeometry.Cell(state.toString(), solid, passable, stone, block instanceof DoorBlock || block instanceof FenceGateBlock);
        }, x0, x1, y, z0, z1, height);
        f.politics.castleInspect(actor, estate, world + ":" + x0 + ":" + x1 + ":" + y + ":" + z0 + ":" + z1 + ":" + scan.fingerprint(),
                scan.area(), scan.walls(), scan.perimeter(), scan.gates());
    }
}
