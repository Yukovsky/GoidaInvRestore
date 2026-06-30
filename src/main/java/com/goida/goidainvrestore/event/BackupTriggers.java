package com.goida.goidainvrestore.event;

import com.goida.goidainvrestore.Config;
import com.goida.goidainvrestore.GoidaInvRestore;
import com.goida.goidainvrestore.backup.BackupManager;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Triggers backups: on death (before items drop), on a periodic timer for every online player, and
 * optionally on logout. All backups are for online players only.
 */
@EventBusSubscriber(modid = GoidaInvRestore.MOD_ID)
public final class BackupTriggers {

    private static long tickCounter;

    private BackupTriggers() {}

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!Config.BACKUP_ON_DEATH.get()) return;
        if (event.getEntity() instanceof ServerPlayer player) {
            BackupManager.backup(player, BackupManager.DEATH);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!Config.BACKUP_ON_LOGOUT.get()) return;
        if (event.getEntity() instanceof ServerPlayer player) {
            BackupManager.backup(player, BackupManager.LOGOUT);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        long interval = Config.periodicIntervalTicks();
        if (interval <= 0 || !BackupManager.isReady()) return;
        if (++tickCounter % interval != 0) return;
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            BackupManager.backup(player, BackupManager.PERIODIC);
        }
    }
}
