package com.goida.goidainvrestore.command;

import com.goida.goidainvrestore.GoidaInvRestore;
import com.goida.goidainvrestore.backup.BackupManager;
import com.goida.goidainvrestore.backup.RestoreMode;
import com.goida.goidainvrestore.menu.HistoryMenu;
import com.goida.goidainvrestore.menu.SnapshotMenu;
import com.goida.goidainvrestore.permission.InvRestorePermissions;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Collection;
import java.util.UUID;

/**
 * Admin commands: {@code /goidainvrestore} (alias {@code /invrestore}).
 * <ul>
 *     <li>{@code backups <player>} — open the paginated history GUI.</li>
 *     <li>{@code view <player> <index>} — open a specific backup.</li>
 *     <li>{@code restore <player> <index> [overwrite|give]} — restore onto an online player.</li>
 *     <li>{@code backup <player>} — take a manual backup of an online player.</li>
 *     <li>{@code delete <player> <index>} / {@code clear <player>} — manage history.</li>
 * </ul>
 */
@EventBusSubscriber(modid = GoidaInvRestore.MOD_ID)
public final class InvRestoreCommands {

    private InvRestoreCommands() {}

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        LiteralCommandNode<CommandSourceStack> root = event.getDispatcher().register(
                Commands.literal("goidainvrestore")
                        .requires(InvRestorePermissions::canUse)
                        .then(Commands.literal("backups")
                                .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                        .executes(InvRestoreCommands::backups)))
                        .then(Commands.literal("view")
                                .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                        .then(Commands.argument("index", IntegerArgumentType.integer(0))
                                                .executes(InvRestoreCommands::view))))
                        .then(Commands.literal("backup")
                                .requires(InvRestorePermissions::canManage)
                                .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                        .executes(InvRestoreCommands::manualBackup)))
                        .then(Commands.literal("restore")
                                .requires(InvRestorePermissions::canRestore)
                                .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                        .then(Commands.argument("index", IntegerArgumentType.integer(0))
                                                .executes(ctx -> restore(ctx, RestoreMode.OVERWRITE))
                                                .then(Commands.literal("overwrite")
                                                        .executes(ctx -> restore(ctx, RestoreMode.OVERWRITE)))
                                                .then(Commands.literal("give")
                                                        .executes(ctx -> restore(ctx, RestoreMode.GIVE_ALL))))))
                        .then(Commands.literal("delete")
                                .requires(InvRestorePermissions::canManage)
                                .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                        .then(Commands.argument("index", IntegerArgumentType.integer(0))
                                                .executes(InvRestoreCommands::delete))))
                        .then(Commands.literal("clear")
                                .requires(InvRestorePermissions::canManage)
                                .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                        .executes(InvRestoreCommands::clear))));

        event.getDispatcher().register(Commands.literal("invrestore").redirect(root));
    }

    private static GameProfile profile(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(ctx, "player");
        if (profiles.isEmpty()) {
            throw new CommandSyntaxException(
                    CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownArgument(),
                    Component.literal("Игрок не найден"));
        }
        return profiles.iterator().next();
    }

    private static int backups(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer admin = ctx.getSource().getPlayerOrException();
        GameProfile gp = profile(ctx);
        HistoryMenu.open(admin, gp.getId(), gp.getName());
        return 1;
    }

    private static int view(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer admin = ctx.getSource().getPlayerOrException();
        GameProfile gp = profile(ctx);
        int index = IntegerArgumentType.getInteger(ctx, "index");
        SnapshotMenu.open(admin, gp.getId(), gp.getName(), index);
        return 1;
    }

    private static int manualBackup(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        GameProfile gp = profile(ctx);
        ServerPlayer target = onlineTarget(ctx, gp.getId());
        if (target == null) {
            ctx.getSource().sendFailure(Component.literal("§cИгрок §f" + gp.getName() + " §cне в сети."));
            return 0;
        }
        boolean ok = BackupManager.backup(target, BackupManager.MANUAL);
        ctx.getSource().sendSuccess(() -> Component.literal(ok
                ? "§aСоздан бэкап игрока §f" + gp.getName()
                : "§eБэкап не создан (хранилище недоступно)."), true);
        return ok ? 1 : 0;
    }

    private static int restore(CommandContext<CommandSourceStack> ctx, RestoreMode mode) throws CommandSyntaxException {
        GameProfile gp = profile(ctx);
        int index = IntegerArgumentType.getInteger(ctx, "index");
        ServerPlayer target = onlineTarget(ctx, gp.getId());
        if (target == null) {
            ctx.getSource().sendFailure(Component.literal("§cИгрок §f" + gp.getName() + " §cне в сети."));
            return 0;
        }
        boolean ok = BackupManager.restore(ctx.getSource(), target, index, mode);
        if (ok) {
            ctx.getSource().sendSuccess(() -> Component.literal("§aБэкап #" + index + " восстановлен игроку §f"
                    + gp.getName() + " §a(" + (mode == RestoreMode.OVERWRITE ? "перезапись" : "выдача") + ")."), true);
            return 1;
        }
        ctx.getSource().sendFailure(Component.literal("§cБэкап #" + index + " не найден."));
        return 0;
    }

    private static int delete(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        GameProfile gp = profile(ctx);
        int index = IntegerArgumentType.getInteger(ctx, "index");
        BackupManager.delete(gp.getId(), index);
        ctx.getSource().sendSuccess(() -> Component.literal("§aБэкап #" + index + " игрока §f"
                + gp.getName() + " §aудалён."), true);
        return 1;
    }

    private static int clear(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        GameProfile gp = profile(ctx);
        BackupManager.clear(gp.getId());
        ctx.getSource().sendSuccess(() -> Component.literal("§aВся история бэкапов игрока §f"
                + gp.getName() + " §aудалена."), true);
        return 1;
    }

    private static ServerPlayer onlineTarget(CommandContext<CommandSourceStack> ctx, UUID uuid) {
        return ctx.getSource().getServer().getPlayerList().getPlayer(uuid);
    }
}
