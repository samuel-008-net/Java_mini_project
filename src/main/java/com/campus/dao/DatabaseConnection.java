package com.campus.dao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseConnection {

    private static final String DB_URL = "jdbc:sqlite:campus_events.db";

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL);
    }

    public static void initialize() {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {

            // 1. Clubs Table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS clubs (
                    email TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    password TEXT NOT NULL,
                    avatar_path TEXT
                );
            """);

            // 2. Students Table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS students (
                    email TEXT PRIMARY KEY,
                    roll_number TEXT UNIQUE NOT NULL,
                    name TEXT NOT NULL,
                    department TEXT NOT NULL,
                    password TEXT NOT NULL,
                    avatar_path TEXT
                );
            """);

            // 3. Events Table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS events (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    club_name TEXT NOT NULL,
                    type TEXT NOT NULL,
                    credits INTEGER DEFAULT 0,
                    status TEXT NOT NULL,
                    student_coords TEXT,
                    faculty_coords TEXT,
                    booked_seats INTEGER DEFAULT 0,
                    total_capacity INTEGER NOT NULL,
                    color_theme TEXT,
                    poster_logo TEXT,
                    time_left TEXT,
                    reg_amount TEXT,
                    whatsapp_link TEXT,
                    payment_qr_path TEXT,
                    cert_provided INTEGER DEFAULT 1,
                    cert_timeline TEXT
                );
            """);

            // 4. Registrations Table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS registrations (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    event_id INTEGER NOT NULL,
                    student_roll TEXT NOT NULL,
                    student_name TEXT NOT NULL,
                    department TEXT NOT NULL,
                    utr TEXT,
                    student_email TEXT NOT NULL,
                    timestamp TEXT NOT NULL,
                    UNIQUE(event_id, student_roll),
                    FOREIGN KEY(event_id) REFERENCES events(id) ON DELETE CASCADE
                );
            """);

            // Seed default test accounts
            stmt.execute("""
                INSERT OR IGNORE INTO clubs (email, name, password) 
                VALUES ('ieee@gmail.com', 'IEEE Student Branch', 'password123'),
                       ('coding@gmail.com', 'Coding Ninjas Club', 'password123');
            """);

            stmt.execute("""
                INSERT OR IGNORE INTO students (email, roll_number, name, department, password)
                VALUES ('alex@gmail.com', '22CS101', 'Alex Rivers', 'Computer Science', 'password123');
            """);

        } catch (SQLException e) {
            System.err.println("Database initialization failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}