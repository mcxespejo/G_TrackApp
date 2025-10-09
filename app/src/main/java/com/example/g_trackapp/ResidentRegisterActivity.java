package com.example.g_trackapp;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.*;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.model.AddressComponent;
import com.google.android.libraries.places.api.model.AddressComponents;
import com.google.android.libraries.places.widget.Autocomplete;
import com.google.android.libraries.places.widget.AutocompleteActivity;
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode;
import com.google.firebase.FirebaseApp;
import com.google.firebase.firestore.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class ResidentRegisterActivity extends AppCompatActivity {

    private static final String TAG = "ResidentRegisterActivity";
    private static final int AUTOCOMPLETE_REQUEST_CODE = 100;
    private FirebaseFirestore db;

    // UI
    private EditText etUsername, etFirstName, etLastName, etPhone, etEmail, etPassword, etConfirmPassword;
    private EditText etRegion, etCity, etBarangay, etStreet; // address fields
    private Button btnRegister;
    private ImageView btnBack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_resident_register);

        // 🔹 Initialize Firebase
        FirebaseApp.initializeApp(this);
        db = FirebaseFirestore.getInstance();

        // 🔹 Initialize Places
        if (!Places.isInitialized()) {
            Places.initialize(getApplicationContext(), getString(R.string.google_maps_key));
        }

        // 🔹 Initialize UI elements
        etUsername = findViewById(R.id.etUsername);
        etFirstName = findViewById(R.id.etFirstName);
        etLastName = findViewById(R.id.etLastName);
        etRegion = findViewById(R.id.etRegion);
        etCity = findViewById(R.id.etCity);
        etBarangay = findViewById(R.id.etBarangay);
        etStreet = findViewById(R.id.etStreet);

        etPhone = findViewById(R.id.etPhone);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);

        btnRegister = findViewById(R.id.btnRegister);
        btnBack = findViewById(R.id.btnBack);

        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        // 🔹 Open Autocomplete when Street field is clicked
        etStreet.setOnClickListener(v -> {
            Intent intent = new Autocomplete.IntentBuilder(
                    AutocompleteActivityMode.FULLSCREEN,
                    Arrays.asList(
                            Place.Field.ID,
                            Place.Field.NAME,
                            Place.Field.ADDRESS,
                            Place.Field.ADDRESS_COMPONENTS,
                            Place.Field.LAT_LNG
                    )
            ).build(this);
            startActivityForResult(intent, AUTOCOMPLETE_REQUEST_CODE);
        });

        // 🔹 Register button logic
        btnRegister.setOnClickListener(v -> {
            String username = etUsername.getText().toString().trim();
            String firstName = etFirstName.getText().toString().trim();
            String lastName = etLastName.getText().toString().trim();
            String region = etRegion.getText().toString().trim();
            String city = etCity.getText().toString().trim();
            String barangay = etBarangay.getText().toString().trim();
            String street = etStreet.getText().toString().trim();
            String phone = etPhone.getText().toString().trim();
            String email = etEmail.getText().toString().trim();
            String password = etPassword.getText().toString().trim();
            String confirmPassword = etConfirmPassword.getText().toString().trim();

            if (username.isEmpty() || firstName.isEmpty() || lastName.isEmpty() ||
                    street.isEmpty() || phone.isEmpty() || email.isEmpty() ||
                    password.isEmpty() || confirmPassword.isEmpty()) {
                Toast.makeText(this, "All fields are required", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!password.equals(confirmPassword)) {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show();
                return;
            }

            String fullAddress = street + ", " + barangay + ", " + city + ", " + region;

            registerResident(username, firstName, lastName, fullAddress, region, city, barangay, street, phone, email, password);
        });
    }

    // 🔹 Handle Autocomplete result
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == AUTOCOMPLETE_REQUEST_CODE) {
            if (resultCode == RESULT_OK && data != null) {
                Place place = Autocomplete.getPlaceFromIntent(data);
                etStreet.setText(place.getAddress());

                AddressComponents components = place.getAddressComponents();
                if (components != null) {
                    for (AddressComponent ac : components.asList()) {
                        if (ac.getTypes().contains("administrative_area_level_1")) {
                            etRegion.setText(ac.getName());
                        } else if (ac.getTypes().contains("locality")) {
                            etCity.setText(ac.getName());
                        } else if (ac.getTypes().contains("sublocality_level_1")) {
                            etBarangay.setText(ac.getName());
                        } else if (ac.getTypes().contains("route")) {
                            etStreet.setText(ac.getName());
                        }
                    }
                }

                Log.d(TAG, "Place selected: " + place.getAddress());
            } else if (resultCode == AutocompleteActivity.RESULT_ERROR) {
                Toast.makeText(this, "Error selecting address", Toast.LENGTH_SHORT).show();
            } else if (resultCode == RESULT_CANCELED) {
                Log.d(TAG, "Autocomplete canceled");
            }
        }
    }

    // 🔹 Save resident to Firestore
    private void registerResident(String username, String firstName, String lastName,
                                  String fullAddress, String region, String city,
                                  String barangay, String street,
                                  String phone, String email, String password) {

        DocumentReference counterRef = db.collection("counters").document("residents_counter");

        db.runTransaction(transaction -> {
            DocumentSnapshot snapshot = transaction.get(counterRef);
            long newId = snapshot.exists() ? snapshot.getLong("lastId") + 1 : 1;
            transaction.set(counterRef, new HashMap<String, Object>() {{
                put("lastId", newId);
            }});
            return newId;
        }).addOnSuccessListener(newId -> {
            Map<String, Object> resident = new HashMap<>();
            resident.put("id", newId);
            resident.put("username", username);
            resident.put("firstName", firstName);
            resident.put("lastName", lastName);
            resident.put("fullAddress", fullAddress);
            resident.put("region", region);
            resident.put("city", city);
            resident.put("barangay", barangay);
            resident.put("street", street);
            resident.put("phone", phone);
            resident.put("email", email);
            resident.put("password", password);

            db.collection("residents").document(String.valueOf(newId))
                    .set(resident)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(this, "Registration successful!", Toast.LENGTH_SHORT).show();
                        startActivity(new Intent(this, ResidentLoginActivity.class));
                        finish();
                    });
        });
    }
}
