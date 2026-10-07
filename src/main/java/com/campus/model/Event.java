package com.campus.model;

public class Event {
    private int eventId;
    private String title;
    private String category;
    private int capacity;
    private int registeredCount;

    public Event(int eventId, String title, String category, int capacity, int registeredCount) {
        this.eventId = eventId;
        this.title = title;
        this.category = category;
        this.capacity = capacity;
        this.registeredCount = registeredCount;
    }

    public int getEventId() { return eventId; }
    public String getTitle() { return title; }
    public String getCategory() { return category; }
    public int getCapacity() { return capacity; }
    public int getRegisteredCount() { return registeredCount; }
    public void setRegisteredCount(int registeredCount) { this.registeredCount = registeredCount; }

    public boolean isFull() {
        return registeredCount >= capacity;
    }

    @Override
    public String toString() {
        return title + " (" + category + ") - [" + registeredCount + "/" + capacity + " Seats]";
    }
}