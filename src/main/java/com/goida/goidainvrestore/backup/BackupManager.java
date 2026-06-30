package com.goida.goidainvrestore.backup;

import com.goida.goidainvrestore.Config;
import com.goida.goidainvrestore.GoidaInvRestore;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Central entry point for taking, listing and restoring inventory backups. Capture happens on the
 * server thread (it reads live player state); the actual disk/DB write is handed to a single
 * background thread so a large history never stalls the tick. Read/delete operations go straight to
 * the (thread-safe) backend.
 */
public final class BackupManager {

    public static final String DEATH = "DEATH";
    public static final String PERIODIC = "PERIODIC";
    public static final String MANUAL = "MANUAL";
    public static final String PRE_RESTORE = "PRE_RESTORE";
    public static final String LOGOUT = "LOGOUT";

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private static MinecraftServer server;
    private static StorageBackend backend;
    private static ExecutorService io;
    private static Path baseDir;
    private static final ConcurrentHashMap<UUID, String> lastHash = new ConcurrentHashMap<>();

    private BackupManager() {}

    public static void init(MinecraftServer srv) {
        server = srv;
        baseDir = srv.getWorldPath(LevelResource.ROOT).resolve("goidainvrestore");
        try {
            Files.createDirectories(baseDir);
        } catch (Exception e) {
            GoidaInvRestore.LOGGER.error("Could not create {}", baseDir, e);
        }
        io = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "GoidaInvRestore-IO");
            t.setDaemon(true);
            return t;
        });
        backend = createBackend();
    }

    private static StorageBackend createBackend() {
        if (Config.mysqlSelected()) {
            try {
                return MysqlBackend.create();
            } catch (Exception e) {
                GoidaInvRestore.LOGGER.error("MySQL backend init failed — falling back to file storage.", e);
            }
        }
        return new FileBackend(baseDir);
    }

    public static void shutdown() {
        if (io != null) {
            io.shutdown();
            try {
                io.awaitTermination(15, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
        if (backend != null) backend.close();
        lastHash.clear();
        server = null;
    }

    public static boolean isReady() {
        return server != null && backend != null;
    }

    public static HolderLookup.Provider provider() {
        return server.registryAccess();
    }

    // ---- Capture --------------------------------------------------------------------------

    /** Captures a backup. For {@link #PERIODIC} backups, skips when content is unchanged. */
    public static boolean backup(ServerPlayer player, String reason) {
        if (!isReady()) return false;
        InventorySnapshot snap = InventorySnapshot.capture(player, reason);
        UUID uuid = player.getUUID();
        String hash = snap.contentHash(provider());

        boolean enforceDedup = PERIODIC.equals(reason) && Config.DEDUP_PERIODIC.get();
        if (enforceDedup && hash.equals(lastHash.get(uuid))) {
            return false;
        }

        CompoundTag tag = snap.serializeNBT(provider());
        lastHash.put(uuid, hash);
        int max = Config.MAX_RECORDS_PER_PLAYER.get();

        if (Config.ASYNC_WRITES.get() && io != null) {
            io.submit(() -> backend.append(uuid, tag, max));
        } else {
            backend.append(uuid, tag, max);
        }
        return true;
    }

    // ---- Read / manage --------------------------------------------------------------------

    public static List<SnapshotMeta> list(UUID uuid) {
        return isReady() ? backend.list(uuid) : List.of();
    }

    public static InventorySnapshot loadSnapshot(UUID uuid, int index) {
        if (!isReady()) return null;
        CompoundTag tag = backend.load(uuid, index);
        return tag == null ? null : InventorySnapshot.load(tag, provider());
    }

    public static void delete(UUID uuid, int index) {
        if (isReady()) backend.delete(uuid, index);
    }

    public static void clear(UUID uuid) {
        if (isReady()) {
            backend.clear(uuid);
            lastHash.remove(uuid);
        }
    }

    // ---- Restore --------------------------------------------------------------------------

    /** Restores snapshot {@code index} onto {@code target}; takes a safety backup first if enabled. */
    public static boolean restore(CommandSourceStack actor, ServerPlayer target, int index, RestoreMode mode) {
        if (!isReady()) return false;
        InventorySnapshot snap = loadSnapshot(target.getUUID(), index);
        if (snap == null) return false;

        if (Config.PRE_RESTORE_BACKUP.get()) {
            backup(target, PRE_RESTORE);
        }
        snap.restore(target, mode);
        audit(actor, target, index, mode, snap);
        return true;
    }

    private static void audit(CommandSourceStack actor, ServerPlayer target, int index,
                              RestoreMode mode, InventorySnapshot snap) {
        String who = actor == null ? "console" : actor.getTextName();
        String line = String.format("%s | %s restored %s backup #%d (%s, %s, %d items) mode=%s",
                TIME_FMT.format(Instant.now()), who, target.getGameProfile().getName(), index,
                snap.reason(), TIME_FMT.format(Instant.ofEpochMilli(snap.timestamp())),
                snap.itemCount(), mode);
        GoidaInvRestore.LOGGER.info("[AUDIT] {}", line);
        try {
            Files.writeString(baseDir.resolve("audit.log"), line + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception e) {
            GoidaInvRestore.LOGGER.error("Failed to write audit log", e);
        }
    }

    public static String formatTime(long epochMillis) {
        return TIME_FMT.format(Instant.ofEpochMilli(epochMillis));
    }
}
