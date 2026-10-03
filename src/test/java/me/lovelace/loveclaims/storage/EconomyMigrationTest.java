package me.lovelace.loveclaims.storage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class EconomyMigrationTest {

    private static final Logger LOG = Logger.getLogger("test");
    private Connection conn;

    @BeforeEach
    void setUp() throws Exception {
        conn = DriverManager.getConnection("jdbc:sqlite::memory:");
        try (Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE rentals (id VARCHAR(36) PRIMARY KEY, rental_price BIGINT DEFAULT 0)");
            st.execute("INSERT INTO rentals VALUES ('a', 3000), ('b', 0), ('c', 7)");
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        conn.close();
    }

    private long price(String id) throws Exception {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT rental_price FROM rentals WHERE id = '" + id + "'")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    @Test
    void rescalesPricesOnceAndSkipsZero() throws Exception {
        assertTrue(EconomyMigration.needsRescale(conn, 2));
        assertEquals(2, EconomyMigration.migrate(conn, 2, 5.0, LOG));
        assertEquals(15_000, price("a"));
        assertEquals(0, price("b"));
        assertEquals(35, price("c"));
        assertFalse(EconomyMigration.needsRescale(conn, 2));
        assertEquals(0, EconomyMigration.migrate(conn, 2, 5.0, LOG));
        assertEquals(15_000, price("a"));
    }

    @Test
    void emptyTableOnlyRecordsVersion() throws Exception {
        try (Statement st = conn.createStatement()) {
            st.execute("DELETE FROM rentals");
        }
        assertFalse(EconomyMigration.needsRescale(conn, 2));
        assertEquals(0, EconomyMigration.migrate(conn, 2, 5.0, LOG));
        try (Statement st = conn.createStatement()) {
            st.execute("INSERT INTO rentals VALUES ('z', 10)");
        }
        assertEquals(1, EconomyMigration.migrate(conn, 3, 5.0, LOG));
        assertEquals(50, price("z"));
    }

    @Test
    void missingTableIsFine() throws Exception {
        try (Statement st = conn.createStatement()) {
            st.execute("DROP TABLE rentals");
        }
        assertFalse(EconomyMigration.needsRescale(conn, 2));
        assertEquals(0, EconomyMigration.migrate(conn, 2, 5.0, LOG));
    }
}
