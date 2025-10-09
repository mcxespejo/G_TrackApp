package com.example.g_trackapp;

import android.content.Context;
import android.content.SharedPreferences;

public class ResidentSessionManager {

    private static final String PREF_NAME = "ResidentSessionPref";

    private static final String KEY_RESIDENT_ID = "resident_id";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_FIRSTNAME = "firstname";
    private static final String KEY_LASTNAME = "lastname";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_CONTACT = "contact";
    private static final String KEY_REGION = "region";
    private static final String KEY_CITY = "city";
    private static final String KEY_BARANGAY = "barangay";

    private final SharedPreferences pref;
    private final SharedPreferences.Editor editor;

    public ResidentSessionManager(Context context) {
        pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        editor = pref.edit();
    }

    // 🔹 Save all resident details at login
    public void saveResidentDetails(String id, String username, String firstName, String lastName, String email, String contact) {
        editor.putString(KEY_RESIDENT_ID, id);
        editor.putString(KEY_USERNAME, username);
        editor.putString(KEY_FIRSTNAME, firstName);
        editor.putString(KEY_LASTNAME, lastName);
        editor.putString(KEY_EMAIL, email);
        editor.putString(KEY_CONTACT, contact);
        editor.apply();
    }

    // 🔹 Save extra info after login
    public void saveExtraResidentInfo(String region, String city, String barangay) {
        editor.putString(KEY_REGION, region);
        editor.putString(KEY_CITY, city);
        editor.putString(KEY_BARANGAY, barangay);
        editor.apply();
    }

    // 🔹 Save or update just the username
    public void saveUsername(String username) {
        if (username != null) {
            editor.putString(KEY_USERNAME, username);
            editor.apply();
        }
    }

    // 🔹 Getters
    public String getResidentId() { return pref.getString(KEY_RESIDENT_ID, null); }
    public String getUsername() { return pref.getString(KEY_USERNAME, null); }
    public String getFirstName() { return pref.getString(KEY_FIRSTNAME, null); }
    public String getLastName() { return pref.getString(KEY_LASTNAME, null); }
    public String getEmail() { return pref.getString(KEY_EMAIL, null); }
    public String getContact() { return pref.getString(KEY_CONTACT, null); }
    public String getRegion() { return pref.getString(KEY_REGION, null); }
    public String getCity() { return pref.getString(KEY_CITY, null); }
    public String getBarangay() { return pref.getString(KEY_BARANGAY, null); }

    // 🔹 Check login
    public boolean isLoggedIn() {
        return getResidentId() != null && getUsername() != null;
    }

    // 🔹 Logout
    public void logoutResident() {
        clearSession();
    }

    // 🔹 Clear session
    public void clearSession() {
        editor.clear();
        editor.apply();
    }
}
