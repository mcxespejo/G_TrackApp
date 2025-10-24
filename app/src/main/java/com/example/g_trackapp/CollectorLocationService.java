package com.example.g_trackapp;

import android.Manifest;
import android.app.*;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import com.google.android.gms.location.*;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.DocumentChange;

public class CollectorLocationService extends Service {

    private static final String TAG = "CollectorLocationService";
    private static final String CHANNEL_ID = "collector_location_channel";
    private static final int NOTIFICATION_ID = 101;

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private FirebaseFirestore db;
    private CollectorSessionManager sessionManager;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "CollectorLocationService created");

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        db = FirebaseFirestore.getInstance();
        sessionManager = new CollectorSessionManager(this);

        createNotificationChannel();

        // ✅ FIRESTORE REAL-TIME VERIFICATION LOG
        db.collection("collectors")
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) {
                        Log.e(TAG, "Firestore listen failed", e);
                        return;
                    }

                    for (DocumentChange dc : snapshots.getDocumentChanges()) {
                        Log.d(TAG, "Firestore changed: " + dc.getDocument().getId()
                                + " → " + dc.getDocument().getData());
                    }
                });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : "";

        if ("START_LOCATION_UPDATES".equals(action)) {
            Log.d(TAG, "Starting location updates...");
            startForegroundService();
            startLocationUpdates();
        } else if ("STOP_LOCATION_UPDATES".equals(action)) {
            Log.d(TAG, "Stopping location updates...");
            stopLocationUpdates();
            stopForeground(true);
            stopSelf();
        }

        return START_STICKY; // Ensures the service restarts if killed by the system
    }

    private void startForegroundService() {
        Notification notification = buildNotification("Tracking active", "Updating location...");
        startForeground(NOTIFICATION_ID, notification);
    }

    private Notification buildNotification(String title, String message) {
        Intent notificationIntent = new Intent(this, CollectorLandingPageActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(message)
                .setSmallIcon(R.drawable.ic_location) // 🔹 Add this icon in res/drawable/
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Collector Location Tracking",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Tracks the collector’s location in real-time");
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    private void startLocationUpdates() {
        LocationRequest locationRequest = new LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY, 5000 // every 5 seconds
        ).setMinUpdateDistanceMeters(10) // update every 10 meters if possible
                .build();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                for (Location location : locationResult.getLocations()) {
                    if (location != null) {
                        double lat = location.getLatitude();
                        double lon = location.getLongitude();
                        updateCollectorLocation(lat, lon);
                        Log.d(TAG, "Location updated: " + lat + ", " + lon);
                    }
                }
            }
        };

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Location permission not granted — stopping service");
            stopSelf();
            return;
        }

        fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                getMainLooper()
        );
    }

    private void updateCollectorLocation(double latitude, double longitude) {
        String collectorId = sessionManager.getCollectorId();
        if (collectorId == null) {
            Log.w(TAG, "No collector ID found in session");
            return;
        }

        db.collection("collectors").document(collectorId)
                .update("latitude", latitude, "longitude", longitude)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Firestore updated successfully"))
                .addOnFailureListener(e -> Log.e(TAG, "Error updating Firestore", e));
    }

    private void stopLocationUpdates() {
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
            Log.d(TAG, "Location updates stopped");
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopLocationUpdates();
        Log.d(TAG, "CollectorLocationService destroyed");
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null; // Not a bound service
    }
}
