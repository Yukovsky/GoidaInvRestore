package com.goida.goidainvrestore;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server config ({@code goidainvrestore-server.toml}). All values are read live via {@code .get()}.
 */
public final class Config {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // ---- Backup scheduling ----------------------------------------------------------------

    public static final ModConfigSpec.IntValue PERIODIC_INTERVAL_MINUTES = BUILDER
            .comment("Minutes between automatic periodic backups of each online player. 0 disables periodic backups.")
            .defineInRange("backup.periodicIntervalMinutes", 30, 0, 7 * 24 * 60);

    public static final ModConfigSpec.IntValue MAX_RECORDS_PER_PLAYER = BUILDER
            .comment("Maximum number of backup records kept per player. Oldest are dropped (ring buffer).")
            .defineInRange("backup.maxRecordsPerPlayer", 20, 1, 100_000);

    public static final ModConfigSpec.BooleanValue BACKUP_ON_DEATH = BUILDER
            .comment("Take a backup whenever a player dies (captured before items drop).")
            .define("backup.onDeath", true);

    public static final ModConfigSpec.BooleanValue BACKUP_ON_LOGOUT = BUILDER
            .comment("Take a backup whenever a player logs out.")
            .define("backup.onLogout", false);

    public static final ModConfigSpec.BooleanValue DEDUP_PERIODIC = BUILDER
            .comment("Skip a periodic backup if the inventory is unchanged since the last backup (saves space).")
            .define("backup.dedupPeriodic", true);

    public static final ModConfigSpec.BooleanValue PRE_RESTORE_BACKUP = BUILDER
            .comment("Automatically take a safety backup of the current inventory before any restore (makes restores reversible).")
            .define("backup.preRestoreBackup", true);

    public static final ModConfigSpec.BooleanValue ASYNC_WRITES = BUILDER
            .comment("Write backups to disk/DB on a background thread to avoid stalling the server tick.")
            .define("backup.asyncWrites", true);

    // ---- Capture scope --------------------------------------------------------------------

    public static final ModConfigSpec.BooleanValue CAPTURE_ENDER_CHEST = BUILDER
            .comment("Include the ender chest in backups.")
            .define("capture.enderChest", true);

    public static final ModConfigSpec.BooleanValue CAPTURE_CURIOS = BUILDER
            .comment("Include Curios / Cosmetic Armor slots in backups (when those mods are installed).")
            .define("capture.curiosAndCosmetic", true);

    public static final ModConfigSpec.BooleanValue CAPTURE_XP = BUILDER
            .comment("Include experience (level + progress) in backups and restore it on OVERWRITE restores.")
            .define("capture.experience", true);

    // ---- GUI / permissions ----------------------------------------------------------------

    public static final ModConfigSpec.IntValue ADMIN_PERMISSION_LEVEL = BUILDER
            .comment("Vanilla op level required for admin actions when no permission mod overrides the nodes.")
            .defineInRange("permissions.adminLevel", 2, 0, 4);

    // ---- Storage backend ------------------------------------------------------------------

    public static final ModConfigSpec.ConfigValue<String> STORAGE = BUILDER
            .comment("Storage backend: \"file\" (per-player .dat files in the world folder) or \"mysql\".")
            .define("storage.backend", "file");

    public static final ModConfigSpec.ConfigValue<String> MYSQL_HOST = BUILDER
            .define("storage.mysql.host", "localhost");
    public static final ModConfigSpec.IntValue MYSQL_PORT = BUILDER
            .defineInRange("storage.mysql.port", 3306, 1, 65535);
    public static final ModConfigSpec.ConfigValue<String> MYSQL_DATABASE = BUILDER
            .define("storage.mysql.database", "goidainvrestore");
    public static final ModConfigSpec.ConfigValue<String> MYSQL_USER = BUILDER
            .define("storage.mysql.user", "root");
    public static final ModConfigSpec.ConfigValue<String> MYSQL_PASSWORD = BUILDER
            .define("storage.mysql.password", "");
    public static final ModConfigSpec.ConfigValue<String> MYSQL_TABLE_PREFIX = BUILDER
            .define("storage.mysql.tablePrefix", "goidainvrestore_");
    public static final ModConfigSpec.BooleanValue MYSQL_USE_SSL = BUILDER
            .define("storage.mysql.useSsl", false);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {}

    public static boolean mysqlSelected() {
        return "mysql".equalsIgnoreCase(STORAGE.get());
    }

    public static long periodicIntervalTicks() {
        return PERIODIC_INTERVAL_MINUTES.get() * 60L * 20L;
    }
}
