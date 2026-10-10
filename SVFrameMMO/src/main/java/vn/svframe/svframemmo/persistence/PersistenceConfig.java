package vn.svframe.svframemmo.persistence;

import vn.svframe.svframelib.config.YamlLite;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/** Storage-only configuration kept separate from gameplay config reload state. */
public record PersistenceConfig(Backend backend, String yamlDirectory, boolean autoMigrateJson, Mysql mysql) {
    public enum Backend { YAML, MYSQL, JSON }
    public record Mysql(boolean enabled, String host, int port, String database, String user, String pass,
                        int maxPoolSize, long maxLifeTime, long connectionTimeOut, long leakDetectionThreshold,
                        String table, Map<String, String> properties) { }

    public static PersistenceConfig load(Path file) throws Exception {
        Map<String, Object> root = YamlLite.map(YamlLite.parse(file));
        Map<String, Object> storage = map(root.get("storage"));
        Map<String, Object> mysql = map(root.get("mysql"));
        boolean mysqlEnabled = bool(mysql.get("enabled"), false);
        String configured = string(storage.get("backend"), mysqlEnabled ? "MYSQL" : "YAML").trim().toUpperCase();
        Backend backend = Backend.valueOf(configured);
        LinkedHashMap<String, String> properties = new LinkedHashMap<>();
        map(mysql.get("properties")).forEach((key, value) -> properties.put(key, String.valueOf(value)));
        Mysql mysqlConfig = new Mysql(mysqlEnabled,
                string(mysql.get("host"), "localhost"), integer(mysql.get("port"), 3306),
                string(mysql.get("database"), "minecraft"), string(mysql.get("user"), "root"), string(mysql.get("pass"), ""),
                Math.max(1, integer(first(mysql, "maxPoolSize", "max-pool-size"), 10)),
                Math.max(30_000L, longValue(first(mysql, "maxLifeTime", "max-life-time"), 300_000L)),
                Math.max(1_000L, longValue(first(mysql, "connectionTimeOut", "connection-timeout"), 10_000L)),
                Math.max(0L, longValue(first(mysql, "leakDetectionThreshold", "leak-detection-threshold"), 150_000L)),
                table(string(first(mysql, "userdata-table-name", "table"), "svframemmo_playerdata")), Map.copyOf(properties));
        if (backend == Backend.MYSQL && !mysqlEnabled)
            throw new IllegalArgumentException("storage.backend=MYSQL requires mysql.enabled=true");
        return new PersistenceConfig(backend, string(storage.get("yaml-directory"), "svframemmo-userdata"),
                bool(storage.get("auto-migrate-json"), true), mysqlConfig);
    }

    public Properties jdbcProperties() {
        Properties result = new Properties();
        result.putAll(mysql.properties());
        return result;
    }

    private static String table(String value) {
        if (!value.matches("[A-Za-z0-9_]+")) throw new IllegalArgumentException("Invalid userdata table name '" + value + "'");
        return value;
    }
    private static Object first(Map<String, Object> map, String... keys) { for (String key : keys) if (map.containsKey(key)) return map.get(key); return null; }
    private static Map<String, Object> map(Object raw) { if (!(raw instanceof Map<?, ?> source)) return Map.of(); LinkedHashMap<String,Object> out=new LinkedHashMap<>(); source.forEach((k,v)->out.put(String.valueOf(k),v)); return out; }
    private static String string(Object raw, String fallback) { return raw == null ? fallback : String.valueOf(raw); }
    private static int integer(Object raw, int fallback) { try { return raw instanceof Number n ? n.intValue() : raw == null ? fallback : Integer.parseInt(String.valueOf(raw)); } catch (RuntimeException ignored) { return fallback; } }
    private static long longValue(Object raw, long fallback) { try { return raw instanceof Number n ? n.longValue() : raw == null ? fallback : Long.parseLong(String.valueOf(raw)); } catch (RuntimeException ignored) { return fallback; } }
    private static boolean bool(Object raw, boolean fallback) { return raw instanceof Boolean b ? b : raw == null ? fallback : Boolean.parseBoolean(String.valueOf(raw)); }
}
