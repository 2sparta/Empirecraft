package ua.empirecraft.feudal;

import com.palmergames.bukkit.towny.TownyAPI;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import java.sql.SQLException;
import java.util.*;
import static ua.empirecraft.feudal.Model.*;
import static ua.empirecraft.feudal.Database.*;
import ua.empirecraft.feudal.FeudalService.Actor;

/** Bounded, read-only rectangular courtyard inspection; it never loads a chunk or changes a block. */
public final class CastleInspector {
    private final PoliticsService p;
    public CastleInspector(PoliticsService p) throws SQLException {this.p=p;p.db.execute("CREATE TABLE IF NOT EXISTS political_castle_selections(state VARCHAR(80),corner INT,world VARCHAR(36),x INT,y INT,z INT,PRIMARY KEY(state,corner))");}
    public void select(Actor actor,String state,int corner,Player player){p.db.transaction(()->{p.ruler(actor,state);require(corner==1 || corner==2,"Точка: 1 або 2.");
        var at=player.getLocation();check(at.getWorld(),at.getBlockX(),at.getBlockZ(),p.personal(state),at.getBlockY(),new HashSet<>());
        p.db.execute("MERGE INTO political_castle_selections KEY(state,corner) VALUES(?,?,?,?,?,?)",state,corner,at.getWorld().getUID().toString(),at.getBlockX(),at.getBlockY(),at.getBlockZ());
        p.db.execute("DELETE FROM political_castles WHERE state=?",state);p.changed(actor,state,"CASTLE_CORNER",corner+" "+at.getBlockX()+","+at.getBlockY()+","+at.getBlockZ());return null;});}
    private void check(World world,int x,int z,Set<String> personal,int y,Set<Long> checked) throws Exception {
        require(world.isChunkLoaded(x>>4,z>>4),"Чанк не завантажений. Перевірка не завантажує території.");
        int size=com.palmergames.bukkit.towny.object.Coord.getCellSize();
        long cell=((long)Math.floorDiv(x,size)<<32) ^ (Math.floorDiv(z,size)&0xffffffffL);
        if(!checked.add(cell))return;
        var claim=TownyAPI.getInstance().getTownBlock(new Location(world,x,y,z));require(claim!=null && claim.getTownOrNull()!=null && personal.contains(TownyBridge.townId(claim.getTownOrNull())),"Уся споруда має бути в особистій вотчині правителя.");
    }
    private static boolean stone(Material m){String n=m.name();return m.isSolid() && (n.contains("STONE") || n.contains("BRICK") || n.contains("DEEPSLATE") || n.contains("ANDESITE") || n.contains("GRANITE") || n.contains("DIORITE") || n.contains("BASALT") || n.contains("BLACKSTONE") || n.contains("TERRACOTTA") || n.contains("PRISMARINE") || n.equals("NETHERITE_BLOCK"));}
    private static boolean gate(Material m){return m.name().endsWith("_DOOR") || m.name().endsWith("_FENCE_GATE");}
    public void inspect(Actor actor,String state) throws Exception {
        p.ruler(actor,state);var points=p.db.query("SELECT * FROM political_castle_selections WHERE state=? ORDER BY corner",state);require(points.size()==2,"Позначте два протилежні внутрішні кути двору: /castle pos1 і pos2.");
        var a=points.get(0);var b=points.get(1);require(Objects.equals(string(a,"world"),string(b,"world")) && number(a,"y")==number(b,"y"),"Кути мають бути в одному світі й на одній висоті підлоги.");
        World world=Bukkit.getWorld(UUID.fromString(string(a,"world")));require(world!=null,"Світ не завантажений.");
        int x0=(int)Math.min(number(a,"x"),number(b,"x")),x1=(int)Math.max(number(a,"x"),number(b,"x")),z0=(int)Math.min(number(a,"z"),number(b,"z")),z1=(int)Math.max(number(a,"z"),number(b,"z")),y=(int)number(a,"y");
        int span=p.rules.threshold("castle-max-span",64),height=p.rules.threshold("castle-wall-height",4);require(span<=128 && height>=1 && height<=32,"Некоректні межі перевірки замку.");
        require((long)x1-x0+1<=span && (long)z1-z0+1<=span,"Двір перевищує ліміт "+span+" × "+span+".");require(y>world.getMinHeight() && y+height<world.getMaxHeight(),"Висота поза світом.");
        // Verify all permissions/chunk availability before reading geometry.
        var personal=p.personal(state);var checked=new HashSet<Long>();
        for(int x=x0-1;x<=x1+1;x++)for(int z=z0-1;z<=z1+1;z++)check(world,x,z,personal,y,checked);
        var result=CastleGeometry.scan((x,level,z)->{
            Block block=world.getBlockAt(x,level,z);Material material=block.getType();
            return new CastleGeometry.Cell(block.getBlockData().getAsString(),material.isSolid(),block.isPassable(),stone(material),gate(material));
        },x0,x1,y,z0,z1,height);
        String geometry=world.getUID()+":"+x0+":"+x1+":"+y+":"+z0+":"+z1+":"+result.fingerprint();
        p.castleInspect(actor,state,geometry,result.area(),result.walls(),result.perimeter(),result.gates());
    }
    public boolean selected(String id) throws SQLException {return p.db.query("SELECT corner FROM political_castle_selections WHERE state=?",id).size()==2;}
}
