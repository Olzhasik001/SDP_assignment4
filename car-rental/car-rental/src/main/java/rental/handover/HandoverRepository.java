package rental.handover;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;

/** Persistence gateway of subsystem C (SQLite file handover.db). */
public class HandoverRepository {
    private final String url;

    public HandoverRepository(Path dir) { this(dir, 3); }

    public HandoverRepository(Path dir, int gpsDevices) {
        try { Files.createDirectories(dir); } catch (IOException e) { throw new IllegalStateException(e); }
        this.url = "jdbc:sqlite:" + dir.resolve("handover.db");
        try (Connection c = conn(); Statement s = c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS inspections(id INTEGER PRIMARY KEY AUTOINCREMENT, order_ref INTEGER, "
                    + "car_id INTEGER, damage_score INTEGER, status TEXT)");
            s.execute("CREATE TABLE IF NOT EXISTS gps_devices(id INTEGER PRIMARY KEY, serial TEXT, order_ref INTEGER)");
            int count;
            try (ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM gps_devices")) { rs.next(); count = rs.getInt(1); }
            if (count == 0) {
                for (int i = 1; i <= gpsDevices; i++) s.execute("INSERT INTO gps_devices VALUES (" + i + ",'GPS-00" + i + "',NULL)");
            }
        } catch (SQLException e) { throw new IllegalStateException("Handover DB init failed", e); }
    }

    private Connection conn() throws SQLException { return DriverManager.getConnection(url); }

    public Inspection insertInspection(long orderRef, long carId, int score) {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(
                "INSERT INTO inspections(order_ref,car_id,damage_score,status) VALUES(?,?,?,?)")) {
            ps.setLong(1, orderRef); ps.setLong(2, carId); ps.setInt(3, score); ps.setString(4, Inspection.Status.PENDING.name());
            ps.executeUpdate();
            try (Statement ks = c.createStatement(); ResultSet k = ks.executeQuery("SELECT last_insert_rowid()")) {
                k.next();
                return new Inspection(k.getLong(1), orderRef, carId, score, Inspection.Status.PENDING);
            }
        } catch (SQLException e) { throw new IllegalStateException(e); }
    }

    public Optional<Inspection> findInspection(long orderRef) {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement("SELECT * FROM inspections WHERE order_ref = ?")) {
            ps.setLong(1, orderRef);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(new Inspection(rs.getLong("id"), rs.getLong("order_ref"), rs.getLong("car_id"),
                        rs.getInt("damage_score"), Inspection.Status.valueOf(rs.getString("status")))) : Optional.empty();
            }
        } catch (SQLException e) { throw new IllegalStateException(e); }
    }

    public void updateInspection(Inspection i) {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement("UPDATE inspections SET status = ? WHERE id = ?")) {
            ps.setString(1, i.getStatus().name()); ps.setLong(2, i.getId()); ps.executeUpdate();
        } catch (SQLException e) { throw new IllegalStateException(e); }
    }

    /** Assigns the first free device to the order; returns its serial or empty if none is free. */
    public Optional<String> assignFreeDevice(long orderRef) {
        try (Connection c = conn()) {
            String serial = null; long id = -1;
            try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery(
                    "SELECT id, serial FROM gps_devices WHERE order_ref IS NULL ORDER BY id LIMIT 1")) {
                if (rs.next()) { id = rs.getLong("id"); serial = rs.getString("serial"); }
            }
            if (serial == null) return Optional.empty();
            try (PreparedStatement ps = c.prepareStatement("UPDATE gps_devices SET order_ref = ? WHERE id = ?")) {
                ps.setLong(1, orderRef); ps.setLong(2, id); ps.executeUpdate();
            }
            return Optional.of(serial);
        } catch (SQLException e) { throw new IllegalStateException(e); }
    }

    public void freeDevice(long orderRef) {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement("UPDATE gps_devices SET order_ref = NULL WHERE order_ref = ?")) {
            ps.setLong(1, orderRef); ps.executeUpdate();
        } catch (SQLException e) { throw new IllegalStateException(e); }
    }

    public Optional<String> findDevice(long orderRef) {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement("SELECT serial FROM gps_devices WHERE order_ref = ?")) {
            ps.setLong(1, orderRef);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? Optional.of(rs.getString(1)) : Optional.empty(); }
        } catch (SQLException e) { throw new IllegalStateException(e); }
    }
}
