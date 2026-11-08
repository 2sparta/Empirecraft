package guardtest;

import java.util.Set;
import net.minecraft.core.BlockPos;
import ua.empirecraft.leaves.client.ClientSeasons;

/** Early meshes must be remembered even before the first season synchronization packet. */
public final class ClientRefreshRuntimeTest {
    public static void main(String[] args)throws Exception {
        var field=ClientSeasons.class.getDeclaredField("TOUCHED");field.setAccessible(true);
        @SuppressWarnings("unchecked") Set<Long> touched=(Set<Long>)field.get(null);touched.clear();
        // No world/season is active yet. The state is intentionally unused in this early path.
        if(ClientSeasons.color(null,new BlockPos(32,65,-16),0x45a332,0)!=0x45a332)throw new AssertionError("Early tint must remain native");
        if(touched.size()!=1)throw new AssertionError("Initial leaf mesh would miss the first season refresh");
        ClientSeasons.color(null,new BlockPos(33,66,-15),0x45a332,0);
        if(touched.size()!=1)throw new AssertionError("Repeated section must be deduplicated");
        ClientSeasons.color(null,new BlockPos(48,65,-16),0x45a332,0);
        if(touched.size()!=2)throw new AssertionError("A second leaf section must be retained");
        touched.clear();System.out.println("CLIENT_EARLY_REFRESH_RUNTIME_PASS actual production colour path remembers and deduplicates meshes before the first season packet");
    }
}
