/**
 * 🔔 Firebase Cloud Functions (G-Track)
 * Handles:
 * 1. Collector location → Resident notifications (within 50m)
 * 2. SMS verification via Twilio (live)
 * 3. Unified OTP-based password reset (Collectors + Residents)
 * 4. Secure server-side resident login
 */

const { onDocumentWritten } = require("firebase-functions/v2/firestore");
const { onCall } = require("firebase-functions/v2/https");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");
const functions = require("firebase-functions");
const twilio = require("twilio");

initializeApp();
const db = getFirestore();

/* -------------------------------------------------------------------------- */
/* 🧭 1. NEARBY RESIDENT NOTIFICATIONS                                         */
/* -------------------------------------------------------------------------- */

const haversineDistance = (a, b) => {
  const toRad = (x) => (x * Math.PI) / 180;
  const R = 6371000;
  const dLat = toRad(b.latitude - a.latitude);
  const dLon = toRad(b.longitude - a.longitude);
  const lat1 = toRad(a.latitude);
  const lat2 = toRad(b.latitude);
  const c =
    2 *
    Math.atan2(
      Math.sqrt(
        Math.sin(dLat / 2) ** 2 +
          Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) ** 2
      ),
      Math.sqrt(
        1 -
          (Math.sin(dLat / 2) ** 2 +
            Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) ** 2)
      )
    );
  return R * c;
};

const notifiedResidents = new Map();
const COOLDOWN_MS = 5 * 60 * 1000;

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
    if (!after?.latitude || !after?.longitude) return;

    if (
      before &&
      before.latitude === after.latitude &&
      before.longitude === after.longitude
    )
      return console.log("No change in location — skip.");

    const collectorName = after.name || after.truckNumber || "Collector";
    const collectorLocation = { latitude: after.latitude, longitude: after.longitude };

    const residents = await db.collection("residents").get();
    const now = Date.now();
    const sendPromises = [];

    for (const doc of residents.docs) {
      const resident = doc.data();
      if (!resident.latitude || !resident.longitude || !resident.fcmToken) continue;

      const distance = haversineDistance(collectorLocation, resident);
      const last = notifiedResidents.get(doc.id) || 0;
      if (distance <= 50 && now - last > COOLDOWN_MS) {
        notifiedResidents.set(doc.id, now);

        sendPromises.push(
          getMessaging()
            .send({
              token: resident.fcmToken,
              notification: {
                title: `${collectorName} is nearby`,
                body: "Your garbage collector is within 50 meters. Please prepare your garbage.",
              },
            })
            .then(() => console.log(`✅ Sent FCM to ${resident.username}`))
            .catch((e) => console.error(`❌ Failed to send FCM`, e))
        );

        sendPromises.push(
          db.collection("notifications").add({
            residentId: doc.id,
            message: `${collectorName} is nearby — please prepare your garbage.`,
            read: false,
            timestamp: new Date(),
          })
        );
      }
    }

    await Promise.all(sendPromises);
    console.log("✅ Notifications processed.");
  }
);

/* -------------------------------------------------------------------------- */
/* 📱 2. TWILIO SMS SETUP                                                     */
/* -------------------------------------------------------------------------- */

const accountSid = process.env.TWILIO_SID || functions.config().twilio.sid;
const authToken = process.env.TWILIO_TOKEN || functions.config().twilio.token;
const twilioNumber = process.env.TWILIO_PHONE || functions.config().twilio.phone;
const client = twilio(accountSid, authToken);

const sendSms = async (phone, code) => {
  await client.messages.create({
    body: `Your G-Track verification code is ${code}`,
    from: twilioNumber,
    to: phone,
  });
};

const generateCode = () => Math.floor(100000 + Math.random() * 900000).toString();
const CODE_EXPIRY_MS = 5 * 60 * 1000;

/* -------------------------------------------------------------------------- */
/* 🔐 3. UNIFIED OTP-BASED PASSWORD RESET                                     */
/* -------------------------------------------------------------------------- */

