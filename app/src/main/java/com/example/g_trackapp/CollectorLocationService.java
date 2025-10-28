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
    private NotificationManager notificationManager;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "CollectorLocationService created");

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        db = FirebaseFirestore.getInstance();
        sessionManager = new CollectorSessionManager(this);
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        createNotificationChannel();

        // ✅ Firestore real-time debug log
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
        String action = (intent != null) ? intent.getAction() : "";

        if ("START_LOCATION_UPDATES".equals(action)) {
            Log.d(TAG, "Starting location updates...");
            startAsForeground();
            startLocationUpdates();
        } else if ("STOP_LOCATION_UPDATES".equals(action)) {
            Log.d(TAG, "Stopping location updates...");
            stopLocationUpdates();
            stopForeground(true);
            stopSelf();
        }

        return START_STICKY; // Keeps the service alive if killed
    }

    /**
     * Safely start foreground service with persistent notification
     */
    private void startAsForeground() {
        Notification notification = buildNotification("Tracking Active", "Updating location...");
        try {
            startForeground(NOTIFICATION_ID, notification);
        } catch (Exception e) {
            Log.e(TAG, "Failed to start foreground service", e);
        }
    }

    /**
     * Build notification dynamically
     */
    private Notification buildNotification(String title, String message) {
        Intent notificationIntent = new Intent(this, CollectorLandingPageActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(message)
                .setSmallIcon(R.drawable.ic_location) // ✅ ensure icon exists in res/drawable
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    /**
     * Create notification channel (Android O+)
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Collector Location Tracking",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Tracks the collector’s real-time location");
            notificationManager.createNotificationChannel(channel);
        }
    }

    /**
     * Request continuous location updates with safety checks
     */
    private void startLocationUpdates() {
        LocationRequest locationRequest = new LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY, 5000 // every 5 seconds
        )
                .setMinUpdateDistanceMeters(10) // update every 10 meters
                .build();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                for (Location location : locationResult.getLocations()) {
                    if (location != null) {
                        double lat = location.getLatitude();
                        double lon = location.getLongitude();
                        updateCollectorLocation(lat, lon);
                        Log.d(TAG, "Updated location: " + lat + ", " + lon);

                        // Optional: update notification dynamically
                        updateNotification("Tracking Active", "Lat: " + lat + ", Lon: " + lon);
                    }
                }
            }
        };

        if (!hasLocationPermission()) {
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

    private boolean hasLocationPermission() {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Firestore: Update collector's location
     */
    private void updateCollectorLocation(double latitude, double longitude) {
        String collectorId = sessionManager.getCollectorId();
        if (collectorId == null || collectorId.trim().isEmpty()) {
            Log.w(TAG, "No collector ID found in session — cannot update Firestore");
            return;
        }

        db.collection("collectors").document(collectorId)
                .update("latitude", latitude, "longitude", longitude)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Firestore updated successfully"))
                .addOnFailureListener(e -> Log.e(TAG, "Error updating Firestore", e));
    }

    /**
     * Stop updates and clean up
     */
    private void stopLocationUpdates() {
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
            Log.d(TAG, "Location updates stopped");
        }
    }

    private void updateNotification(String title, String content) {
        Notification notification = buildNotification(title, content);
        notificationManager.notify(NOTIFICATION_ID, notification);
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
