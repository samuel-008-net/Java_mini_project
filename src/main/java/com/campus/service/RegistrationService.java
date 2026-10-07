package com.campus.service;

import com.campus.dao.EventDAO;
import com.campus.model.Event;

import java.sql.SQLException;
import java.util.*;

public class RegistrationService {
    private final EventDAO eventDAO = new EventDAO();
    
    // Collections Layer: In-memory HashSet for O(1) duplicate lookup
    private final Set<String> registrationCache = new HashSet<>();

    public RegistrationService() {
        refreshCache();
    }

    public void refreshCache() {
        try {
            registrationCache.clear();
            registrationCache.addAll(eventDAO.getExistingRegistrationKeys());
        } catch (SQLException e) {
            System.err.println("Database offline or unreachable: " + e.getMessage());
        }
    }

    public List<Event> getAllEvents() throws SQLException {
        return eventDAO.getAllEvents();
    }

    /**
     * Algorithmic duplicate prevention & seat reservation logic
     */
    public String registerStudent(String studentId, String name, String dept, Event event) {
        if (studentId == null || studentId.trim().isEmpty()) {
            return "Error: Student ID cannot be empty.";
        }
        if (name == null || name.trim().isEmpty()) {
            return "Error: Name cannot be empty.";
        }
        if (event == null) {
            return "Error: No event selected.";
        }

        String cleanId = studentId.trim().toUpperCase();
        String duplicateKey = cleanId + "_" + event.getEventId();

        // 1. Algorithmic Duplicate Check using HashSet
        if (registrationCache.contains(duplicateKey)) {
            return "DUPLICATE REJECTED: Student [" + cleanId + "] is already registered for this event!";
        }

        // 2. Capacity Boundary Check
        if (event.isFull()) {
            return "REJECTED: Event capacity reached (" + event.getCapacity() + " seats full).";
        }

        // 3. Database Persistence
        try {
            eventDAO.registerStudent(cleanId, name.trim(), dept, event.getEventId());
            registrationCache.add(duplicateKey);
            event.setRegisteredCount(event.getRegisteredCount() + 1);
            return "SUCCESS";
        } catch (SQLException e) {
            if (e.getErrorCode() == 1062) { // MySQL Duplicate Key error code
                return "DUPLICATE REJECTED: Record already exists in the database.";
            }
            return "Database Error: " + e.getMessage();
        }
    }

    /**
     * AI-Inspired Heuristic Recommendation Engine:
     * Calculates a relevance score for events based on department relevance and popularity
     */
    public List<Event> getRecommendations(String department, List<Event> events) {
        if (events == null || events.isEmpty()) {
            return Collections.emptyList();
        }

        List<Event> sortedList = new ArrayList<>(events);
        sortedList.sort((e1, e2) -> {
            int score1 = calculateRelevanceScore(e1, department);
            int score2 = calculateRelevanceScore(e2, department);
            return Integer.compare(score2, score1); // Descending score
        });

        return sortedList;
    }

    private int calculateRelevanceScore(Event event, String department) {
        int score = 0;
        String title = event.getTitle().toLowerCase();
        String dept = department.toLowerCase();

        // Topic similarity match
        if (dept.contains("computer") && (title.contains("ai") || title.contains("cloud") || title.contains("hackathon") || title.contains("coding"))) {
            score += 50;
        } else if (dept.contains("electrical") && (title.contains("circuit") || title.contains("iot") || title.contains("robotics"))) {
            score += 50;
        } else if (dept.contains("management") && (title.contains("leadership") || title.contains("business") || title.contains("finance"))) {
            score += 50;
        }

        // Seat popularity weighting
        score += event.getRegisteredCount() * 5;
        return score;
    }
}