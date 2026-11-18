package ua.empirecraft.interactions;

import com.mctabletop.neoforge.block.BoardInteraction;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.event.RegisterCommandsEvent;

public interface TableAccess {
    boolean empirecraft$isPrivate();
    void empirecraft$setPrivate(boolean restricted);

    static void commands(RegisterCommandsEvent event) {
        var root=Commands.literal("table");
        var access=Commands.literal("access").executes(ctx -> change(ctx.getSource(),null));
        access.then(Commands.literal("public").executes(ctx -> change(ctx.getSource(),false)));
        access.then(Commands.literal("private").executes(ctx -> change(ctx.getSource(),true)));
        event.getDispatcher().register(root.then(access));
    }

    private static int change(CommandSourceStack source, Boolean restricted) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player=source.getPlayerOrException();
        if (!(player.pick(6,1,false) instanceof BlockHitResult hit) || hit.getType()!=HitResult.Type.BLOCK) {
            source.sendFailure(Component.translatable("empirecraft.table.look"));
            return 0;
        }
        var table=BoardInteraction.table(player.level(),hit.getBlockPos());
        if (table==null || player.isSpectator()) {
            source.sendFailure(Component.translatable("empirecraft.table.look"));
            return 0;
        }
        var settings=(TableAccess)table;
        if (restricted!=null) {
            if (!table.canManage(player)) {
                source.sendFailure(Component.translatable("empirecraft.table.owner"));
                return 0;
            }
            settings.empirecraft$setPrivate(restricted);
        }
        source.sendSuccess(() -> Component.translatable(settings.empirecraft$isPrivate()
            ? "empirecraft.table.private" : "empirecraft.table.public"),false);
        return 1;
    }
}
