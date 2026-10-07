package com.campus.model;

import java.sql.Timestamp;

public class Registration {
    private int registrationId;
    private String studentId;
    private String studentName;
    private String department;
    private int eventId;
    private Timestamp registeredAt;

    public Registration(int registrationId, String studentId, String studentName, String department, int eventId, Timestamp registeredAt) {
        this.registrationId = registrationId;
        this.studentId = studentId;
        this.studentName = studentName;
        this.department = department;
        this.eventId = eventId;
        this.registeredAt = registeredAt;
    }

    public int getRegistrationId() { return registrationId; }
    public String getStudentId() { return studentId; }
    public String getStudentName() { return studentName; }
    public String getDepartment() { return department; }
    public int getEventId() { return eventId; }
    public Timestamp getRegisteredAt() { return registeredAt; }
}