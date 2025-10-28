package com.example.g_trackapp;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.firebase.FirebaseApp;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class CollectorAccountSettingActivity extends AppCompatActivity {

    private static final String TAG = "CollectorAccountSetting";

    private TextView tvUsername, tvFullName, tvEmail, tvPhone, tvAddress, tvVerificationStatus;
    private EditText etEmail, etPassword, etPhone, etRegion, etCity, etBarangay, etStreet;
    private Button btnSaveSettings, btnEdit;
    private ImageView btnMenu;

    private boolean isVerified = false;
    private FirebaseFirestore db;
    private String currentCollectorId;
    private boolean isEditing = false;
    private CollectorSessionManager sessionManager;

    // 🔹 Hold current Firestore data
    private String currentEmail, currentPhone, currentRegion, currentCity, currentBarangay, currentStreet;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_collector_account_setting);

        FirebaseApp.initializeApp(this);
        db = FirebaseFirestore.getInstance();
        sessionManager = new CollectorSessionManager(this);

        // Bind views
        tvUsername = findViewById(R.id.tvUsername);
        tvFullName = findViewById(R.id.tvFullName);
        tvEmail = findViewById(R.id.tvEmail);
        tvPhone = findViewById(R.id.tvPhone);
        tvAddress = findViewById(R.id.tvAddress);
        tvVerificationStatus = findViewById(R.id.tvVerificationStatus);
        btnMenu = findViewById(R.id.btnMenu);

        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        etPhone = findViewById(R.id.etPhone);
        etRegion = findViewById(R.id.etRegion);
        etCity = findViewById(R.id.etCity);
        etBarangay = findViewById(R.id.etBarangay);
        etStreet = findViewById(R.id.etStreet);

        btnSaveSettings = findViewById(R.id.btnSaveSettings);
        btnEdit = findViewById(R.id.btnEdit);

        toggleEditMode(false);
        updateVerificationStatus();

        // Load logged-in collector info from session
        currentCollectorId = sessionManager.getCollectorId();
        if (currentCollectorId == null) {
            Toast.makeText(this, "No logged in collector found!", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Use session for first/last name
        String sessionFirstName = sessionManager.getFirstName();
        String sessionLastName = sessionManager.getLastName();
        String fullName = (sessionFirstName != null ? sessionFirstName : "") + " " +
                (sessionLastName != null ? sessionLastName : "");
        tvFullName.setText(fullName.trim());

        loadCollectorInfo(); // Fetch remaining data from Firestore

        btnEdit.setOnClickListener(v -> {
            isEditing = !isEditing;
            if (isEditing) populateEditFields(); // populate with current data
            toggleEditMode(isEditing);
        });

        btnSaveSettings.setOnClickListener(v -> saveCollectorChanges());

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnHome).setOnClickListener(v -> {
            Intent intent = new Intent(this, CollectorMainMenuActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        });

        if (btnMenu != null) {
            btnMenu.setOnClickListener(v -> showPopupMenu(btnMenu));
        }
    }

    // 🔹 Populate EditTexts with current Firestore data
    private void populateEditFields() {
        etEmail.setText(currentEmail != null ? currentEmail : "");
        etPhone.setText(currentPhone != null ? currentPhone : "");
        etRegion.setText(currentRegion != null ? currentRegion : "");
        etCity.setText(currentCity != null ? currentCity : "");
        etBarangay.setText(currentBarangay != null ? currentBarangay : "");
        etStreet.setText(currentStreet != null ? currentStreet : "");
        etPassword.setText(""); // always blank for security
    }

    private void saveCollectorChanges() {
        if (currentCollectorId == null) return;

        Map<String, Object> updateData = new HashMap<>();

        // Only update fields that are non-empty (user wants to change)
        String email = etEmail.getText().toString().trim();
        if (!email.isEmpty()) updateData.put("email", email);

        String password = etPassword.getText().toString().trim();
        if (!password.isEmpty()) updateData.put("password", password);

        String phone = etPhone.getText().toString().trim();
        if (!phone.isEmpty()) updateData.put("phone", phone);

        String region = etRegion.getText().toString().trim();
        if (!region.isEmpty()) updateData.put("region", region);

        String city = etCity.getText().toString().trim();
        if (!city.isEmpty()) updateData.put("city", city);

        String barangay = etBarangay.getText().toString().trim();
        if (!barangay.isEmpty()) updateData.put("barangay", barangay);

        String street = etStreet.getText().toString().trim();
        if (!street.isEmpty()) updateData.put("streetHouseNumber", street);

        if (updateData.isEmpty()) {
            Toast.makeText(this, "No changes to save!", Toast.LENGTH_SHORT).show();
            return;
        }

        // 🔹 Update Firestore document
        db.collection("collectors").document(currentCollectorId)
                .update(updateData)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Changes saved!", Toast.LENGTH_SHORT).show();

                    // 🔹 Reload the updated info
                    loadCollectorInfo();

                    // 🔹 Switch back to view mode
                    isEditing = false;
                    toggleEditMode(false);

                    // 🔹 Clear password field for security
                    etPassword.setText("");
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Failed to update collector: ", e);
                });
    }


    private void loadCollectorInfo() {
        if (currentCollectorId == null) return;

        db.collection("collectors").document(currentCollectorId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (!documentSnapshot.exists()) return;

                    currentEmail = documentSnapshot.getString("email");
                    currentPhone = documentSnapshot.getString("phone");
                    currentRegion = documentSnapshot.getString("region");
                    currentCity = documentSnapshot.getString("city");
                    currentBarangay = documentSnapshot.getString("barangay");
                    currentStreet = documentSnapshot.getString("streetHouseNumber");

                    String verificationStatus = documentSnapshot.getString("verificationStatus");

                    tvEmail.setText("Email: " + (currentEmail != null ? currentEmail : ""));
                    tvPhone.setText("Phone Number: " + (currentPhone != null ? currentPhone : ""));

                    String fullAddress = "";
                    if (currentStreet != null && !currentStreet.isEmpty()) fullAddress += currentStreet + ", ";
                    if (currentBarangay != null && !currentBarangay.isEmpty()) fullAddress += "Brgy. " + currentBarangay + ", ";
                    if (currentCity != null && !currentCity.isEmpty()) fullAddress += currentCity + ", ";
                    if (currentRegion != null && !currentRegion.isEmpty()) fullAddress += currentRegion;
                    tvAddress.setText("Address: " + fullAddress.trim());

                    if (verificationStatus != null) {
                        tvVerificationStatus.setText("Status: " + verificationStatus);
                        if (verificationStatus.equalsIgnoreCase("Verified")) {
                            tvVerificationStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark));
                            isVerified = true;
                        } else {
                            tvVerificationStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark));
                            isVerified = false;
                        }
                    } else {
                        isVerified = false;
                        updateVerificationStatus();
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to load info: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void toggleEditMode(boolean editing) {
        int editVisibility = editing ? EditText.VISIBLE : EditText.GONE;
        int saveVisibility = editing ? Button.VISIBLE : Button.GONE;

        etEmail.setVisibility(editVisibility);
        etPassword.setVisibility(editVisibility);
        etPhone.setVisibility(editVisibility);
        etRegion.setVisibility(editVisibility);
        etCity.setVisibility(editVisibility);
        etBarangay.setVisibility(editVisibility);
        etStreet.setVisibility(editVisibility);
        btnSaveSettings.setVisibility(saveVisibility);

        int textVisibility = editing ? TextView.GONE : TextView.VISIBLE;
        tvFullName.setVisibility(textVisibility);
        tvEmail.setVisibility(textVisibility);
        tvPhone.setVisibility(textVisibility);
        tvAddress.setVisibility(textVisibility);

        btnEdit.setText(editing ? "Cancel" : "Edit");
    }

    private void updateVerificationStatus() {
        if (isVerified) {
            tvVerificationStatus.setText("Status: Verified ✅");
            tvVerificationStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark));
        } else {
            tvVerificationStatus.setText("Status: Not Verified ❌");
            tvVerificationStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark));
        }
    }

    private void showPopupMenu(ImageView anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenuInflater().inflate(R.menu.menu_top_right, popup.getMenu());
        popup.setOnMenuItemClickListener(this::handleMenuClick);
        popup.show();
    }

    private boolean handleMenuClick(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            Toast.makeText(this, "You are already in Account Settings.", Toast.LENGTH_SHORT).show();
            return true;
        } else if (id == R.id.action_contact) {
            Toast.makeText(this, "Contact Us clicked", Toast.LENGTH_SHORT).show();
            return true;
        } else if (id == R.id.action_about) {
            Toast.makeText(this, "About clicked", Toast.LENGTH_SHORT).show();
            return true;
        } else if (id == R.id.action_logout) {
            sessionManager.logoutCollector();
            Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(this, CollectorLoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return true;
        }
        return false;
    }
}
