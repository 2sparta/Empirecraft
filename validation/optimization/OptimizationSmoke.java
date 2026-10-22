import java.nio.file.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class OptimizationSmoke extends JavaPlugin implements Listener {
    private World world;
    private final int[] burns = new int[6], starts = new int[6], smelts = new int[6];
    private final Material[] blocks = {Material.FURNACE, Material.FURNACE, Material.BLAST_FURNACE, Material.BLAST_FURNACE, Material.SMOKER, Material.SMOKER};
    private final List<String> checks = new ArrayList<>();
    private int failures;
    @Override public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getScheduler().runTaskLater(this, this::prepare, 30);
    }
    private int index(Block b) {
        return b.getWorld() == world && b.getY() == 110 && b.getZ() == 0 && b.getX() >= 0 && b.getX() < 6 ? b.getX() : -1;
    }
    @EventHandler public void burn(FurnaceBurnEvent e) { int i = index(e.getBlock()); if (i >= 0) burns[i]++; }
    @EventHandler public void start(FurnaceStartSmeltEvent e) { int i = index(e.getBlock()); if (i >= 0) { starts[i]++; e.setTotalCookTime(4); } }
    @EventHandler public void smelt(FurnaceSmeltEvent e) { int i = index(e.getBlock()); if (i >= 0) { smelts[i]++; if (i % 2 == 1) e.setCancelled(true); } }
    private void check(String name, boolean pass) { checks.add((pass ? "PASS " : "FAIL ") + name); if (!pass) failures++; }
    private void prepare() {
        world = Objects.requireNonNull(getServer().getWorld("optimization_test"));
        world.setChunkForceLoaded(0, 0, true);
        for (int i = 0; i < 6; i++) {
            world.getBlockAt(i, 110, 0).setType(Material.AIR);
            world.getBlockAt(i, 110, 0).setType(blocks[i]);
            Furnace f = (Furnace)world.getBlockAt(i, 110, 0).getState();
            f.getInventory().setSmelting(new ItemStack(i < 4 ? Material.RAW_IRON : Material.BEEF, 16));
            f.getInventory().setFuel(new ItemStack(Material.COAL, 8));
        }
        getServer().getScheduler().runTaskLater(this, this::verify, 100);
    }
    private void verify() {
        for (int i = 0; i < 6; i++) {
            Furnace f = (Furnace)world.getBlockAt(i, 110, 0).getState();
            ItemStack result = f.getInventory().getResult();
            check(blocks[i] + " burn event " + i, burns[i] > 0);
            check(blocks[i] + " start event " + i, starts[i] > 0);
            check(blocks[i] + " smelt event " + i, smelts[i] > 0);
            if (i % 2 == 0) {
                check(blocks[i] + " output " + i, result != null && result.getType() == (i < 4 ? Material.IRON_INGOT : Material.COOKED_BEEF) && result.getAmount() == 16);
            } else {
                check(blocks[i] + " cancellation output " + i, result == null || result.getType().isAir());
                check(blocks[i] + " cancellation input " + i, f.getInventory().getSmelting() != null && f.getInventory().getSmelting().getAmount() == 16);
            }
        }
        getServer().dispatchCommand(getServer().getConsoleSender(), "execute in minecraft:overworld run place structure minecraft:village_plains 0 110 0");
        try {
            Files.writeString(Path.of("optimization-smoke.txt"), String.join("\n", checks) + "\nfailures=" + failures + "\n");
        } catch (Exception e) { throw new RuntimeException(e); }
        getLogger().info("OPTIMIZATION_SMOKE failures=" + failures + " checks=" + checks.size());
        getServer().getScheduler().runTaskLater(this, () -> getServer().shutdown(), 10);
    }
}
