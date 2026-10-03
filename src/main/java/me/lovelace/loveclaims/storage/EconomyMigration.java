package me.lovelace.loveclaims.storage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * One-time rescale of {@code rentals.rental_price} when LoveCore's {@code economy.scale-version} grows.
 * The version the data was written under is kept in {@code economy_meta}; the rescale and the version
 * write share one transaction, so the migration can never be applied twice.
 */
public final class EconomyMigration {

    private static final String META_KEY = "scale_version";

    private EconomyMigration() {
    }

    /** True when the stored version is lower than the target (or absent) and some rental has a price. */
    public static boolean needsRescale(Connection conn, int targetVersion) throws SQLException {
        ensureMeta(conn);
        Integer stored = readVersion(conn);
        if (stored != null && stored >= targetVersion) return false;
        if (!tableExists(conn, "rentals")) return false;
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT 1 FROM rentals WHERE rental_price > 0 LIMIT 1")) {
            return rs.next();
        }
    }

    /** @return number of rows rescaled. */
    public static int migrate(Connection conn, int targetVersion, double factor, Logger log) throws SQLException {
        ensureMeta(conn);
        Integer stored = readVersion(conn);
        if (stored != null && stored >= targetVersion) return 0;

        boolean wasAuto = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            int from = stored != null ? stored : 1;
            int rows = 0;
            if (from < targetVersion && factor > 0 && factor != 1.0 && tableExists(conn, "rentals")) {
                double mult = Math.pow(factor, targetVersion - from);
                List<String> ids = new ArrayList<>();
                List<Long> prices = new ArrayList<>();
                try (Statement st = conn.createStatement();
                     ResultSet rs = st.executeQuery("SELECT id, rental_price FROM rentals WHERE rental_price > 0")) {
                    while (rs.next()) {
                        ids.add(rs.getString(1));
                        prices.add(rs.getLong(2));
                    }
                }
                try (PreparedStatement ps = conn.prepareStatement("UPDATE rentals SET rental_price = ? WHERE id = ?")) {
                    for (int i = 0; i < ids.size(); i++) {
                        ps.setLong(1, Math.max(1L, Math.round(prices.get(i) * mult)));
                        ps.setString(2, ids.get(i));
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                rows = ids.size();
            }
            writeVersion(conn, targetVersion);
            conn.commit();
            if (rows > 0) {
                log.info("Economy migration v" + from + " -> v" + targetVersion + ": rescaled " + rows
                        + " rental prices (x" + factor + " per step)");
            }
            return rows;
        } catch (SQLException | RuntimeException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(wasAuto);
        }
    }

    private static void ensureMeta(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS economy_meta (meta_key VARCHAR(64) PRIMARY KEY, meta_value VARCHAR(64) NOT NULL)");
        }
    }

    private static boolean tableExists(Connection conn, String table) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static Integer readVersion(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT meta_value FROM economy_meta WHERE meta_key = ?")) {
            ps.setString(1, META_KEY);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                try {
                    return Integer.parseInt(rs.getString(1).trim());
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }
    }

    private static void writeVersion(Connection conn, int version) throws SQLException {
        try (PreparedStatement del = conn.prepareStatement("DELETE FROM economy_meta WHERE meta_key = ?")) {
            del.setString(1, META_KEY);
            del.executeUpdate();
        }
        try (PreparedStatement ins = conn.prepareStatement("INSERT INTO economy_meta (meta_key, meta_value) VALUES (?, ?)")) {
            ins.setString(1, META_KEY);
            ins.setString(2, String.valueOf(version));
            ins.executeUpdate();
        }
    }
}
