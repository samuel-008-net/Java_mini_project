package com.campus.dao;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class EventDAO {

    // 1. Load clubs from database
    public static void loadClubs(Map<String, String> registeredClubs, Map<String, String> clubCredentials, Map<String, String> clubAvatarPaths) {
        String sql = "SELECT * FROM clubs";
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String email = rs.getString("email");
                registeredClubs.put(email, rs.getString("name"));
                clubCredentials.put(email, rs.getString("password"));
                clubAvatarPaths.put(email, rs.getString("avatar_path"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // 2. Load students from database
    public static void loadStudents(Map<String, Object> registeredStudents, Map<String, String> studentCredentials, Map<String, String> studentAvatarPaths, StudentSessionCreator creator) {
        String sql = "SELECT * FROM students";
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String email = rs.getString("email");
                registeredStudents.put(email, creator.create(
                        rs.getString("roll_number"),
                        rs.getString("name"),
                        email,
                        rs.getString("department")
                ));
                studentCredentials.put(email, rs.getString("password"));
                studentAvatarPaths.put(email, rs.getString("avatar_path"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // 3. Insert new event into database
    public static int insertEvent(String name, String clubName, String type, int credits, String status,
                                   String studentCoords, String facultyCoords, int totalCapacity,
                                   String colorTheme, String posterLogo, String timeLeft, String regAmount,
                                   String whatsappLink, String paymentQrPath, boolean certProvided, String certTimeline) throws SQLException {
        String sql = """
            INSERT INTO events (name, club_name, type, credits, status, student_coords, faculty_coords, 
                                booked_seats, total_capacity, color_theme, poster_logo, time_left, 
                                reg_amount, whatsapp_link, payment_qr_path, cert_provided, cert_timeline)
            VALUES (?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, clubName);
            ps.setString(3, type);
            ps.setInt(4, credits);
            ps.setString(5, status);
            ps.setString(6, studentCoords);
            ps.setString(7, facultyCoords);
            ps.setInt(8, totalCapacity);
            ps.setString(9, colorTheme);
            ps.setString(10, posterLogo);
            ps.setString(11, timeLeft);
            ps.setString(12, regAmount);
            ps.setString(13, whatsappLink);
            ps.setString(14, paymentQrPath);
            ps.setInt(15, certProvided ? 1 : 0);
            ps.setString(16, certTimeline);
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return -1;
    }

    // 4. Register student with ACID transaction
    public static boolean registerStudentForEvent(int eventId, String roll, String name, String dept, String utr, String email, String timestamp) throws SQLException {
        String insertRegSql = """
            INSERT INTO registrations (event_id, student_roll, student_name, department, utr, student_email, timestamp)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """;
        String updateSeatSql = "UPDATE events SET booked_seats = booked_seats + 1 WHERE id = ?";

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement psReg = conn.prepareStatement(insertRegSql);
                 PreparedStatement psSeat = conn.prepareStatement(updateSeatSql)) {

                psReg.setInt(1, eventId);
                psReg.setString(2, roll);
                psReg.setString(3, name);
                psReg.setString(4, dept);
                psReg.setString(5, utr);
                psReg.setString(6, email);
                psReg.setString(7, timestamp);
                psReg.executeUpdate();

                psSeat.setInt(1, eventId);
                psSeat.executeUpdate();

                conn.commit();
                return true;
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            }
        }
    }

    // 5. Update user avatar
    public static void updateAvatar(String table, String email, String avatarPath) {
        String sql = "UPDATE " + table + " SET avatar_path = ? WHERE email = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, avatarPath);
            ps.setString(2, email);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // 6. Update user password
    public static void updatePassword(String table, String email, String newPassword) throws SQLException {
        String sql = "UPDATE " + table + " SET password = ? WHERE email = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newPassword);
            ps.setString(2, email);
            ps.executeUpdate();
        }
    }

    @FunctionalInterface
    public interface StudentSessionCreator {
        Object create(String roll, String name, String email, String department);
    }
}