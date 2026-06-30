package com.goida.goidainvrestore.backup;

import com.goida.goidainvrestore.Config;
import com.goida.goidainvrestore.GoidaInvRestore;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

/**
 * MySQL-backed history.
 *
 * <p>The MySQL Connector/J driver is bundled inside this mod jar at {@code /META-INF/jars/} and is
 * loaded here in its <b>own isolated {@link java.net.URLClassLoader}</b> whose parent is the JDK
 * platform loader. That keeps our copy of {@code com.mysql.*} in a separate classloader from any
 * other mod that also bundles Connector/J — the two can never clash — while the driver still works
 * standalone. We obtain connections through the bundled {@link Driver} directly (never the global
 * {@link java.sql.DriverManager}), so there is no SPI registration that other mods could collide with.
 *
 * <p>All JDBC interaction afterwards uses only the JDK {@code java.sql.*} interfaces (shared via the
 * platform loader), so this class compiles against the JDK alone.
 */
public final class MysqlBackend implements StorageBackend {

    private final String table;
    private final String url;
    private final Properties props;
    private Driver driver;
    private Connection connection;

    private MysqlBackend(Driver driver, String url, Properties props, String table) {
        this.driver = driver;
        this.url = url;
        this.props = props;
        this.table = table;
    }

    /** Builds and verifies a MySQL backend, or throws if the driver/connection cannot be set up. */
    public static MysqlBackend create() throws Exception {
        Driver driver = loadBundledDriver();

        String host = Config.MYSQL_HOST.get();
        int port = Config.MYSQL_PORT.get();
        String db = Config.MYSQL_DATABASE.get();
        boolean ssl = Config.MYSQL_USE_SSL.get();
        String url = "jdbc:mysql://" + host + ":" + port + "/" + db
                + "?createDatabaseIfNotExist=true&useSSL=" + ssl
                + "&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8";

        Properties props = new Properties();
        props.setProperty("user", Config.MYSQL_USER.get());
        props.setProperty("password", Config.MYSQL_PASSWORD.get());

        String table = sanitizePrefix(Config.MYSQL_TABLE_PREFIX.get()) + "backups";
        MysqlBackend backend = new MysqlBackend(driver, url, props, table);
        backend.connection = backend.openConnection();
        backend.createSchema();
        GoidaInvRestore.LOGGER.info("MySQL storage backend connected ({}:{}/{}, table {}).", host, port, db, table);
        return backend;
    }

