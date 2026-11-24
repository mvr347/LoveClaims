package me.lovelace.loveclaims.storage;

import me.lovelace.loveclaims.LoveClaims;
import me.lovelace.loveclaims.gems.GemStats;
import me.lovelace.loveclaims.model.Claim;
import me.lovelace.loveclaims.model.ClaimFlag;
import me.lovelace.loveclaims.model.ClaimPermission;
import me.lovelace.loveclaims.model.IndicatorType;
import me.lovelace.loveclaims.model.PlotType;
import me.lovelace.loveclaims.model.TrustLevel;
import me.lovelace.loveclaims.model.UserData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.BoundingBox;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class SQLiteStorage {
    private final LoveClaims plugin;
    private Connection claimsConnection;
    private Connection rentalsConnection;
    private final ExecutorService dbExecutor;

    /** claim_gems row index that holds the gem fused into the anchor (regular gems use 0, 1, 2...). */
    private static final int PERMANENT_GEM_SLOT = -1;

    private static final String CLAIMS_UPSERT =
            "INSERT INTO claims (id, world, min_x, min_y, min_z, max_x, max_y, max_z, owner_uuid, name, description, anchor_x, anchor_y, anchor_z, created_at, last_active, home_x, home_y, home_z, claim_type, is_clan_territory, is_under_siege, owner_display_name, default_permissions, anchor_tier_id, behavior_bonus_radius, admin_expand_bonus, applied_radius_bonus, extra_expansion_unlocked, anchor_broken) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                    "ON CONFLICT(id) DO UPDATE SET " +
                    "world=excluded.world, min_x=excluded.min_x, min_y=excluded.min_y, min_z=excluded.min_z, " +
                    "max_x=excluded.max_x, max_y=excluded.max_y, max_z=excluded.max_z, " +
                    "owner_uuid=excluded.owner_uuid, name=excluded.name, description=excluded.description, " +
                    "anchor_x=excluded.anchor_x, anchor_y=excluded.anchor_y, anchor_z=excluded.anchor_z, " +
                    "last_active=excluded.last_active, home_x=excluded.home_x, home_y=excluded.home_y, home_z=excluded.home_z, claim_type=excluded.claim_type, is_clan_territory=excluded.is_clan_territory, is_under_siege=excluded.is_under_siege, owner_display_name=excluded.owner_display_name, default_permissions=excluded.default_permissions, " +
                    "anchor_tier_id=excluded.anchor_tier_id, behavior_bonus_radius=excluded.behavior_bonus_radius, admin_expand_bonus=excluded.admin_expand_bonus, applied_radius_bonus=excluded.applied_radius_bonus, extra_expansion_unlocked=excluded.extra_expansion_unlocked, anchor_broken=excluded.anchor_broken";

    private static final String RENTALS_UPSERT =
            "INSERT INTO rentals (id, world, min_x, min_y, min_z, max_x, max_y, max_z, owner_uuid, name, description, anchor_x, anchor_y, anchor_z, created_at, last_active, home_x, home_y, home_z, rental_price, rental_end_time, parent_claim_id, indicator_type, hologram_id, last_tax_time, default_permissions, plot_type) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                    "ON CONFLICT(id) DO UPDATE SET " +
                    "world=excluded.world, min_x=excluded.min_x, min_y=excluded.min_y, min_z=excluded.min_z, " +
                    "max_x=excluded.max_x, max_y=excluded.max_y, max_z=excluded.max_z, " +
                    "owner_uuid=excluded.owner_uuid, name=excluded.name, description=excluded.description, " +
                    "anchor_x=excluded.anchor_x, anchor_y=excluded.anchor_y, anchor_z=excluded.anchor_z, " +
                    "last_active=excluded.last_active, home_x=excluded.home_x, home_y=excluded.home_y, home_z=excluded.home_z, " +
                    "rental_price=excluded.rental_price, rental_end_time=excluded.rental_end_time, parent_claim_id=excluded.parent_claim_id, indicator_type=excluded.indicator_type, hologram_id=excluded.hologram_id, last_tax_time=excluded.last_tax_time, default_permissions=excluded.default_permissions, plot_type=excluded.plot_type";

    public SQLiteStorage(LoveClaims plugin) {
        this.plugin = plugin;
        // Один shared Connection на файл БД не потокобезопасен для конкурентных запросов -
        // сериализуем все обращения к БД через единственный поток, чтобы избежать
        // "database is locked" и повреждения запросов при параллельных сохранениях.
        this.dbExecutor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>()) {
            @Override protected void terminated() {
                // shutdown atomically rejects new submissions; this runs only once all accepted
                // operations have finished, even if close() timed out or was interrupted.
                closeConnections();
            }
        };
    }

    public static String serializePermissions(Set<ClaimPermission> perms) {
        if (perms == null || perms.isEmpty()) return "";
        return perms.stream().map(Enum::name).collect(Collectors.joining(","));
    }

    public static Set<ClaimPermission> parsePermissions(String raw) {
        Set<ClaimPermission> set = EnumSet.noneOf(ClaimPermission.class);
        if (raw == null || raw.trim().isEmpty()) return set;
        String[] parts = raw.split(",");
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) continue;
            try {
                set.add(ClaimPermission.valueOf(trimmed));
            } catch (IllegalArgumentException e) {
                try {
                    TrustLevel tl = TrustLevel.valueOf(trimmed);
                    switch (tl) {
                        case OWNER, MANAGER -> set.addAll(EnumSet.allOf(ClaimPermission.class));
                        case BUILD -> {
                            set.add(ClaimPermission.BUILD);
                            set.add(ClaimPermission.CONTAINERS);
                            set.add(ClaimPermission.INTERACT);
                        }
                        case CONTAINER -> {
                            set.add(ClaimPermission.CONTAINERS);
                            set.add(ClaimPermission.INTERACT);
                        }
                        case ACCESS -> set.add(ClaimPermission.INTERACT);
                        case NONE -> {}
                    }
                } catch (IllegalArgumentException ignored) {}
            }
        }
        return set;
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public CompletableFuture<Void> initDatabase() {
        return CompletableFuture.runAsync(() -> {
            try {
                File storageDir = new File(plugin.getDataFolder(), "storage");
                if (!storageDir.exists()) {
                    storageDir.mkdirs();
                }

                File claimsFile = new File(storageDir, "ClaimsSQLITE.db");
                claimsConnection = DriverManager.getConnection("jdbc:sqlite:" + claimsFile.getAbsolutePath());
                configureConnection(claimsConnection);
                initClaimsTables(claimsConnection);

                File rentalsFile = new File(storageDir, "RentalsSQLITE.db");
                rentalsConnection = DriverManager.getConnection("jdbc:sqlite:" + rentalsFile.getAbsolutePath());
                configureConnection(rentalsConnection);
                initRentalsTables(rentalsConnection);
                migrateEconomy(rentalsConnection, rentalsFile);

            } catch (SQLException e) {
                throw new RuntimeException("DB Init failed", e);
            }
        }, dbExecutor);
    }

    /**
     * Rescales stored rental prices once when LoveCore's economy scale version grew
     * (see {@link EconomyMigration}); the rentals database file is copied first.
     */
    private void migrateEconomy(Connection conn, File dbFile) {
        try {
            var economy = dev.lovelace.lovecore.api.LoveCore.service(dev.lovelace.lovecore.api.economy.LoveEconomy.class);
            if (economy.isEmpty()) return;
            int target = economy.get().economyScaleVersion();
            double factor = plugin.getConfig().getDouble("economy.migration.factor", 5.0);
            if (EconomyMigration.needsRescale(conn, target)) {
                try (Statement st = conn.createStatement()) {
                    st.execute("PRAGMA wal_checkpoint(TRUNCATE)");
                }
                File backup = new File(dbFile.getParentFile(), dbFile.getName() + ".pre-economy-v" + target);
                if (!backup.exists()) {
                    java.nio.file.Files.copy(dbFile.toPath(), backup.toPath());
                    plugin.getLogger().info("Economy migration: database copy saved to " + backup.getName());
                }
            }
            EconomyMigration.migrate(conn, target, factor, plugin.getLogger());
        } catch (Throwable t) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "Economy migration failed - rental prices were NOT rescaled", t);
        }
    }

    private void configureConnection(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA journal_mode = WAL;");
            stmt.execute("PRAGMA synchronous = NORMAL;");
            stmt.execute("PRAGMA temp_store = MEMORY;");
            stmt.execute("PRAGMA foreign_keys = ON;");
        }
    }

    private void initClaimsTables(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS claims (id VARCHAR(36) PRIMARY KEY, world VARCHAR(64), min_x INT, min_y INT, min_z INT, max_x INT, max_y INT, max_z INT, owner_uuid VARCHAR(36), name VARCHAR(128), description TEXT, anchor_x INT, anchor_y INT, anchor_z INT, created_at BIGINT, last_active BIGINT, home_x INT, home_y INT, home_z INT, claim_type VARCHAR(32) DEFAULT 'PLAYER', is_clan_territory BOOLEAN DEFAULT FALSE, is_under_siege BOOLEAN DEFAULT FALSE, owner_display_name VARCHAR(64), default_permissions VARCHAR(128) DEFAULT '', anchor_tier_id VARCHAR(64), behavior_bonus_radius INT DEFAULT 0, admin_expand_bonus INT DEFAULT 0, applied_radius_bonus INT DEFAULT 0, extra_expansion_unlocked BOOLEAN DEFAULT FALSE, anchor_broken BOOLEAN DEFAULT FALSE);");
            stmt.execute("CREATE TABLE IF NOT EXISTS claim_members (claim_id VARCHAR(36), player_uuid VARCHAR(36), trust_level VARCHAR(64), PRIMARY KEY (claim_id, player_uuid), FOREIGN KEY(claim_id) REFERENCES claims(id) ON DELETE CASCADE);");
            stmt.execute("CREATE TABLE IF NOT EXISTS claim_flags (claim_id VARCHAR(36), flag_name VARCHAR(64), state BOOLEAN, PRIMARY KEY (claim_id, flag_name), FOREIGN KEY(claim_id) REFERENCES claims(id) ON DELETE CASCADE);");
            stmt.execute("CREATE TABLE IF NOT EXISTS claim_gems (claim_id VARCHAR(36), slot_index INT, gem_data TEXT, PRIMARY KEY (claim_id, slot_index), FOREIGN KEY(claim_id) REFERENCES claims(id) ON DELETE CASCADE);");

            // Добавление новых колонок, если их нет
            try { stmt.execute("ALTER TABLE claims ADD COLUMN claim_type VARCHAR(32) DEFAULT 'PLAYER';"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE claims ADD COLUMN is_clan_territory BOOLEAN DEFAULT FALSE;"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE claims ADD COLUMN is_under_siege BOOLEAN DEFAULT FALSE;"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE claims ADD COLUMN owner_display_name VARCHAR(64);"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE claims ADD COLUMN default_permissions VARCHAR(128) DEFAULT '';"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE claims ADD COLUMN anchor_tier_id VARCHAR(64);"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE claims ADD COLUMN behavior_bonus_radius INT DEFAULT 0;"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE claims ADD COLUMN admin_expand_bonus INT DEFAULT 0;"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE claims ADD COLUMN applied_radius_bonus INT DEFAULT 0;"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE claims ADD COLUMN extra_expansion_unlocked BOOLEAN DEFAULT FALSE;"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE claims ADD COLUMN anchor_broken BOOLEAN DEFAULT FALSE;"); } catch (SQLException ignored) {}

            stmt.execute("CREATE TABLE IF NOT EXISTS users (uuid VARCHAR(36) PRIMARY KEY, bonus_members INT, bonus_slots INT, show_proximity_border BOOLEAN DEFAULT TRUE);");
            try { stmt.execute("ALTER TABLE users ADD COLUMN show_proximity_border BOOLEAN DEFAULT TRUE;"); } catch (SQLException ignored) {}
            try { stmt.execute("DROP TABLE IF EXISTS user_quests;"); } catch (SQLException ignored) {}
            try { stmt.execute("DROP TABLE IF EXISTS user_buffs;"); } catch (SQLException ignored) {}
        }
    }

    private void initRentalsTables(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS rentals (id VARCHAR(36) PRIMARY KEY, world VARCHAR(64), min_x INT, min_y INT, min_z INT, max_x INT, max_y INT, max_z INT, owner_uuid VARCHAR(36), name VARCHAR(128), description TEXT, anchor_x INT, anchor_y INT, anchor_z INT, created_at BIGINT, last_active BIGINT, home_x INT, home_y INT, home_z INT, rental_price BIGINT DEFAULT 0, rental_end_time BIGINT DEFAULT 0, parent_claim_id VARCHAR(36), indicator_type VARCHAR(32) DEFAULT 'NONE', hologram_id VARCHAR(64), last_tax_time BIGINT DEFAULT 0, default_permissions VARCHAR(128) DEFAULT '');");
            stmt.execute("CREATE TABLE IF NOT EXISTS rental_members (rental_id VARCHAR(36), player_uuid VARCHAR(36), trust_level VARCHAR(64), PRIMARY KEY (rental_id, player_uuid), FOREIGN KEY(rental_id) REFERENCES rentals(id) ON DELETE CASCADE);");
            try { stmt.execute("ALTER TABLE rentals ADD COLUMN default_permissions VARCHAR(128) DEFAULT '';"); } catch (SQLException ignored) {}
            // Trade points (market stalls). Old rows keep RENTAL_PLOT via the column default;
            // the ALTER fails harmlessly when the column already exists.
            try { stmt.execute("ALTER TABLE rentals ADD COLUMN plot_type VARCHAR(32) DEFAULT 'RENTAL_PLOT';"); } catch (SQLException ignored) {}
        }
    }

    public CompletableFuture<List<Claim>> loadAllClaims() {
        return CompletableFuture.supplyAsync(() -> {
            List<Claim> allClaims = new ArrayList<>();
            allClaims.addAll(loadClaimsFromDB(claimsConnection, "claims", false));
            allClaims.addAll(loadClaimsFromDB(rentalsConnection, "rentals", true));
            return allClaims;
        }, dbExecutor);
    }

    private List<Claim> loadClaimsFromDB(Connection conn, String tableName, boolean isRental) {
        Map<UUID, Claim> claimsMap = new HashMap<>();
        Map<String, Integer> skippedWorlds = new HashMap<>();
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery("SELECT * FROM " + tableName)) {
            while (rs.next()) {
                World world = Bukkit.getWorld(rs.getString("world"));
                if (world == null) {
                    skippedWorlds.merge(String.valueOf(rs.getString("world")), 1, Integer::sum);
                    continue;
                }

                UUID id = UUID.fromString(rs.getString("id"));
                BoundingBox box = new BoundingBox(rs.getInt("min_x"), rs.getInt("min_y"), rs.getInt("min_z"), rs.getInt("max_x"), rs.getInt("max_y"), rs.getInt("max_z"));
                String ownerUuidStr = rs.getString("owner_uuid");
                UUID ownerUuid = ownerUuidStr != null ? UUID.fromString(ownerUuidStr) : null;

                Claim claim = new Claim(id, world, box, ownerUuid, new Location(world, rs.getInt("anchor_x"), rs.getInt("anchor_y"), rs.getInt("anchor_z")));

                claim.setName(rs.getString("name"));
                String desc = rs.getString("description"); if (desc != null) claim.setDescription(desc);

                int hx = rs.getInt("home_x");
                if (!rs.wasNull()) claim.setHomeLocation(new Location(world, hx, rs.getInt("home_y"), rs.getInt("home_z")));

                if (isRental) {
                    claim.setRentalPrice(rs.getLong("rental_price"));
                    claim.setRentalEndTime(rs.getLong("rental_end_time"));
                    String parentId = rs.getString("parent_claim_id");
                    if (parentId != null) claim.setParentClaimId(UUID.fromString(parentId));
                    claim.setIndicatorType(IndicatorType.valueOf(rs.getString("indicator_type")));
                    claim.setHologramId(rs.getString("hologram_id"));
                    claim.setLastTaxTime(rs.getLong("last_tax_time"));
                    claim.setPlotType(PlotType.parse(rs.getString("plot_type")));
                } else {
                    claim.setClaimType(Claim.ClaimType.valueOf(rs.getString("claim_type")));
                    claim.setClanTerritory(rs.getBoolean("is_clan_territory") || claim.getClaimType() == Claim.ClaimType.CLAN);
                    claim.setUnderSiege(rs.getBoolean("is_under_siege"));
                    claim.setOwnerDisplayName(rs.getString("owner_display_name"));
                    try {
                        claim.setAnchorTierId(rs.getString("anchor_tier_id"));
                        claim.setBehaviorBonusRadius(rs.getInt("behavior_bonus_radius"));
                        claim.setAdminExpandBonus(rs.getInt("admin_expand_bonus"));
                        claim.setAppliedRadiusBonus(rs.getInt("applied_radius_bonus"));
                        claim.setExtraExpansionUnlocked(rs.getBoolean("extra_expansion_unlocked"));
                        claim.setAnchorBroken(rs.getBoolean("anchor_broken"));
                    } catch (SQLException ignored) {}
                }

                try {
                    String defPerms = rs.getString("default_permissions");
                    if (defPerms != null && !defPerms.isEmpty()) {
                        claim.setDefaultPermissions(parsePermissions(defPerms));
                    }
                } catch (SQLException ignored) {}

                long lastActive = rs.getLong("last_active");
                if (lastActive > 0) claim.setLastActive(lastActive);

                claimsMap.put(id, claim);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error loading from " + tableName + ": " + e.getMessage());
        }
        if (!skippedWorlds.isEmpty()) {
            // 2026-11-07: a claim in a world that is not loaded yet is not lost; it is loaded when the world loads.
            plugin.getLogger().warning("Claims of worlds that are not loaded yet were skipped (" + tableName + "): "
                    + skippedWorlds + ". They are loaded when their world loads.");
        }

        String memberTable = isRental ? "rental_members" : "claim_members";
        String idColumn = isRental ? "rental_id" : "claim_id";

        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery("SELECT * FROM " + memberTable)) {
            while (rs.next()) {
                Claim claim = claimsMap.get(UUID.fromString(rs.getString(idColumn)));
                if (claim != null) {
                    claim.getMembers().put(UUID.fromString(rs.getString("player_uuid")), parsePermissions(rs.getString("trust_level")));
                }
            }
        } catch (SQLException e) { plugin.getLogger().severe("Error loading members (" + tableName + "): " + e.getMessage()); }

        if (!isRental) {
            String flagTable = "claim_flags";
            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery("SELECT * FROM " + flagTable)) {
                while (rs.next()) {
                    Claim claim = claimsMap.get(UUID.fromString(rs.getString(idColumn)));
                    if (claim != null) {
                        try { claim.setFlag(ClaimFlag.valueOf(rs.getString("flag_name")), rs.getInt("state") == 1); } catch (IllegalArgumentException ignored) {}
                    }
                }
            } catch (SQLException e) { plugin.getLogger().severe("Error loading flags (" + tableName + "): " + e.getMessage()); }

            if (plugin.getGemFactory() != null) {
                Map<UUID, List<GemStats>> gemsByClaim = new HashMap<>();
                Map<UUID, GemStats> permanentByClaim = new HashMap<>();
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT claim_id, slot_index, gem_data FROM claim_gems ORDER BY claim_id, slot_index ASC")) {
                    while (rs.next()) {
                        UUID cid = UUID.fromString(rs.getString("claim_id"));
                        boolean permanent = rs.getInt("slot_index") == PERMANENT_GEM_SLOT;
                        plugin.getGemFactory().deserializeStatsFromDb(rs.getString("gem_data"))
                                .ifPresent(gs -> {
                                    if (permanent) permanentByClaim.put(cid, gs);
                                    else gemsByClaim.computeIfAbsent(cid, k -> new ArrayList<>()).add(gs);
                                });
                    }
                } catch (SQLException e) {
                    plugin.getLogger().severe("Error loading claim gems: " + e.getMessage());
                }
                gemsByClaim.forEach((cid, list) -> {
                    Claim c = claimsMap.get(cid);
                    if (c != null) c.loadInstalledGems(list);
                });
                permanentByClaim.forEach((cid, gem) -> {
                    Claim c = claimsMap.get(cid);
                    if (c != null) c.setPermanentGem(gem);
                });
            }
        }

        claimsMap.values().forEach(c -> c.setModified(false));
        return new ArrayList<>(claimsMap.values());
    }

    public CompletableFuture<Claim> loadClaimAsync(UUID claimId) {
        return CompletableFuture.supplyAsync(() -> {
            Claim claim = loadSingleClaimFromDB(claimsConnection, "claims", "claim_members", "claim_flags", "claim_id", claimId, false);
            if (claim != null) {
                return claim;
            }
            return loadSingleClaimFromDB(rentalsConnection, "rentals", "rental_members", "rental_flags", "rental_id", claimId, true);
        }, dbExecutor);
    }

    private Claim loadSingleClaimFromDB(Connection conn, String tableName, String memberTable, String flagTable, String idColumn, UUID claimId, boolean isRental) {
        Claim claim = null;
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM " + tableName + " WHERE id = ?")) {
            ps.setString(1, claimId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    World world = Bukkit.getWorld(rs.getString("world"));
                    if (world == null) return null;

                    UUID id = UUID.fromString(rs.getString("id"));
                    BoundingBox box = new BoundingBox(rs.getInt("min_x"), rs.getInt("min_y"), rs.getInt("min_z"), rs.getInt("max_x"), rs.getInt("max_y"), rs.getInt("max_z"));
                    String ownerUuidStr = rs.getString("owner_uuid");
                    UUID ownerUuid = ownerUuidStr != null ? UUID.fromString(ownerUuidStr) : null;

                    claim = new Claim(id, world, box, ownerUuid, new Location(world, rs.getInt("anchor_x"), rs.getInt("anchor_y"), rs.getInt("anchor_z")));

                    claim.setName(rs.getString("name"));
                    String desc = rs.getString("description"); if (desc != null) claim.setDescription(desc);

                    int hx = rs.getInt("home_x");
                    if (!rs.wasNull()) claim.setHomeLocation(new Location(world, hx, rs.getInt("home_y"), rs.getInt("home_z")));

                    if (isRental) {
                        claim.setRentalPrice(rs.getLong("rental_price"));
                        claim.setRentalEndTime(rs.getLong("rental_end_time"));
                        String parentId = rs.getString("parent_claim_id");
                        if (parentId != null) claim.setParentClaimId(UUID.fromString(parentId));
                        claim.setIndicatorType(IndicatorType.valueOf(rs.getString("indicator_type")));
                        claim.setHologramId(rs.getString("hologram_id"));
                        claim.setLastTaxTime(rs.getLong("last_tax_time"));
                    claim.setPlotType(PlotType.parse(rs.getString("plot_type")));
                    } else {
                        claim.setClaimType(Claim.ClaimType.valueOf(rs.getString("claim_type")));
                        claim.setClanTerritory(rs.getBoolean("is_clan_territory") || claim.getClaimType() == Claim.ClaimType.CLAN);
                        claim.setUnderSiege(rs.getBoolean("is_under_siege"));
                        claim.setOwnerDisplayName(rs.getString("owner_display_name"));
                        try {
                            claim.setAnchorTierId(rs.getString("anchor_tier_id"));
                            claim.setBehaviorBonusRadius(rs.getInt("behavior_bonus_radius"));
                            claim.setAdminExpandBonus(rs.getInt("admin_expand_bonus"));
                            claim.setAppliedRadiusBonus(rs.getInt("applied_radius_bonus"));
                            claim.setExtraExpansionUnlocked(rs.getBoolean("extra_expansion_unlocked"));
                            claim.setAnchorBroken(rs.getBoolean("anchor_broken"));
                        } catch (SQLException ignored) {}
                    }

                    try {
                        String defPerms = rs.getString("default_permissions");
                        if (defPerms != null && !defPerms.isEmpty()) {
                            claim.setDefaultPermissions(parsePermissions(defPerms));
                        }
                    } catch (SQLException ignored) {}

                    long lastActive = rs.getLong("last_active");
                    if (lastActive > 0) claim.setLastActive(lastActive);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error loading single claim from " + tableName + ": " + e.getMessage());
            return null;
        }

        if (claim == null) {
            return null;
        }

        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM " + memberTable + " WHERE " + idColumn + " = ?")) {
            ps.setString(1, claimId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    claim.getMembers().put(UUID.fromString(rs.getString("player_uuid")), parsePermissions(rs.getString("trust_level")));
                }
            }
        } catch (SQLException e) { plugin.getLogger().severe("Error loading members for claim " + claimId + ": " + e.getMessage()); }

        if (!isRental) { // Флаги только для обычных клеймов
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM " + flagTable + " WHERE " + idColumn + " = ?")) {
                ps.setString(1, claimId.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        try { claim.setFlag(ClaimFlag.valueOf(rs.getString("flag_name")), rs.getInt("state") == 1); } catch (IllegalArgumentException ignored) {}
                    }
                }
            } catch (SQLException e) { plugin.getLogger().severe("Error loading flags for claim " + claimId + ": " + e.getMessage()); }

            if (plugin.getGemFactory() != null) {
                List<GemStats> gems = new ArrayList<>();
                GemStats[] permanentHolder = new GemStats[1];
                try (PreparedStatement ps = conn.prepareStatement("SELECT slot_index, gem_data FROM claim_gems WHERE claim_id = ? ORDER BY slot_index ASC")) {
                    ps.setString(1, claimId.toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            boolean permanent = rs.getInt("slot_index") == PERMANENT_GEM_SLOT;
                            plugin.getGemFactory().deserializeStatsFromDb(rs.getString("gem_data"))
                                    .ifPresent(g -> { if (permanent) permanentHolder[0] = g; else gems.add(g); });
                        }
                    }
                } catch (SQLException e) {
                    plugin.getLogger().severe("Error loading gems for claim " + claimId + ": " + e.getMessage());
                }
                claim.loadInstalledGems(gems);
                if (permanentHolder[0] != null) claim.setPermanentGem(permanentHolder[0]);
            }
        }

        claim.setModified(false);
        return claim;
    }

    public void saveClaimAsync(Claim claim) {
        CompletableFuture.runAsync(() -> {
            boolean isRental = claim.isRentalPlot();
            Connection conn = isRental ? rentalsConnection : claimsConnection;
            String sql = isRental ? RENTALS_UPSERT : CLAIMS_UPSERT;
            long version = claim.getModificationVersion();

            try {
                if (conn == null || conn.isClosed() || claim.getWorld() == null) return;
                conn.setAutoCommit(false);
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    prepareClaimStatement(ps, claim, isRental);
                    ps.executeUpdate();
                    if (!isRental) {
                        saveClaimGems(conn, claim);
                    }
                }
                conn.commit();
                claim.markSaved(version);
            } catch (SQLException e) {
                plugin.getLogger().severe("Error saving claim: " + e.getMessage());
                try { conn.rollback(); } catch (SQLException ignored) {}
            } finally {
                try { if (conn != null && !conn.isClosed()) conn.setAutoCommit(true); } catch (SQLException ignored) {}
            }
        }, dbExecutor);
    }

    public void batchSaveAllAsync(java.util.Collection<Claim> claims) {
        dbExecutor.execute(() -> {
            saveBatch(claimsConnection, CLAIMS_UPSERT, claims.stream().filter(c -> !c.isRentalPlot()).toList(), false);
            saveBatch(rentalsConnection, RENTALS_UPSERT, claims.stream().filter(Claim::isRentalPlot).toList(), true);
        });
    }

    private void saveBatch(Connection conn, String sql, List<Claim> claims, boolean isRental) {
        if (claims.isEmpty() || conn == null) return;
        Map<Claim, Long> pending = new java.util.HashMap<>();
        try {
            if (conn.isClosed()) return;
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (Claim claim : claims) {
                    if (!claim.isModified() || claim.getWorld() == null) continue;
                    long version = claim.getModificationVersion();
                    prepareClaimStatement(ps, claim, isRental);
                    ps.addBatch();
                    pending.put(claim, version);
                }
                if (!pending.isEmpty()) {
                    ps.executeBatch();
                    if (!isRental) {
                        for (Claim claim : pending.keySet()) {
                            saveClaimGems(conn, claim);
                        }
                    }
                }
            }
            conn.commit();
            // Clear the modified flag only after a successful commit, otherwise a failed batch loses changes.
            pending.forEach(Claim::markSaved);
        } catch (SQLException e) {
            plugin.getLogger().severe("Batch save error: " + e.getMessage());
            try { conn.rollback(); } catch (SQLException ignored) {}
        } finally {
            try { if (conn != null) conn.setAutoCommit(true); } catch (SQLException ignored) {}
        }
    }

    public void saveAllClaimsSync(Collection<Claim> claims) {
        // Выполняем внутри dbExecutor и ждём завершения: этот метод вызывается из onDisable
        // ДО остановки dbExecutor, пока в очереди ещё вполне может быть необработанный
        // async-запрос (например, saveClaimAsync от события за секунду до выключения). Прямой
        // вызов на текущем (главном) потоке работал бы с тем же Connection параллельно с
        // executor-потоком - Connection у SQLite JDBC не потокобезопасен для конкурентного
        // доступа из двух потоков одновременно, даже несмотря на WAL.
        runOnDbExecutorAndAwait(() -> {
            saveBatchSync(claimsConnection, CLAIMS_UPSERT, claims.stream().filter(c -> !c.isRentalPlot()).toList(), false);
            saveBatchSync(rentalsConnection, RENTALS_UPSERT, claims.stream().filter(Claim::isRentalPlot).toList(), true);
        });
    }

    /**
     * Выполнить задачу на dbExecutor и дождаться её завершения (с таймаутом), сохраняя
     * сериализацию доступа к Connection даже для "синхронных" вызовов со внешнего потока.
     * После начала остановки новые записи отклоняются: isShutdown не означает,
     * что рабочий поток уже закончил доступ к Connection.
     */
    private void runOnDbExecutorAndAwait(Runnable task) {
        try {
            dbExecutor.submit(task).get(15, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            plugin.getLogger().severe("Interrupted while waiting for synchronous DB save: " + e.getMessage());
        } catch (java.util.concurrent.ExecutionException e) {
            plugin.getLogger().severe("Synchronous DB save failed: " + e.getCause());
        } catch (java.util.concurrent.TimeoutException e) {
            plugin.getLogger().severe("Synchronous DB save timed out after 15s!");
        }
    }

    private void saveBatchSync(Connection conn, String sql, List<Claim> claims, boolean isRental) {
        if (claims.isEmpty() || conn == null) return;
        try {
            if (conn.isClosed()) return;
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (Claim claim : claims) {
                    if (claim.getWorld() == null) continue;
                    prepareClaimStatement(ps, claim, isRental);
                    ps.addBatch();
                }
                ps.executeBatch();
                if (!isRental) {
                    for (Claim claim : claims) {
                        if (claim.getWorld() != null) {
                            saveClaimGems(conn, claim);
                        }
                    }
                }
            }
            conn.commit();
            conn.setAutoCommit(true);
        } catch (SQLException e) {
            plugin.getLogger().severe("Sync save error: " + e.getMessage());
            try { conn.rollback(); conn.setAutoCommit(true); } catch (SQLException ignored) {}
        }
    }

    private void prepareClaimStatement(PreparedStatement ps, Claim claim, boolean isRental) throws SQLException {
        ps.setString(1, claim.getId().toString());
        ps.setString(2, claim.getWorld().getName());
        ps.setInt(3, (int) claim.getBoundingBox().getMinX());
        ps.setInt(4, (int) claim.getBoundingBox().getMinY());
        ps.setInt(5, (int) claim.getBoundingBox().getMinZ());
        ps.setInt(6, (int) claim.getBoundingBox().getMaxX());
        ps.setInt(7, (int) claim.getBoundingBox().getMaxY());
        ps.setInt(8, (int) claim.getBoundingBox().getMaxZ());
        ps.setString(9, claim.getOwnerUuid() != null ? claim.getOwnerUuid().toString() : null);
        ps.setString(10, claim.getName());
        ps.setString(11, claim.getDescription());
        ps.setInt(12, claim.getAnchorLocation().getBlockX());
        ps.setInt(13, claim.getAnchorLocation().getBlockY());
        ps.setInt(14, claim.getAnchorLocation().getBlockZ());
        ps.setLong(15, System.currentTimeMillis()); // CreatedAt (не храним в памяти, но в базе нужно)
        ps.setLong(16, claim.getLastActive());

        Location home = claim.getHomeLocation();
        if (home != null) {
            ps.setInt(17, home.getBlockX());
            ps.setInt(18, home.getBlockY());
            ps.setInt(19, home.getBlockZ());
        } else {
            ps.setNull(17, Types.INTEGER);
            ps.setNull(18, Types.INTEGER);
            ps.setNull(19, Types.INTEGER);
        }

        if (isRental) {
            ps.setLong(20, claim.getRentalPrice());
            ps.setLong(21, claim.getRentalEndTime());
            ps.setString(22, claim.getParentClaimId() != null ? claim.getParentClaimId().toString() : null);
            ps.setString(23, claim.getIndicatorType().name());
            ps.setString(24, claim.getHologramId());
            ps.setLong(25, claim.getLastTaxTime());
            ps.setString(26, serializePermissions(claim.getDefaultPermissions()));
            ps.setString(27, claim.getPlotType().name());
        } else {
            ps.setString(20, claim.getClaimType().name());
            ps.setBoolean(21, claim.isClanTerritory());
            ps.setBoolean(22, claim.isUnderSiege());
            ps.setString(23, claim.getOwnerDisplayName());
            ps.setString(24, serializePermissions(claim.getDefaultPermissions()));
            ps.setString(25, claim.getAnchorTierId());
            ps.setInt(26, claim.getBehaviorBonusRadius());
            ps.setInt(27, claim.getAdminExpandBonus());
            ps.setInt(28, claim.getAppliedRadiusBonus());
            ps.setBoolean(29, claim.isExtraExpansionUnlocked());
            ps.setBoolean(30, claim.isAnchorBroken());
        }
    }

    private void saveClaimGems(Connection conn, Claim claim) throws SQLException {
        if (plugin.getGemFactory() == null) return;
        try (PreparedStatement del = conn.prepareStatement("DELETE FROM claim_gems WHERE claim_id = ?")) {
            del.setString(1, claim.getId().toString());
            del.executeUpdate();
        }
        List<GemStats> gems = claim.getInstalledGems();
        GemStats permanent = claim.getPermanentGem();
        if (gems.isEmpty() && permanent == null) return;
        try (PreparedStatement ins = conn.prepareStatement("INSERT INTO claim_gems (claim_id, slot_index, gem_data) VALUES (?, ?, ?)")) {
            if (permanent != null) {
                ins.setString(1, claim.getId().toString());
                ins.setInt(2, PERMANENT_GEM_SLOT);
                ins.setString(3, plugin.getGemFactory().serializeStatsForDb(permanent));
                ins.addBatch();
            }
            for (int i = 0; i < gems.size(); i++) {
                ins.setString(1, claim.getId().toString());
                ins.setInt(2, i);
                ins.setString(3, plugin.getGemFactory().serializeStatsForDb(gems.get(i)));
                ins.addBatch();
            }
            ins.executeBatch();
        }
    }

    public void saveMemberPermissionsAsync(UUID claimId, UUID playerUuid, Set<ClaimPermission> perms) {
        boolean isRental = plugin.getClaimManager().getClaimById(claimId).map(Claim::isRentalPlot).orElse(false);
        Connection conn = isRental ? rentalsConnection : claimsConnection;
        String table = isRental ? "rental_members" : "claim_members";
        String idCol = isRental ? "rental_id" : "claim_id";

        CompletableFuture.runAsync(() -> {
            try (PreparedStatement ps = conn.prepareStatement("INSERT OR REPLACE INTO " + table + " (" + idCol + ", player_uuid, trust_level) VALUES (?, ?, ?)")) {
                ps.setString(1, claimId.toString());
                ps.setString(2, playerUuid.toString());
                ps.setString(3, serializePermissions(perms));
                ps.executeUpdate();
            } catch (SQLException e) { plugin.getLogger().severe("SQL Error: " + e.getMessage()); }
        }, dbExecutor);
    }

    public void saveMemberAsync(UUID claimId, UUID playerUuid, TrustLevel level) {
        Set<ClaimPermission> perms = EnumSet.noneOf(ClaimPermission.class);
        switch (level) {
            case OWNER, MANAGER -> perms.addAll(EnumSet.allOf(ClaimPermission.class));
            case BUILD -> {
                perms.add(ClaimPermission.BUILD);
                perms.add(ClaimPermission.CONTAINERS);
                perms.add(ClaimPermission.INTERACT);
            }
            case CONTAINER -> {
                perms.add(ClaimPermission.CONTAINERS);
                perms.add(ClaimPermission.INTERACT);
            }
            case ACCESS -> perms.add(ClaimPermission.INTERACT);
            case NONE -> {}
        }
        saveMemberPermissionsAsync(claimId, playerUuid, perms);
    }

    public void removeMemberAsync(UUID claimId, UUID playerUuid) {
        boolean isRental = plugin.getClaimManager().getClaimById(claimId).map(Claim::isRentalPlot).orElse(false);
        Connection conn = isRental ? rentalsConnection : claimsConnection;
        String table = isRental ? "rental_members" : "claim_members";
        String idCol = isRental ? "rental_id" : "claim_id";

        CompletableFuture.runAsync(() -> {
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM " + table + " WHERE " + idCol + " = ? AND player_uuid = ?")) {
                ps.setString(1, claimId.toString());
                ps.setString(2, playerUuid.toString());
                ps.executeUpdate();
            } catch (SQLException e) { plugin.getLogger().severe("SQL Error: " + e.getMessage()); }
        }, dbExecutor);
    }

    public void saveFlagAsync(UUID claimId, ClaimFlag flag, boolean state) {
        boolean isRental = plugin.getClaimManager().getClaimById(claimId).map(Claim::isRentalPlot).orElse(false);
        if (isRental) return;

        Connection conn = claimsConnection;
        String table = "claim_flags";
        String idCol = "claim_id";

        CompletableFuture.runAsync(() -> {
            try (PreparedStatement ps = conn.prepareStatement("INSERT OR REPLACE INTO " + table + " (" + idCol + ", flag_name, state) VALUES (?, ?, ?)")) {
                ps.setString(1, claimId.toString());
                ps.setString(2, flag.name());
                ps.setInt(3, state ? 1 : 0);
                ps.executeUpdate();
            } catch (SQLException e) { plugin.getLogger().severe("SQL Error: " + e.getMessage()); }
        }, dbExecutor);
    }

    public void deleteClaimAsync(UUID claimId) {
        boolean isRental = plugin.getClaimManager().getClaimById(claimId).map(Claim::isRentalPlot).orElse(false);
        Connection conn = isRental ? rentalsConnection : claimsConnection;
        String mainTable = isRental ? "rentals" : "claims";
        String memberTable = isRental ? "rental_members" : "claim_members";
        String idCol = isRental ? "rental_id" : "claim_id";

        CompletableFuture.runAsync(() -> {
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM " + memberTable + " WHERE " + idCol + " = ?")) {
                ps.setString(1, claimId.toString());
                ps.executeUpdate();
            } catch (SQLException ignored) {}

            if (!isRental) {
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM claim_flags WHERE claim_id = ?")) {
                    ps.setString(1, claimId.toString());
                    ps.executeUpdate();
                } catch (SQLException ignored) {}
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM claim_gems WHERE claim_id = ?")) {
                    ps.setString(1, claimId.toString());
                    ps.executeUpdate();
                } catch (SQLException ignored) {}
            }

            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM " + mainTable + " WHERE id = ?")) {
                ps.setString(1, claimId.toString());
                ps.executeUpdate();
            } catch (SQLException ignored) {}
        }, dbExecutor);
    }

    public CompletableFuture<UserData> loadUserData(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            UserData data = new UserData(uuid);
            try (PreparedStatement ps = claimsConnection.prepareStatement("SELECT * FROM users WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        data.setBonusMemberLimit(rs.getInt("bonus_members"));
                        data.setBonusClaimSlots(rs.getInt("bonus_slots"));
                        try {
                            data.setShowProximityBorder(rs.getBoolean("show_proximity_border"));
                        } catch (SQLException ignored) {}
                    }
                }
            } catch (SQLException e) { plugin.getLogger().severe("SQL Error: " + e.getMessage()); }
            return data;
        }, dbExecutor);
    }

    public void saveUserDataAsync(UserData data) {
        CompletableFuture.runAsync(() -> {
            try {
                if (claimsConnection == null || claimsConnection.isClosed()) return;
            } catch (SQLException e) {
                return;
            }

            try (PreparedStatement ps = claimsConnection.prepareStatement("INSERT OR REPLACE INTO users (uuid, bonus_members, bonus_slots, show_proximity_border) VALUES (?, ?, ?, ?)")) {
                ps.setString(1, data.getUuid().toString());
                ps.setInt(2, data.getBonusMemberLimit());
                ps.setInt(3, data.getBonusClaimSlots());
                ps.setBoolean(4, data.isShowProximityBorder());
                ps.executeUpdate();
            } catch (SQLException e) {
                if (e.getMessage() == null || !e.getMessage().contains("closed")) plugin.getLogger().severe("SQL Error: " + e.getMessage());
            }
        }, dbExecutor);
    }

    public void saveUserDataSync(UserData data) {
        runOnDbExecutorAndAwait(() -> saveUserDataInternal(data));
    }

    private void saveUserDataInternal(UserData data) {
        try {
            if (claimsConnection == null || claimsConnection.isClosed()) return;
        } catch (SQLException e) {
            return;
        }

        try (PreparedStatement ps = claimsConnection.prepareStatement("INSERT OR REPLACE INTO users (uuid, bonus_members, bonus_slots, show_proximity_border) VALUES (?, ?, ?, ?)")) {
            ps.setString(1, data.getUuid().toString());
            ps.setInt(2, data.getBonusMemberLimit());
            ps.setInt(3, data.getBonusClaimSlots());
            ps.setBoolean(4, data.isShowProximityBorder());
            ps.executeUpdate();
        } catch (SQLException e) { plugin.getLogger().severe("SQL Error: " + e.getMessage()); }
    }

    public void saveAllUserDataSync(java.util.Collection<me.lovelace.loveclaims.model.UserData> allData) {
        // См. комментарий в saveAllClaimsSync: сериализуем через dbExecutor, чтобы не трогать
        // claimsConnection параллельно с ещё не отработавшими async-задачами в очереди executor'а
        // во время onDisable.
        runOnDbExecutorAndAwait(() -> saveAllUserDataSyncInternal(allData));
    }

    private void saveAllUserDataSyncInternal(java.util.Collection<me.lovelace.loveclaims.model.UserData> allData) {
        if (claimsConnection == null) return;

        try {
            claimsConnection.setAutoCommit(false);
        } catch (SQLException ex) {
            plugin.getLogger().severe("Failed to start transaction: " + ex.getMessage());
            return;
        }

        try (PreparedStatement psUser = claimsConnection.prepareStatement("INSERT OR REPLACE INTO users (uuid, bonus_members, bonus_slots, show_proximity_border) VALUES (?, ?, ?, ?)")) {

            for (me.lovelace.loveclaims.model.UserData data : allData) {
                psUser.setString(1, data.getUuid().toString());
                psUser.setInt(2, data.getBonusMemberLimit());
                psUser.setInt(3, data.getBonusClaimSlots());
                psUser.setBoolean(4, data.isShowProximityBorder());
                psUser.addBatch();
            }

            psUser.executeBatch();
            claimsConnection.commit();

        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to save user data synchronously! " + e.getMessage());
            try {
                claimsConnection.rollback();
            } catch (SQLException rollbackEx) {
                plugin.getLogger().severe("Rollback failed: " + rollbackEx.getMessage());
            }
        } finally {
            try {
                claimsConnection.setAutoCommit(true);
            } catch (SQLException autoCommitEx) {
                plugin.getLogger().severe("AutoCommit reset failed: " + autoCommitEx.getMessage());
            }
        }
    }

    private void closeConnections() {
        try { if (claimsConnection != null) claimsConnection.close(); }
        catch (SQLException ex) { plugin.getLogger().warning("Closing claims database: " + ex.getMessage()); }
        try { if (rentalsConnection != null) rentalsConnection.close(); }
        catch (SQLException ex) { plugin.getLogger().warning("Closing rentals database: " + ex.getMessage()); }
    }

    public synchronized void close() {
        if (dbExecutor.isShutdown()) return;
        dbExecutor.shutdown();
        try {
            if (!dbExecutor.awaitTermination(15, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("Database queue is still draining; connections will close after pending writes.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            plugin.getLogger().warning("Interrupted waiting for DB shutdown; pending writes will finish before connections close.");
        }
    }
}
