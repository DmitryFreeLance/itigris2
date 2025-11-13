package com.example.bot.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class Database {
    private static final Logger log = LoggerFactory.getLogger(Database.class);
    private final String url;

    public Database(String dbPath) {
        this.url = "jdbc:sqlite:" + dbPath;
    }

    public void init() {
        try (Connection conn = DriverManager.getConnection(url);
             Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS users (" +
                    "chat_id INTEGER PRIMARY KEY," +
                    "username TEXT," +
                    "first_name TEXT," +
                    "last_name TEXT," +
                    "is_admin INTEGER DEFAULT 0," +
                    "tag TEXT DEFAULT 'basic'," +
                    "subscription_end TEXT," +
                    "subscription_active INTEGER DEFAULT 0," +
                    "created_at TEXT," +
                    "updated_at TEXT" +
                    ");");
            st.execute("CREATE INDEX IF NOT EXISTS idx_users_sub_end ON users(subscription_end);");
            log.info("SQLite schema ensured.");
        } catch (SQLException e) {
            throw new RuntimeException("DB init failed", e);
        }
    }

    public void upsertUser(long chatId, String username, String firstName, String lastName, boolean isAdmin) {
        String sql = "INSERT INTO users(chat_id, username, first_name, last_name, is_admin, created_at, updated_at) " +
                "VALUES(?,?,?,?,?,?,?) " +
                "ON CONFLICT(chat_id) DO UPDATE SET " +
                "username=excluded.username, " +
                "first_name=excluded.first_name, " +
                "last_name=excluded.last_name, " +
                "is_admin=excluded.is_admin, " +
                "updated_at=excluded.updated_at";
        String now = java.time.OffsetDateTime.now().toString();
        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, chatId);
            ps.setString(2, username);
            ps.setString(3, firstName);
            ps.setString(4, lastName);
            ps.setInt(5, isAdmin ? 1 : 0);
            ps.setString(6, now);
            ps.setString(7, now);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void setSubscription(long chatId, boolean active, LocalDate endDate) {
        String sql = "UPDATE users SET subscription_active=?, subscription_end=?, updated_at=? WHERE chat_id=?";
        String now = java.time.OffsetDateTime.now().toString();
        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, active ? 1 : 0);
            ps.setString(2, endDate != null ? endDate.toString() : null);
            ps.setString(3, now);
            ps.setLong(4, chatId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void cancelSubscription(long chatId) {
        setSubscription(chatId, false, LocalDate.now());
    }

    public List<Long> getAllUserChatIds() {
        List<Long> ids = new ArrayList<>();
        String sql = "SELECT chat_id FROM users";
        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) ids.add(rs.getLong(1));
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return ids;
    }

    public List<Long> getAllSubscriberChatIdsActive() {
        List<Long> ids = new ArrayList<>();
        String sql = "SELECT chat_id FROM users WHERE subscription_active=1";
        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) ids.add(rs.getLong(1));
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return ids;
    }

    public LocalDate getSubscriptionEnd(long chatId) {
        String sql = "SELECT subscription_end FROM users WHERE chat_id=?";
        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, chatId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String v = rs.getString(1);
                    return v == null ? null : LocalDate.parse(v);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    public boolean isSubscriptionActive(long chatId) {
        String sql = "SELECT subscription_active FROM users WHERE chat_id=?";
        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, chatId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) == 1;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return false;
    }

    public List<String> listActiveSubscribersTagAndDate() {
        String sql = "SELECT username, tag, subscription_end FROM users WHERE subscription_active=1 ORDER BY subscription_end";
        List<String> lines = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String username = rs.getString(1);
                if (username == null || username.isBlank()) username = "(без username)";
                String tag = rs.getString(2);
                String end = rs.getString(3);
                String pretty = end == null ? "—" : com.example.bot.DateUtils.formatRu(LocalDate.parse(end));
                lines.add("@" + username + " (" + tag + " | " + pretty + ")");
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return lines;
    }

    public List<Long> findSubsEndingInDays(int days) {
        java.time.LocalDate target = java.time.LocalDate.now().plusDays(days);
        String sql = "SELECT chat_id FROM users WHERE subscription_active=1 AND subscription_end=?";
        List<Long> ids = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, target.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) ids.add(rs.getLong(1));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return ids;
    }

    public List<Long> findSubsEndingToday() {
        java.time.LocalDate target = java.time.LocalDate.now();
        String sql = "SELECT chat_id FROM users WHERE subscription_active=1 AND subscription_end=?";
        List<Long> ids = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, target.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) ids.add(rs.getLong(1));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return ids;
    }
}
