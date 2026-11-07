package locktest;

import java.util.*;
import com.mojang.authlib.GameProfile;
import melonslise.locks.common.init.LocksItems;
import melonslise.locks.common.item.LockingItem;
import melonslise.locks.common.util.LocksUtil;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.registries.ForgeRegistries;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/** Writes old-format locks using the exact pre-upgrade JAR in a disposable world. */
public final class Baseline extends JavaPlugin {
    @Override public void onEnable() {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try {
                var world = Bukkit.getWorld("locks_test");
                var level = (ServerLevel)world.getClass().getMethod("getHandle").invoke(world);
                var player = FakePlayerFactory.get(level, new GameProfile(UUID.fromString("7209c764-53a2-4bfb-915b-a4951a159731"), "Locksmith"));
                String[] blocks = {"minecraft:chest", "farmersdelight:oak_cabinet", "handcrafted:oak_cupboard"};
                for (int i = 0; i < blocks.length; i++) {
                    var pos = new BlockPos(i * 4, 80, 0); level.setChunkForced(pos.getX() >> 4, 0, true);
                    level.setBlock(pos.below(), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
                    level.setBlock(pos, ForgeRegistries.BLOCKS.getValue(new ResourceLocation(blocks[i])).defaultBlockState(), 3);
                    var inventory = (Container)level.getBlockEntity(pos); inventory.setItem(0, new ItemStack(Items.IRON_INGOT, 12));
                    var stack = new ItemStack(LocksItems.STEEL_LOCK.get()); stack.getOrCreateTag().putInt(LockingItem.KEY_ID, 1001 + i);
                    player.moveTo(pos.getX() + 0.5, 80, -1, 0, 0); player.setItemInHand(InteractionHand.MAIN_HAND, stack);
                    stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false)));
                    if (!LocksUtil.locked(level, pos)) throw new AssertionError("Old lock placement " + blocks[i]);
                }
                System.out.println("STEEL_BASELINE_PASS old-format-locks=3");
            } catch (Throwable error) { System.err.println("STEEL_NATIVE_FAIL"); error.printStackTrace(); }
            Bukkit.shutdown();
        }, 30);
    }
}
