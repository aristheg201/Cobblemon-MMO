package vn.svframe.svframemmo.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** MySQL-compatible JDBC backend with pooled, transactional snapshot writes. */
public final class MysqlPlayerDataStore implements PlayerDataStore {
    private final Gson gson = new GsonBuilder().create();
    private final HikariDataSource dataSource;
    private final String table;

    public MysqlPlayerDataStore(PersistenceConfig config) throws Exception {
        PersistenceConfig.Mysql mysql = config.mysql();
        if (!mysql.enabled()) throw new IllegalStateException("MySQL userdata backend is disabled in config.yml");
        this.table = mysql.table();
        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl("jdbc:mysql://" + mysql.host() + ':' + mysql.port() + '/' + mysql.database());
        hikari.setUsername(mysql.user());
        hikari.setPassword(mysql.pass());
        hikari.setMaximumPoolSize(mysql.maxPoolSize());
        hikari.setMaxLifetime(mysql.maxLifeTime());
        hikari.setConnectionTimeout(mysql.connectionTimeOut());
        if (mysql.leakDetectionThreshold() > 0) hikari.setLeakDetectionThreshold(mysql.leakDetectionThreshold());
        mysql.properties().forEach(hikari::addDataSourceProperty);
        hikari.setPoolName("SVFrameMMO-Userdata");
        this.dataSource = new HikariDataSource(hikari);
        setup();
    }

    @Override public String id() { return "MYSQL"; }

    private void setup() throws Exception {
        try (Connection connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS `" + table + "` (uuid VARCHAR(36) NOT NULL, data LONGTEXT NOT NULL, updated_at BIGINT NOT NULL, PRIMARY KEY (uuid))");
        }
    }

    @Override
    public Map<UUID, PlayerDataSnapshot> loadAll() throws Exception {
        LinkedHashMap<UUID, PlayerDataSnapshot> result = new LinkedHashMap<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT uuid,data FROM `" + table + "`");
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) result.put(UUID.fromString(rows.getString(1)), gson.fromJson(rows.getString(2), PlayerDataSnapshot.class));
        }
        return result;
    }

    @Override
    public void saveAll(Map<UUID, PlayerDataSnapshot> snapshots) throws Exception {
        upsert(snapshots);
    }

    @Override
    public void saveSome(Map<UUID, PlayerDataSnapshot> snapshots) throws Exception {
        if (snapshots == null || snapshots.isEmpty()) return;
        upsert(snapshots);
    }

    private void upsert(Map<UUID, PlayerDataSnapshot> snapshots) throws Exception {
        if (snapshots == null || snapshots.isEmpty()) return;
        try (Connection connection = dataSource.getConnection()) {
            boolean auto = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO `" + table + "` (uuid,data,updated_at) VALUES (?,?,?) ON DUPLICATE KEY UPDATE data=VALUES(data),updated_at=VALUES(updated_at)")) {
                long now = System.currentTimeMillis();
                for (Map.Entry<UUID, PlayerDataSnapshot> entry : snapshots.entrySet()) {
                    statement.setString(1, entry.getKey().toString());
                    statement.setString(2, gson.toJson(entry.getValue()));
                    statement.setLong(3, now);
                    statement.addBatch();
                }
                statement.executeBatch();
                connection.commit();
            } catch (Exception failure) {
                connection.rollback();
                throw failure;
            } finally { connection.setAutoCommit(auto); }
        }
    }

    @Override public void close() { dataSource.close(); }
}
