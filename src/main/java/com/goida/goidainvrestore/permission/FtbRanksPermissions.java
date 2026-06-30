package com.goida.goidainvrestore.permission;

import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Resolves a player's permission through FTB Ranks, if that mod is installed.
 *
 * <p>On NeoForge FTB Ranks does <b>not</b> register a {@code PermissionAPI} handler: it only wraps
 * Brigadier command predicates ({@code command.*} nodes) and is otherwise reachable solely through
 * its {@code FTBRanksAPI}. So a permission check that is not tied to running a command (a GUI button
 * click, a restore action, …) is only seen by FTB Ranks if we ask its API directly. We do that
 * reflectively to avoid a compile/runtime dependency.
 *
 * <p>Returns {@link Optional#empty()} when FTB Ranks is absent or the player's rank has no explicit
 * value for the node — the caller then falls back to its normal logic (op level, Bukkit bridge).
 */
public final class FtbRanksPermissions {

    private static volatile boolean absent = false;
    private static volatile Method getPermissionValue; // FTBRanksAPI#getPermissionValue(ServerPlayer, String)
    private static volatile Method isEmpty;             // PermissionValue#isEmpty()
    private static volatile Method asBoolean;           // PermissionValue#asBoolean() -> Optional<Boolean>

    private FtbRanksPermissions() {}

    private static boolean init() {
        if (getPermissionValue != null) return true;
        if (absent) return false;
        try {
            Class<?> api = Class.forName("dev.ftb.mods.ftbranks.api.FTBRanksAPI");
            Class<?> value = Class.forName("dev.ftb.mods.ftbranks.api.PermissionValue");
            Method gpv = api.getMethod("getPermissionValue", ServerPlayer.class, String.class);
            isEmpty = value.getMethod("isEmpty");
            asBoolean = value.getMethod("asBoolean");
            getPermissionValue = gpv; // set last: readiness flag
            return true;
        } catch (Throwable t) {
            absent = true;
            return false;
        }
    }

    /**
     * @return the explicit allow/deny from the player's FTB Ranks rank, or {@link Optional#empty()}
     *         if FTB Ranks is absent or the node is unset for that rank.
     */
    @SuppressWarnings("unchecked")
    public static Optional<Boolean> check(ServerPlayer player, String node) {
        if (player == null || !init()) return Optional.empty();
        try {
            Object value = getPermissionValue.invoke(null, player, node);
            if (value == null || Boolean.TRUE.equals(isEmpty.invoke(value))) return Optional.empty();
            return (Optional<Boolean>) asBoolean.invoke(value);
        } catch (Throwable ignored) {
            return Optional.empty();
        }
    }
}