/**
 * ✅ Step 1: Verify user existence by username
 */
exports.verifyUserForReset = onCall(async (req) => {
  const { username, userType } = req.data;
  if (!username || !userType)
    throw new functions.https.HttpsError("invalid-argument", "Missing username or userType.");

  const collection = userType === "collector" ? "collectors" : "residents";
  const snapshot = await db.collection(collection).where("username", "==", username).limit(1).get();

  if (snapshot.empty) return { success: false, message: "User not found" };

  const user = snapshot.docs[0].data();
  const phone = user.phone || null;

  if (!phone) return { success: false, message: "No phone number on record" };

  return { success: true, phone };
});

/**
 * ✅ Step 2: Send OTP (via Twilio)
 */
exports.sendOtp = onCall(async (req) => {
  const { username, userType } = req.data;
  if (!username || !userType)
    throw new functions.https.HttpsError("invalid-argument", "Missing fields.");

  const collection = userType === "collector" ? "collectors" : "residents";
  const snapshot = await db.collection(collection).where("username", "==", username).limit(1).get();

  if (snapshot.empty) return { success: false, message: "User not found" };

  const userDoc = snapshot.docs[0];
  const phone = userDoc.get("phone");
  if (!phone) return { success: false, message: "No phone number found" };

  const otp = generateCode();

  await userDoc.ref.update({
    resetCode: otp,
    resetTimestamp: Date.now(),
  });

  await sendSms(phone, otp);
  console.log(`📲 Sent OTP to ${phone} (${collection})`);

  return { success: true };
});

/**
 * ✅ Step 3: Verify OTP & update password
 */
exports.verifyOtpAndUpdatePassword = onCall(async (req) => {
  const { username, userType, otp, newPassword } = req.data;
  if (!username || !userType || !otp || !newPassword)
    throw new functions.https.HttpsError("invalid-argument", "Missing required fields.");

  const collection = userType === "collector" ? "collectors" : "residents";
  const snapshot = await db.collection(collection).where("username", "==", username).limit(1).get();

  if (snapshot.empty)
    throw new functions.https.HttpsError("not-found", "User not found.");

  const userDoc = snapshot.docs[0];
  const storedOtp = userDoc.get("resetCode");
  const timestamp = userDoc.get("resetTimestamp");

  if (storedOtp !== otp)
    throw new functions.https.HttpsError("permission-denied", "Invalid OTP.");
  if (Date.now() - timestamp > CODE_EXPIRY_MS)
    throw new functions.https.HttpsError("deadline-exceeded", "OTP expired.");

  await userDoc.ref.update({
    password: newPassword,
    resetCode: FieldValue.delete(),
    resetTimestamp: FieldValue.delete(),
  });

  console.log(`✅ Password updated for ${username} (${collection})`);
  return { success: true };
});

/* -------------------------------------------------------------------------- */
/* 🔒 4. SECURE RESIDENT LOGIN                                                */
/* -------------------------------------------------------------------------- */

exports.verifyResidentLogin = onCall(async (req) => {
  const { username, password } = req.data;
  if (!username || !password)
    throw new functions.https.HttpsError("invalid-argument", "Missing fields.");

  const snap = await db.collection("residents").where("username", "==", username).limit(1).get();
  if (snap.empty)
    throw new functions.https.HttpsError("not-found", "Resident not found.");

  const doc = snap.docs[0];
  const data = doc.data();

  if (data.password !== password)
    throw new functions.https.HttpsError("unauthenticated", "Invalid password.");

  return {
    success: true,
    residentId: doc.id,
    firstName: data.firstname || data.firstName || "",
    lastName: data.lastname || data.lastName || "",
    email: data.email || "",
    phone: data.phone || "",
    region: data.region || "",
    city: data.city || "",
    barangay: data.barangay || "",
  };
});
