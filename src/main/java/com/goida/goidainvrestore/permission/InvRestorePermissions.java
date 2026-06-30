package com.goida.goidainvrestore.permission;

import com.goida.goidainvrestore.Config;
import com.goida.goidainvrestore.GoidaInvRestore;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;

import java.util.Optional;
import java.util.function.Predicate;

/**
 * Admin permission gate for the inventory-backup commands and GUI.
 *
 * <p>Rights are exposed as NeoForge permission nodes ({@code goidainvrestore.use/restore/manage}).
 * With no permission mod installed the nodes fall back to the vanilla op level
 * ({@code adminPermissionLevel}, default 2). Resolution order honours, in turn: NeoForge
 * {@code PermissionAPI} (LuckPerms-as-a-mod + op-level fallback), FTB Ranks (its own API, since it
 * does not hook PermissionAPI), then the Bukkit permission system on hybrid cores.
 */
@EventBusSubscriber(modid = GoidaInvRestore.MOD_ID)
public final class InvRestorePermissions {

    /** {@code goidainvrestore.use} — open the backup GUI / view backups. */
    public static final PermissionNode<Boolean> USE = node("use");
    /** {@code goidainvrestore.restore} — restore a backup onto a player. */
    public static final PermissionNode<Boolean> RESTORE = node("restore");
    /** {@code goidainvrestore.manage} — manual backup, delete/clear history. */
    public static final PermissionNode<Boolean> MANAGE = node("manage");

    public static final String NODE_USE = "goidainvrestore.use";
    public static final String NODE_RESTORE = "goidainvrestore.restore";
    public static final String NODE_MANAGE = "goidainvrestore.manage";

    private InvRestorePermissions() {}

    private static PermissionNode<Boolean> node(String name) {
        return new PermissionNode<>(GoidaInvRestore.MOD_ID, name, PermissionTypes.BOOLEAN,
                (player, playerUUID, context) ->
                        player != null && player.hasPermissions(Config.ADMIN_PERMISSION_LEVEL.get()));
    }

    @SubscribeEvent
    public static void onGatherNodes(PermissionGatherEvent.Nodes event) {
        event.addNodes(USE, RESTORE, MANAGE);
    }

    // ---- Player checks --------------------------------------------------------------------

    public static boolean canUse(ServerPlayer player) {
        return has(player, USE, NODE_USE);
    }

    public static boolean canRestore(ServerPlayer player) {
        return has(player, RESTORE, NODE_RESTORE);
    }

    public static boolean canManage(ServerPlayer player) {
        return has(player, MANAGE, NODE_MANAGE);
    }

    // ---- Command-source variants (console / command blocks pass via op level) -------------

    public static boolean canUse(CommandSourceStack source) {
        return has(source, InvRestorePermissions::canUse);
    }

    public static boolean canRestore(CommandSourceStack source) {
        return has(source, InvRestorePermissions::canRestore);
    }

    public static boolean canManage(CommandSourceStack source) {
        return has(source, InvRestorePermissions::canManage);
    }

    private static boolean has(ServerPlayer player, PermissionNode<Boolean> node, String dottedNode) {
        if (Boolean.TRUE.equals(PermissionAPI.getPermission(player, node))) {
            return true;
        }
        Optional<Boolean> ftb = FtbRanksPermissions.check(player, dottedNode);
        if (ftb.isPresent()) {
            return ftb.get();
        }
        return BukkitPermissionBridge.hasPermission(player, dottedNode);
    }

    private static boolean has(CommandSourceStack source, Predicate<ServerPlayer> playerCheck) {
        if (source.hasPermission(Config.ADMIN_PERMISSION_LEVEL.get())) {
            return true;
        }
        return source.getEntity() instanceof ServerPlayer p && playerCheck.test(p);
    }
}
