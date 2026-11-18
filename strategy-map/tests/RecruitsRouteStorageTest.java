package ua.empirecraft.townymap;

import com.talhanation.recruits.world.RecruitsRoute;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class RecruitsRouteStorageTest {
    @TempDir Path directory;
    @Test void originalRecruitsRouteFilesKeepIdentityCoordinatesAndWaitActions()throws Exception {
        var original=new RecruitsRoute("Патруль Чорного Крука");
        original.addWaypoint(new RecruitsRoute.Waypoint("Переправа",new BlockPos(-16,64,31),
                new RecruitsRoute.WaypointAction(RecruitsRoute.WaypointAction.Type.WAIT,30)));
        original.addWaypoint(new RecruitsRoute.Waypoint("Варта",new BlockPos(100,72,400),null));
        original.saveToFile(directory.toFile());
        var loaded=RecruitsRoute.loadAllRoutes(directory.toFile());assertEquals(1,loaded.size());var restored=loaded.get(0);
        assertEquals(original.getId(),restored.getId());assertEquals(original.getName(),restored.getName());
        assertEquals(2,restored.getWaypoints().size());assertEquals(new BlockPos(-16,64,31),restored.getWaypoints().get(0).getPosition());
        assertEquals(30,restored.getWaypoints().get(0).getAction().getWaitSeconds());
        assertNull(restored.getWaypoints().get(1).getAction());
    }
    @Test void renamingAndReorderingDoNotCreateDuplicatePatrolRoutes()throws Exception {
        var route=new RecruitsRoute("Old");route.addWaypoint(new RecruitsRoute.Waypoint("A",new BlockPos(0,64,0),null));
        route.addWaypoint(new RecruitsRoute.Waypoint("B",new BlockPos(16,64,0),null));route.saveToFile(directory.toFile());
        UUID id=route.getId();route.setName("Нова назва");Collections.swap(route.getWaypoints(),0,1);route.saveToFile(directory.toFile());
        var all=RecruitsRoute.loadAllRoutes(directory.toFile());assertEquals(1,all.size());assertEquals(id,all.get(0).getId());
        assertEquals("B",all.get(0).getWaypoints().get(0).getName());
    }
}
