package com.goida.goidainvrestore;

import com.goida.goidainvrestore.backup.BackupManager;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;

/**
 * GoidaInvRestore — a fully server-side inventory backup &amp; restore system.
 *
 * <p>Takes periodic and on-death snapshots of each online player's inventory (main/armor/offhand,
 * ender chest, Curios/Cosmetic Armor, XP), keeps a capped per-player history, and lets admins
 * browse and restore them through a paginated vanilla chest GUI. Item fidelity (including modded
 * containers such as Sophisticated Backpacks) is preserved via DataComponent-aware NBT.
 */
@Mod(GoidaInvRestore.MOD_ID)
public final class GoidaInvRestore {

    public static final String MOD_ID = "goidainvrestore";
    public static final Logger LOGGER = LogUtils.getLogger();

    public GoidaInvRestore(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, Config.SPEC);
        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);
        LOGGER.info("GoidaInvRestore loaded (server-side).");
    }

    private void onServerStarting(ServerStartingEvent event) {
        BackupManager.init(event.getServer());
    }

    private void onServerStopping(ServerStoppingEvent event) {
        BackupManager.shutdown();
    }
}
