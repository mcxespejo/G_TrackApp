package com.example.g_trackapp;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.content.Intent;
import android.util.Log;
import android.widget.*;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.functions.HttpsCallableResult;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class ResidentLoginActivity extends AppCompatActivity {

    private static final String TAG = "ResidentLoginActivity";
    private static final long RESET_CODE_EXPIRATION_MS = 5 * 60 * 1000; // 5 minutes
    private FirebaseFirestore db;
    private FirebaseFunctions functions;
    private ResidentSessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_resident_login);

        db = FirebaseFirestore.getInstance();
        functions = FirebaseFunctions.getInstance();
        sessionManager = new ResidentSessionManager(this);

        Button loginButton = findViewById(R.id.btn_login);
        TextView signUpPrompt = findViewById(R.id.tv_signup_prompt);
        TextView forgotPasswordLink = findViewById(R.id.tv_forgot_password);
        EditText usernameInput = findViewById(R.id.et_username);
        EditText passwordInput = findViewById(R.id.et_password);
        ImageView btnBack = findViewById(R.id.btnBack);

        // 🔹 Auto-login if already logged in
        if (sessionManager.isLoggedIn()) {
            Log.d(TAG, "Auto-login for resident: " + sessionManager.getUsername());
            goToResidentLandingPage();
            return;
        }

        // 🔹 Login Button
        loginButton.setOnClickListener(v -> {
            String username = usernameInput.getText().toString().trim();
            String password = passwordInput.getText().toString().trim();

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter both username and password", Toast.LENGTH_SHORT).show();
                return;
            }

            db.collection("residents")
                    .whereEqualTo("username", username)
                    .limit(1)
                    .get()
                    .addOnSuccessListener(querySnapshot -> {
                        if (!querySnapshot.isEmpty()) {
                            DocumentSnapshot document = querySnapshot.getDocuments().get(0);
                            String storedPassword = document.getString("password");

                            if (storedPassword != null && storedPassword.equals(password)) {
                                Toast.makeText(this, "Login successful!", Toast.LENGTH_SHORT).show();

                                String firstName = document.getString("firstname");
                                if (firstName == null) firstName = document.getString("firstName");
                                String lastName = document.getString("lastname");
                                if (lastName == null) lastName = document.getString("lastName");
                                String email = document.getString("email");
                                String contact = document.getString("phone");
                                String region = document.getString("region");
                                String city = document.getString("city");
                                String barangay = document.getString("barangay");

                                sessionManager.saveResidentDetails(
                                        document.getId(),
                                        username,
                                        firstName != null ? firstName : "",
                                        lastName != null ? lastName : "",
                                        email != null ? email : "",
                                        contact != null ? contact : ""
                                );
                                sessionManager.saveExtraResidentInfo(region, city, barangay);

                                updateFcmToken(document.getId());
                                goToResidentLandingPage();

                            } else {
                                Toast.makeText(this, "Incorrect password", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Toast.makeText(this, "Resident not found", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error fetching resident", e);
                        Toast.makeText(this, "Error connecting to Firestore", Toast.LENGTH_SHORT).show();
                    });
        });

        // 🔹 Sign Up Redirect
        signUpPrompt.setOnClickListener(v ->
                startActivity(new Intent(this, ResidentRegisterActivity.class)));

        // 🔹 Forgot Password Flow
        forgotPasswordLink.setOnClickListener(v -> showUsernameDialog());

        // 🔹 Back Button
        btnBack.setOnClickListener(v -> finish());
    }

    // Step 1: Ask for username
    private void showUsernameDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Reset Password");

        final EditText inputUsername = new EditText(this);
        inputUsername.setHint("Enter your username");
        builder.setView(inputUsername);

        builder.setPositiveButton("Next", (dialog, which) -> {
            String username = inputUsername.getText().toString().trim();
            if (username.isEmpty()) {
                Toast.makeText(this, "Please enter your username", Toast.LENGTH_SHORT).show();
                return;
            }
            sendResetCode(username);
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        builder.show();
    }

    // Step 2: Send reset code via Cloud Function or fallback simulation
    private void sendResetCode(String username) {
        db.collection("residents")
                .whereEqualTo("username", username)
                .limit(1)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.isEmpty()) {
                        Toast.makeText(this, "Username not found", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    DocumentSnapshot doc = snapshot.getDocuments().get(0);
                    String phone = doc.getString("phone");
                    String docId = doc.getId();

                    if (phone == null || phone.isEmpty()) {
                        Toast.makeText(this, "No phone number found for this account", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    Map<String, Object> data = new HashMap<>();
                    data.put("phone", phone);

                    functions.getHttpsCallable("sendVerificationCode")
                            .call(data)
                            .addOnSuccessListener((HttpsCallableResult result) -> {
                                Toast.makeText(this, "Verification code sent via SMS.", Toast.LENGTH_SHORT).show();
                                showVerificationDialog(username);
                            })
                            .addOnFailureListener(e -> {
                                Log.w(TAG, "Cloud function failed, simulating SMS", e);
                                simulateSendResetCode(username, phone, docId);
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error fetching user", e);
                    Toast.makeText(this, "Error connecting to Firestore", Toast.LENGTH_SHORT).show();
                });
    }

    // Fallback simulation
    private void simulateSendResetCode(String username, String phone, String docId) {
        String code = String.format("%06d", new Random().nextInt(1_000_000));
        long timestamp = System.currentTimeMillis();

        Map<String, Object> updates = new HashMap<>();
        updates.put("resetCode", code);
        updates.put("resetTimestamp", timestamp);

        db.collection("residents").document(docId).update(updates)
                .addOnSuccessListener(aVoid -> {
                    new AlertDialog.Builder(this)
                            .setTitle("Verification Code Sent (Simulated)")
                            .setMessage("Code sent to " + phone + "\n\n(for testing: " + code + ")")
                            .setPositiveButton("OK", (d, w) -> showVerificationDialog(username))
                            .show();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to send code", Toast.LENGTH_SHORT).show());
    }

    // Step 3: Verify code and reset password
    private void showVerificationDialog(String username) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Verify Code & Reset Password");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);

        final EditText inputCode = new EditText(this);
        inputCode.setHint("Enter verification code");
        layout.addView(inputCode);

        final EditText inputNewPassword = new EditText(this);
        inputNewPassword.setHint("Enter new password");
        layout.addView(inputNewPassword);

        builder.setView(layout);

        builder.setPositiveButton("Reset", (dialog, which) -> {
            String codeEntered = inputCode.getText().toString().trim();
            String newPassword = inputNewPassword.getText().toString().trim();

            if (codeEntered.isEmpty() || newPassword.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            verifyCodeAndReset(username, codeEntered, newPassword);
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        builder.show();
    }

    // Step 4: Verify code (via Cloud Function or local fallback)
    private void verifyCodeAndReset(String username, String enteredCode, String newPassword) {
        db.collection("residents").whereEqualTo("username", username).limit(1)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.isEmpty()) {
                        Toast.makeText(this, "User not found", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    DocumentSnapshot doc = snapshot.getDocuments().get(0);
                    String phone = doc.getString("phone");
                    String docId = doc.getId();

                    Map<String, Object> payload = new HashMap<>();
                    payload.put("phone", phone);
                    payload.put("code", enteredCode);

                    functions.getHttpsCallable("verifyCode")
                            .call(payload)
                            .addOnSuccessListener(result -> updatePasswordCloud(docId, newPassword))
                            .addOnFailureListener(e -> {
                                Log.w(TAG, "Cloud verify failed, checking locally", e);
                                verifyAndResetPasswordFallback(docId, enteredCode, newPassword);
                            });
                });
    }

    // Cloud: update password via secure function
    private void updatePasswordCloud(String docId, String newPassword) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("docId", docId);
        payload.put("newPassword", newPassword);

        functions.getHttpsCallable("updatePassword")
                .call(payload)
                .addOnSuccessListener(result ->
                        Toast.makeText(this, "Password updated successfully!", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Cloud updatePassword failed, falling back", e);
                    updatePasswordLocal(docId, newPassword);
                });
    }

    // Local fallback if Cloud update fails
    private void updatePasswordLocal(String docId, String newPassword) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("password", newPassword);
        updates.put("resetCode", null);
        updates.put("resetTimestamp", null);

        db.collection("residents").document(docId).update(updates)
                .addOnSuccessListener(aVoid ->
                        Toast.makeText(this, "Password reset successfully!", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to reset password", Toast.LENGTH_SHORT).show());
    }

    private void verifyAndResetPasswordFallback(String docId, String enteredCode, String newPassword) {
        db.collection("residents").document(docId).get()
                .addOnSuccessListener(doc -> {
                    String storedCode = doc.getString("resetCode");
                    Long timestamp = doc.getLong("resetTimestamp");

                    if (storedCode == null || timestamp == null) {
                        Toast.makeText(this, "No active reset code", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (!storedCode.equals(enteredCode)) {
                        Toast.makeText(this, "Invalid code", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (System.currentTimeMillis() - timestamp > RESET_CODE_EXPIRATION_MS) {
                        Toast.makeText(this, "Code expired", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    updatePasswordLocal(docId, newPassword);
                });
    }

    // 🔹 Update FCM token
    private void updateFcmToken(String residentId) {
        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) return;
                    String token = task.getResult();
                    if (token == null || token.isEmpty()) return;

                    Map<String, Object> updateData = new HashMap<>();
                    updateData.put("fcmToken", token);

                    db.collection("residents").document(residentId).update(updateData);
                });
    }

    private void goToResidentLandingPage() {
        Intent intent = new Intent(this, ResidentLandingPageActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }
}
