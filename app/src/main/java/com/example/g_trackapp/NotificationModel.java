package com.example.g_trackapp;

import com.google.firebase.Timestamp;

public class NotificationModel {

    private String message;
    private String username;
    private boolean isRead;
    private Timestamp timestamp;

    // Empty constructor required for Firestore
    public NotificationModel() {}

    // Constructor for creating notification objects
    public NotificationModel(String message, String username, boolean isRead, Timestamp timestamp) {
        this.message = message;
        this.username = username;
        this.isRead = isRead;
        this.timestamp = timestamp;
    }

    // Getters
    public String getMessage() { return message; }
    public String getUsername() { return username; }
    public boolean isRead() { return isRead; }
    public Timestamp getTimestamp() { return timestamp; }

    // Setters
    public void setMessage(String message) { this.message = message; }
    public void setUsername(String username) { this.username = username; }
    public void setRead(boolean read) { isRead = read; }
    public void setTimestamp(Timestamp timestamp) { this.timestamp = timestamp; }
}
