package com.example.g_trackapp;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.MenuItem;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.google.firebase.FirebaseApp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.HashMap;
import java.util.Map;

public class ResidentAccountSettingActivity extends AppCompatActivity {

    TextView tvUsername, tvFullName, tvEmail, tvPhone, tvAddress,
            tvUploadedFile, tvVerificationStatus;

    EditText etFirstName, etLastName, etEmail, etPassword, etPhone, etRegion, etCity, etBarangay, etStreet;

    Button btnSaveSettings, btnUploadId, btnEdit;
    ImageView btnMenu, imgUploadedId;
    ProgressBar progressBar;

    Uri uploadedFileUri;
    private boolean isVerified = false;
    private String currentIdUrl;

    private FirebaseFirestore db;
    private FirebaseStorage storage;

    private String currentResidentId;
    private boolean isEditing = false;
    private ResidentSessionManager sessionManager;


    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_resident_account_setting);

        sessionManager = new ResidentSessionManager(this);

        FirebaseApp.initializeApp(this);
        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();

        // UI refs
        tvUsername = findViewById(R.id.tvUsername);
        tvFullName = findViewById(R.id.tvFullName);
        tvEmail = findViewById(R.id.tvEmail);
        tvPhone = findViewById(R.id.tvPhone);
        tvAddress = findViewById(R.id.tvAddress);
        tvUploadedFile = findViewById(R.id.tvUploadedFile);
        tvVerificationStatus = findViewById(R.id.tvVerificationStatus);
        progressBar = findViewById(R.id.progressBar);
        btnMenu = findViewById(R.id.btnMenu);
        imgUploadedId = findViewById(R.id.imgUploadedId);

        etFirstName = findViewById(R.id.etFirstName);
        etLastName = findViewById(R.id.etLastName);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        etPhone = findViewById(R.id.etPhone);
        etRegion = findViewById(R.id.etRegion);
        etCity = findViewById(R.id.etCity);
        etBarangay = findViewById(R.id.etBarangay);
        etStreet = findViewById(R.id.etStreet);

        btnSaveSettings = findViewById(R.id.btnSaveSettings);
        btnUploadId = findViewById(R.id.btnUploadId);
        btnEdit = findViewById(R.id.btnEdit);

        toggleEditMode(false);
        updateVerificationStatus();

        currentResidentId = sessionManager.getResidentId();

        if (currentResidentId != null) {
            loadResidentInfo();
        } else {
            Toast.makeText(this, "No logged in resident found!", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        btnEdit.setOnClickListener(v -> {
            isEditing = !isEditing;
            toggleEditMode(isEditing);
        });

        btnSaveSettings.setOnClickListener(v -> saveResidentChanges());

        // Upload ID
        btnUploadId.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("image/*");
            intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            filePickerLauncher.launch(Intent.createChooser(intent, "Select ID Image"));
        });

        // Fullscreen preview
        imgUploadedId.setOnClickListener(v -> {
            if (currentIdUrl != null) {
                Intent intent = new Intent(this, FullScreenImageActivity.class);
                intent.putExtra("imageUrl", currentIdUrl);
                startActivity(intent);
            }
        });

        // Bottom Nav
        ImageView btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        ImageView btnHome = findViewById(R.id.btnHome);
        if (btnHome != null) btnHome.setOnClickListener(v -> {
            Intent intent = new Intent(this, ResidentMainMenuActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        });

        ImageView btnAlarm = findViewById(R.id.btnAlarm);
        if (btnAlarm != null) btnAlarm.setOnClickListener(v ->
                Toast.makeText(this, "Alarm clicked", Toast.LENGTH_SHORT).show()
        );

        if (btnMenu != null) {
            btnMenu.setOnClickListener(v -> showPopupMenu(btnMenu));
        }
    }

    // Save resident profile
    private void saveResidentChanges() {
        String firstName = etFirstName.getText().toString().trim();
        String lastName = etLastName.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String phone = etPhone.getText().toString().trim();
        String region = etRegion.getText().toString().trim();
        String city = etCity.getText().toString().trim();
        String barangay = etBarangay.getText().toString().trim();
        String street = etStreet.getText().toString().trim();

        if (firstName.isEmpty() || lastName.isEmpty() || email.isEmpty() || phone.isEmpty()
                || region.isEmpty() || city.isEmpty() || barangay.isEmpty() || street.isEmpty()) {
            Toast.makeText(this, "All fields are required!", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, Object> updateData = new HashMap<>();
        updateData.put("firstName", firstName);
        updateData.put("lastName", lastName);
        updateData.put("email", email);
        updateData.put("phone", phone);
        updateData.put("region", region);
        updateData.put("city", city);
        updateData.put("barangay", barangay);
        updateData.put("streetHouseNumber", street);
        if (!password.isEmpty()) updateData.put("password", password);

        db.collection("residents").document(currentResidentId)
                .update(updateData)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Changes saved!", Toast.LENGTH_SHORT).show();

                    // ✅ Update session for offline consistency
                    sessionManager.saveResidentDetails(
                            currentResidentId,
                            sessionManager.getUsername(), // keep old username
                            firstName,
                            lastName,
                            email,
                            phone
                    );
                    sessionManager.saveExtraResidentInfo(region, city, barangay);

                    loadResidentInfo();
                    toggleEditMode(false);
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    // Load info + ID preview (optimized for session usage)
    private void loadResidentInfo() {
        // ✅ Get cached session values first
        String firstName = sessionManager.getFirstName();
        String lastName = sessionManager.getLastName();
        String email = sessionManager.getEmail();
        String username = sessionManager.getUsername();

        String fullName = (firstName != null ? firstName : "") + " " +
                (lastName != null ? lastName : "");

        tvFullName.setText("Full Name: " + fullName.trim());
        tvEmail.setText("Email: " + (email != null ? email : ""));
        tvUsername.setText("Username: " + (username != null ? username : ""));

        etFirstName.setText(firstName != null ? firstName : "");
        etLastName.setText(lastName != null ? lastName : "");
        etEmail.setText(email != null ? email : "");

        // ✅ Fetch only remaining fields from Firestore
        db.collection("residents").document(currentResidentId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String phone = documentSnapshot.getString("phone");
                        String region = documentSnapshot.getString("region");
                        String city = documentSnapshot.getString("city");
                        String barangay = documentSnapshot.getString("barangay");
                        String street = documentSnapshot.getString("streetHouseNumber");

                        String fullAddress = "";
                        if (street != null && !street.isEmpty()) fullAddress += street + ", ";
                        if (barangay != null && !barangay.isEmpty()) fullAddress += "Brgy. " + barangay + ", ";
                        if (city != null && !city.isEmpty()) fullAddress += city + ", ";
                        if (region != null && !region.isEmpty()) fullAddress += region;

                        tvPhone.setText("Phone: " + (phone != null ? phone : ""));
                        tvAddress.setText("Address: " + fullAddress.trim());

                        etPhone.setText(phone != null ? phone : "");
                        etRegion.setText(region != null ? region : "");
                        etCity.setText(city != null ? city : "");
                        etBarangay.setText(barangay != null ? barangay : "");
                        etStreet.setText(street != null ? street : "");

                        // 🔹 Verification
                        String verificationStatus = documentSnapshot.getString("verificationStatus");
                        if (verificationStatus != null) {
                            tvVerificationStatus.setText("Status: " + verificationStatus);
                            if (verificationStatus.equalsIgnoreCase("Verified")) {
                                tvVerificationStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark));
                                isVerified = true;
                            } else {
                                tvVerificationStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark));
                                isVerified = false;
                            }
                        }

                        // 🔹 ID preview
                        String idUrl = documentSnapshot.getString("idUrl");
                        if (idUrl != null) {
                            currentIdUrl = idUrl;
                            tvUploadedFile.setText("Uploaded: ID Image");
                            imgUploadedId.setVisibility(ImageView.VISIBLE);
                            Glide.with(this).load(idUrl).into(imgUploadedId);
                        } else {
                            imgUploadedId.setVisibility(ImageView.GONE);
                            currentIdUrl = null;
                        }
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to load: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    // Toggle edit mode
    private void toggleEditMode(boolean editing) {
        int editVis = editing ? EditText.VISIBLE : EditText.GONE;
        int saveVis = editing ? Button.VISIBLE : Button.GONE;

        etFirstName.setVisibility(editVis);
        etLastName.setVisibility(editVis);
        etEmail.setVisibility(editVis);
        etPassword.setVisibility(editVis);
        etPhone.setVisibility(editVis);
        etRegion.setVisibility(editVis);
        etCity.setVisibility(editVis);
        etBarangay.setVisibility(editVis);
        etStreet.setVisibility(editVis);
        btnSaveSettings.setVisibility(saveVis);

        int textVis = editing ? TextView.GONE : TextView.VISIBLE;
        tvFullName.setVisibility(textVis);
        tvEmail.setVisibility(textVis);
        tvPhone.setVisibility(textVis);
        tvAddress.setVisibility(textVis);

        btnEdit.setText(editing ? "Cancel" : "Edit");
    }

    // File picker
    ActivityResultLauncher<Intent> filePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri fileUri = result.getData().getData();
                    if (fileUri != null) {
                        try {
                            getContentResolver().takePersistableUriPermission(fileUri,
                                    result.getData().getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION));
                        } catch (SecurityException e) {
                            e.printStackTrace();
                        }

                        uploadedFileUri = fileUri;
                        String fileName = getFileName(fileUri);

                        new android.app.AlertDialog.Builder(this)
                                .setTitle("Confirm Upload")
                                .setMessage("Do you want to upload this ID?\n\nFile: " + fileName)
                                .setPositiveButton("Upload", (dialog, which) -> {
                                    tvUploadedFile.setText("Uploading: " + fileName);
                                    uploadFileToFirebase(fileUri);
                                })
                                .setNegativeButton("Cancel", (dialog, which) -> {
                                    Toast.makeText(this, "Upload cancelled", Toast.LENGTH_SHORT).show();
                                    uploadedFileUri = null;
                                })
                                .setCancelable(false)
                                .show();
                    }
                }
            });

    // Upload to Firebase Storage
    private void uploadFileToFirebase(Uri fileUri) {
        if (currentResidentId == null) {
            Toast.makeText(this, "No resident logged in!", Toast.LENGTH_SHORT).show();
            return;
        }

        progressBar.setVisibility(ProgressBar.VISIBLE);
        progressBar.setProgress(0);

        StorageReference fileRef = storage.getReference()
                .child("resident_ids/" + currentResidentId); // ✅ overwrite always

        fileRef.putFile(fileUri)
                .addOnProgressListener(snapshot -> {
                    double progress = (100.0 * snapshot.getBytesTransferred()) / snapshot.getTotalByteCount();
                    progressBar.setProgress((int) progress);
                })
                .addOnSuccessListener(taskSnapshot ->
                        fileRef.getDownloadUrl().addOnSuccessListener(uri -> {
                            progressBar.setVisibility(ProgressBar.GONE);
                            String downloadUrl = uri.toString();

                            currentIdUrl = downloadUrl;
                            tvUploadedFile.setText("Uploaded: ID Image");
                            tvVerificationStatus.setText("Status: Pending Verification");
                            tvVerificationStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark));

                            imgUploadedId.setVisibility(ImageView.VISIBLE);
                            Glide.with(this).load(downloadUrl).into(imgUploadedId);

                            Map<String, Object> updateData = new HashMap<>();
                            updateData.put("idUrl", downloadUrl);
                            updateData.put("verificationStatus", "Pending");

                            db.collection("residents").document(currentResidentId)
                                    .update(updateData)
                                    .addOnSuccessListener(aVoid ->
                                            Toast.makeText(this, "File uploaded & saved!", Toast.LENGTH_SHORT).show())
                                    .addOnFailureListener(e ->
                                            Toast.makeText(this, "Error saving: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                        }))
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(ProgressBar.GONE);
                    Toast.makeText(this, "Upload failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private String getFileName(Uri uri) {
        String result = null;
        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (idx >= 0) result = cursor.getString(idx);
                }
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result != null ? result.lastIndexOf('/') : -1;
            if (cut != -1) result = result.substring(cut + 1);
        }
        return result;
    }

    private void updateVerificationStatus() {
        if (isVerified) {
            tvVerificationStatus.setText("Status: Verified ✅");
            tvVerificationStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
        } else {
            tvVerificationStatus.setText("Status: Not Verified ❌");
            tvVerificationStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
        }
    }

    // Top menu
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
            sessionManager.logoutResident();
            Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(this, ResidentLoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return true;
        }
        return false;
    }
}