    private static Driver loadBundledDriver() throws Exception {
        try (InputStream in = MysqlBackend.class.getResourceAsStream("/META-INF/jars/mysql-connector-j.jar")) {
            if (in == null) {
                throw new IllegalStateException("Bundled MySQL Connector/J jar not found on classpath");
            }
            Path tmp = Files.createTempFile("goidainvrestore-mysql", ".jar");
            tmp.toFile().deleteOnExit();
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);

            // Parent = platform loader: shares java.sql.* with the JDK, isolated from the mod loader.
            java.net.URLClassLoader loader = new java.net.URLClassLoader(
                    new java.net.URL[]{tmp.toUri().toURL()}, ClassLoader.getPlatformClassLoader());
            Class<?> driverClass = Class.forName("com.mysql.cj.jdbc.Driver", true, loader);
            return (Driver) driverClass.getDeclaredConstructor().newInstance();
        }
    }

    private static String sanitizePrefix(String prefix) {
        String p = prefix == null ? "" : prefix.replaceAll("[^A-Za-z0-9_]", "");
        return p.isEmpty() ? "goidainvrestore_" : p;
    }

    private synchronized Connection openConnection() throws Exception {
        Connection c = driver.connect(url, props);
        if (c == null) {
            throw new IllegalStateException("MySQL driver rejected the connection URL");
        }
        return c;
    }

    private synchronized Connection conn() throws Exception {
        if (connection == null || connection.isClosed() || !connection.isValid(2)) {
            connection = openConnection();
        }
        return connection;
    }

    private void createSchema() throws Exception {
        String sql = "CREATE TABLE IF NOT EXISTS " + table + " ("
                + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                + "uuid CHAR(36) NOT NULL,"
                + "created_at BIGINT NOT NULL,"
                + "reason VARCHAR(32),"
                + "dimension VARCHAR(255),"
                + "xp_level INT,"
                + "total_xp INT,"
                + "item_count INT,"
                + "data MEDIUMBLOB NOT NULL,"
                + "INDEX idx_uuid_created (uuid, created_at)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        try (Statement st = conn().createStatement()) {
            st.executeUpdate(sql);
        }
    }

    @Override
    public synchronized void append(UUID uuid, CompoundTag snapshot, int maxRecords) {
        try {
            byte[] data = compress(snapshot);
            String insert = "INSERT INTO " + table
                    + " (uuid, created_at, reason, dimension, xp_level, total_xp, item_count, data) "
                    + "VALUES (?,?,?,?,?,?,?,?)";
            try (PreparedStatement ps = conn().prepareStatement(insert)) {
                ps.setString(1, uuid.toString());
                ps.setLong(2, snapshot.getLong("timestamp"));
                ps.setString(3, snapshot.getString("reason"));
                ps.setString(4, snapshot.getString("dimension"));
                ps.setInt(5, snapshot.getInt("xpLevel"));
                ps.setInt(6, snapshot.getInt("totalXp"));
                ps.setInt(7, snapshot.getInt("itemCount"));
                ps.setBytes(8, data);
                ps.executeUpdate();
            }
            trim(uuid, maxRecords);
        } catch (Exception e) {
            GoidaInvRestore.LOGGER.error("MySQL append failed for {}", uuid, e);
        }
    }

    private void trim(UUID uuid, int maxRecords) throws Exception {
        String sql = "DELETE FROM " + table + " WHERE uuid=? AND id NOT IN "
                + "(SELECT id FROM (SELECT id FROM " + table
                + " WHERE uuid=? ORDER BY created_at DESC, id DESC LIMIT ?) keep)";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, uuid.toString());
            ps.setInt(3, maxRecords);
            ps.executeUpdate();
        }
    }

    @Override
    public synchronized List<SnapshotMeta> list(UUID uuid) {
        List<SnapshotMeta> out = new ArrayList<>();
        String sql = "SELECT id, created_at, reason, dimension, xp_level, total_xp, item_count FROM "
                + table + " WHERE uuid=? ORDER BY created_at ASC, id ASC";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new SnapshotMeta(
                            (int) rs.getLong("id"),
                            rs.getLong("created_at"),
                            rs.getString("reason"),
                            rs.getString("dimension"),
                            rs.getInt("xp_level"),
                            rs.getInt("total_xp"),
                            rs.getInt("item_count")));
                }
            }
        } catch (Exception e) {
            GoidaInvRestore.LOGGER.error("MySQL list failed for {}", uuid, e);
        }
        return out;
    }

    @Override
    public synchronized CompoundTag load(UUID uuid, int index) {
        String sql = "SELECT data FROM " + table + " WHERE id=? AND uuid=?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, index);
            ps.setString(2, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return decompress(rs.getBytes("data"));
                }
            }
        } catch (Exception e) {
            GoidaInvRestore.LOGGER.error("MySQL load failed for {} #{}", uuid, index, e);
        }
        return null;
    }

    @Override
    public synchronized void delete(UUID uuid, int index) {
        try (PreparedStatement ps = conn().prepareStatement(
                "DELETE FROM " + table + " WHERE id=? AND uuid=?")) {
            ps.setInt(1, index);
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
        } catch (Exception e) {
            GoidaInvRestore.LOGGER.error("MySQL delete failed for {} #{}", uuid, index, e);
        }
    }

    @Override
    public synchronized void clear(UUID uuid) {
        try (PreparedStatement ps = conn().prepareStatement("DELETE FROM " + table + " WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (Exception e) {
            GoidaInvRestore.LOGGER.error("MySQL clear failed for {}", uuid, e);
        }
    }

    @Override
    public synchronized void close() {
        try {
            if (connection != null && !connection.isClosed()) connection.close();
        } catch (Exception ignored) {
        }
    }

    private static byte[] compress(CompoundTag tag) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        NbtIo.writeCompressed(tag, bos);
        return bos.toByteArray();
    }

    private static CompoundTag decompress(byte[] data) throws Exception {
        return NbtIo.readCompressed(new ByteArrayInputStream(data), NbtAccounter.unlimitedHeap());
    }
}
