package com.example.g_trackapp;

import android.content.Context;
import android.content.SharedPreferences;

public class CollectorSessionManager {
    private static final String PREF_NAME = "CollectorSessionPref";

    private static final String KEY_COLLECTOR_ID = "collector_id";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_FIRSTNAME = "firstName";
    private static final String KEY_LASTNAME = "lastName";

    private final SharedPreferences pref;
    private final SharedPreferences.Editor editor;

    public CollectorSessionManager(Context context) {
        pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        editor = pref.edit();
    }

    // 🔹 Save collector details
    public void saveCollectorDetails(String id, String username, String firstName, String lastName) {
        editor.putString(KEY_COLLECTOR_ID, id);
        editor.putString(KEY_USERNAME, username);
        editor.putString(KEY_FIRSTNAME, firstName);
        editor.putString(KEY_LASTNAME, lastName);
        editor.apply();
    }

    // 🔹 Getters
    public String getCollectorId() { return pref.getString(KEY_COLLECTOR_ID, null); }
    public String getUsername() { return pref.getString(KEY_USERNAME, null); }
    public String getFirstName() { return pref.getString(KEY_FIRSTNAME, null); }
    public String getLastName() { return pref.getString(KEY_LASTNAME, null); }

    // 🔹 Check login
    public boolean isLoggedIn() {
        return getCollectorId() != null && getUsername() != null;
    }

    // 🔹 Logout
    public void logoutCollector() {
        clearSession();
    }

    // 🔹 Add this method to fix your error
    public void clearSession() {
        editor.clear();
        editor.apply();
    }
}