package com.example.g_trackapp;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.HashMap;
import java.util.Map;

public class ResidentLocateNowActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLocationClient;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;
    private ResidentSessionManager sessionManager;
    private FirebaseFirestore db;

    private final Map<String, Marker> collectorMarkers = new HashMap<>();
    private final Map<String, Marker> dropOffMarkers = new HashMap<>();
    private ListenerRegistration collectorListener;
    private ListenerRegistration dropOffListener;

    private boolean firstCollectorShown = false;
    private boolean firstDropOffShown = false;

    private Marker activeLabelMarker = null; // current visible label marker

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_resident_locate_now);

        sessionManager = new ResidentSessionManager(this);
        db = FirebaseFirestore.getInstance();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        requestLocationPermission();

        SupportMapFragment mapFragment =
                (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.mapFragment);
        if (mapFragment != null) mapFragment.getMapAsync(this);

        findViewById(R.id.btnYourLocation).setOnClickListener(v -> getDeviceLocation());
        findViewById(R.id.btnDropOffLocation).setOnClickListener(v ->
                Toast.makeText(this, "Drop-off location selected", Toast.LENGTH_SHORT).show()
        );

        findViewById(R.id.btnMenu).setOnClickListener(v -> showPopupMenu((ImageView) v));

        ImageView btnBackBottom = findViewById(R.id.btnBack);
        if (btnBackBottom != null) btnBackBottom.setOnClickListener(v -> finish());

        ImageView btnHome = findViewById(R.id.btnHome);
        if (btnHome != null) {
            btnHome.setOnClickListener(v -> {
                Intent intent = new Intent(this, ResidentMainMenuActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
            });
        }

        ImageView btnAlarm = findViewById(R.id.btnAlarm);
        if (btnAlarm != null) {
            btnAlarm.setOnClickListener(v ->
                    Toast.makeText(this, "Alarm clicked", Toast.LENGTH_SHORT).show()
            );
        }
    }

    private void requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE
            );
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
            }
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.getUiSettings().setZoomControlsEnabled(true);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);
            getDeviceLocation();
            listenToCollectors();
            listenToDropOffLocations();
        } else {
            requestLocationPermission();
        }

        // 👇 Show label bubble when marker tapped
        mMap.setOnMarkerClickListener(marker -> {
            showLabelAboveMarker(marker);
            return true;
        });
    }

    private void getDeviceLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(this, location -> {
                        if (location != null && mMap != null) {
                            LatLng currentLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15));
                        } else {
                            Toast.makeText(this, "Unable to detect your location", Toast.LENGTH_SHORT).show();
                        }
                    });
        }
    }

    /** ✅ Listen for collectors (icon only) */
    private void listenToCollectors() {
        collectorListener = db.collection("collectors").addSnapshotListener((querySnapshot, e) -> {
            if (e != null || querySnapshot == null) return;

            for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                String id = doc.getId();
                Double lat = doc.getDouble("latitude");
                Double lon = doc.getDouble("longitude");
                String name = doc.getString("firstName");

                if (lat == null || lon == null) continue;
                LatLng newPos = new LatLng(lat, lon);

                Marker existingMarker = collectorMarkers.get(id);
                if (existingMarker == null) {
                    MarkerOptions options = new MarkerOptions()
                            .position(newPos)
                            .title(name != null ? name : "Collector")
                            .icon(getMarkerIcon(R.drawable.ic_garbage_truck));

                    Marker marker = mMap.addMarker(options);
                    collectorMarkers.put(id, marker);

                    if (!firstCollectorShown) {
                        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(newPos, 14f));
                        firstCollectorShown = true;
                    }
                } else {
                    animateMarkerSmoothly(existingMarker, newPos);
                }
            }
        });
    }

    /** ✅ Listen for drop-off locations (icon only) */
    private void listenToDropOffLocations() {
        dropOffListener = db.collection("dropofflocation").addSnapshotListener((querySnapshot, e) -> {
            if (e != null || querySnapshot == null) return;

            for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                String id = doc.getId();
                Double lat = doc.getDouble("latitude");
                Double lon = doc.getDouble("longitude"); // ✅ FIXED
                String locationName = doc.getString("location");

                if (lat == null || lon == null) continue;
                LatLng pos = new LatLng(lat, lon);

                if (!dropOffMarkers.containsKey(id)) {
                    MarkerOptions options = new MarkerOptions()
                            .position(pos)
                            .title(locationName != null ? locationName : "Drop-off")
                            .icon(getMarkerIcon(R.drawable.ic_garbage_bin));

                    Marker marker = mMap.addMarker(options);
                    dropOffMarkers.put(id, marker);

                    if (!firstDropOffShown) {
                        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(pos, 14f));
                        firstDropOffShown = true;
                    }
                }
            }
        });
    }


    /** 🎯 Show white rounded label above tapped marker */
    private void showLabelAboveMarker(Marker marker) {
        if (mMap == null || marker == null) return;

        if (activeLabelMarker != null) {
            activeLabelMarker.remove();
            activeLabelMarker = null;
        }

        String labelText = marker.getTitle();
        if (labelText == null || labelText.trim().isEmpty()) return;

        View labelView = getLayoutInflater().inflate(R.layout.custom_marker_label_only, null);
        TextView label = labelView.findViewById(R.id.markerLabelOnly);
        if (label == null) return;
        label.setText(labelText);

        AlphaAnimation fadeIn = new AlphaAnimation(0f, 1f);
        fadeIn.setDuration(300);
        labelView.startAnimation(fadeIn);

        Bitmap labelBitmap = createBitmapFromView(labelView);
        if (labelBitmap == null) return;

        LatLng pos = marker.getPosition();
        LatLng above = new LatLng(pos.latitude + 0.00025, pos.longitude);

        activeLabelMarker = mMap.addMarker(new MarkerOptions()
                .position(above)
                .icon(BitmapDescriptorFactory.fromBitmap(labelBitmap))
                .anchor(0.5f, 1f)
                .zIndex(9999f));
    }

    /** 🧱 Create icon-only marker bitmap */
    private BitmapDescriptor getMarkerIcon(int iconRes) {
        View markerView = getLayoutInflater().inflate(R.layout.custom_marker_layout, null);
        ImageView icon = markerView.findViewById(R.id.markerIcon);
        icon.setImageResource(iconRes);
        markerView.findViewById(R.id.markerLabel).setVisibility(View.GONE);

        markerView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        markerView.layout(0, 0, markerView.getMeasuredWidth(), markerView.getMeasuredHeight());

        Bitmap bitmap = Bitmap.createBitmap(
                markerView.getMeasuredWidth(),
                markerView.getMeasuredHeight(),
                Bitmap.Config.ARGB_8888
        );
        Canvas canvas = new Canvas(bitmap);
        markerView.draw(canvas);
        return BitmapDescriptorFactory.fromBitmap(bitmap);
    }

    /** 🧱 Convert any view to bitmap */
    private Bitmap createBitmapFromView(View view) {
        view.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
        Bitmap bitmap = Bitmap.createBitmap(
                view.getMeasuredWidth(),
                view.getMeasuredHeight(),
                Bitmap.Config.ARGB_8888
        );
        Canvas canvas = new Canvas(bitmap);
        view.draw(canvas);
        return bitmap;
    }

    /** 🚗 Animate collector movement smoothly */
    private void animateMarkerSmoothly(Marker marker, LatLng newPosition) {
        if (marker == null || newPosition == null) return;
        final LatLng start = marker.getPosition();
        final long startTime = System.currentTimeMillis();
        final long duration = 1000;
        Handler handler = new Handler();

        handler.post(new Runnable() {
            @Override
            public void run() {
                long elapsed = System.currentTimeMillis() - startTime;
                float t = Math.min(1, (float) elapsed / duration);
                double lat = (newPosition.latitude - start.latitude) * t + start.latitude;
                double lng = (newPosition.longitude - start.longitude) * t + start.longitude;
                marker.setPosition(new LatLng(lat, lng));
                if (t < 1.0) handler.postDelayed(this, 16);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (collectorListener != null) collectorListener.remove();
        if (dropOffListener != null) dropOffListener.remove();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (mMap != null && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED) {
                    mMap.setMyLocationEnabled(true);
                    getDeviceLocation();
                    listenToCollectors();
                    listenToDropOffLocations();
                }
            } else {
                Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show();
            }
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
            startActivity(new Intent(this, ResidentAccountSettingActivity.class));
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
