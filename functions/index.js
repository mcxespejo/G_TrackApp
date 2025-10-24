/**
 * Firebase Cloud Function to notify residents when a collector is nearby (within 50 meters),
 * with support for filtering/logging by username.
 */

const { onDocumentWritten } = require("firebase-functions/v2/firestore");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");

initializeApp();
const db = getFirestore();

// Helper: Haversine distance (in meters)
const haversineDistance = (coords1, coords2) => {
  const toRad = (x) => (x * Math.PI) / 180;
  const R = 6371000;
  const dLat = toRad(coords2.latitude - coords1.latitude);
  const dLon = toRad(coords2.longitude - coords1.longitude);
  const lat1 = toRad(coords1.latitude);
  const lat2 = toRad(coords2.latitude);

  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.sin(dLon / 2) ** 2 * Math.cos(lat1) * Math.cos(lat2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  return R * c;
};

// Prevent duplicate notifications
const notifiedResidents = new Map(); // residentId → timestamp
const COOLDOWN_MS = 5 * 60 * 1000; // 5-minute cooldown

exports.notifyNearbyResidents = onDocumentWritten(
  {
    document: "collectors/{collectorId}",
    region: "asia-northeast1",
    timeoutSeconds: 60,
    memory: "256MiB",
  },
  async (event) => {
    const before = event.data?.before?.data();
    const after = event.data?.after?.data();

    if (!after || !after.latitude || !after.longitude) return;
    if (before && before.latitude === after.latitude && before.longitude === after.longitude) {
      console.log("No significant location change — skipping notification.");
      return;
    }

    const collectorId = event.params.collectorId;
    const collectorName = after.name || after.truckNumber || "Collector";
    const collectorLocation = {
      latitude: after.latitude,
      longitude: after.longitude,
    };

    console.log(`🚛 ${collectorName} (${collectorId}) moved to:`, collectorLocation);

    // Optional: Filter by username (for testing or targeted alerts)
    // Set this to a username to only notify that resident, or null to notify everyone nearby
    const TARGET_USERNAME = null; // e.g. "resident123"

    // Fetch residents
    let residentsQuery = db.collection("residents");
    if (TARGET_USERNAME) {
      residentsQuery = residentsQuery.where("username", "==", TARGET_USERNAME);
    }

    const residentsSnapshot = await residentsQuery.get();
    const now = Date.now();
    const notificationPromises = [];

    for (const doc of residentsSnapshot.docs) {
      const resident = doc.data();
      const residentId = doc.id;
      const username = resident.username || "UnknownUser";

      if (!resident.latitude || !resident.longitude || !resident.fcmToken) {
        console.log(`⚠️ Skipping ${username} (${residentId}) — missing location or FCM token`);
        continue;
      }

      const distance = haversineDistance(collectorLocation, resident);
      console.log(`👤 ${username} is ${distance.toFixed(1)}m away`);

      const lastNotified = notifiedResidents.get(residentId) || 0;
      const recentlyNotified = now - lastNotified < COOLDOWN_MS;

      if (distance <= 50) {
        if (recentlyNotified) {
          console.log(`⏳ Skipping ${username} — still in cooldown`);
          continue;
        }

        // Update last notified time
        notifiedResidents.set(residentId, now);

        // Push FCM notification
        const message = {
          token: resident.fcmToken,
          notification: {
            title: `${collectorName} is nearby`,
            body: "Your garbage collector is within 50 meters. Please prepare your garbage.",
          },
        };

        notificationPromises.push(
          getMessaging()
            .send(message)
            .then(() => console.log(`✅ Sent notification to ${username}`))
            .catch((err) => console.error(`❌ Failed to send to ${username}:`, err))
        );

        // 🔹 Also save notification inside Firestore for in-app display
        notificationPromises.push(
          db.collection("notifications").add({
            residentId: residentId,
            username: username,
            message: `${collectorName} is nearby — please prepare your garbage.`,
            read: false,
            timestamp: new Date(),
          })
        );
      } else if (recentlyNotified) {
        // Reset cooldown once collector leaves the 50m range
        console.log(`📍 ${username} is now out of range — cooldown reset`);
        notifiedResidents.delete(residentId);
      }
    }

    await Promise.all(notificationPromises);
    console.log("✅ All notifications processed successfully.");
  }
);
