package io.github.rygel.outerstellar.platform.service

import org.slf4j.LoggerFactory

/**
 * A push notification payload.
 *
 * @param title notification title shown in the OS notification centre
 * @param body notification body text
 * @param data optional key/value data delivered to the app in the background
 */
data class PushNotification(
    val title: String,
    val body: String,
    val data: Map<String, String> = emptyMap(),
)

/**
 * Strategy interface for delivering push notifications.
 *
 * Implementations:
 * - [ConsolePushNotificationService] — logs to SLF4J (development / stub)
 * - FcmPushNotificationService — TODO: send via Firebase Cloud Messaging (Android)
 * - ApnsPushNotificationService — TODO: send via Apple Push Notification service (iOS)
 */
interface PushNotificationService {
    /**
     * Send [notification] to a single device identified by [deviceToken].
     *
     * @param platform "android" or "ios"
     * @param deviceToken the FCM registration token or APNs device token
     */
    fun send(platform: String, deviceToken: String, notification: PushNotification)

    /** Send [notification] to all [deviceTokens] (may be a mix of platforms). */
    fun sendToAll(tokens: List<Pair<String, String>>, notification: PushNotification) {
        tokens.forEach { (platform, token) -> send(platform, token, notification) }
    }
}

/** Development stub — logs notifications to console instead of calling FCM / APNs. */
object ConsolePushNotificationService : PushNotificationService {
    private val logger = LoggerFactory.getLogger(ConsolePushNotificationService::class.java)

    override fun send(platform: String, deviceToken: String, notification: PushNotification) {
        logger.info(
            "[PUSH] platform={} token={} title='{}' body='{}' data={}",
            platform,
            deviceToken.take(12) + "...",
            notification.title,
            notification.body,
            notification.data,
        )
    }
}

/**
 * Firebase Cloud Messaging (FCM) stub for Android push notifications.
 *
 * Replace with a real implementation using the Firebase Admin SDK:
 * https://firebase.google.com/docs/cloud-messaging/server
 *
 * Required: a service-account JSON key file (google-services.json) from the Firebase console.
 */
class FcmPushNotificationService(
    /** Path to or contents of the Firebase service account JSON. */
    @Suppress("UnusedPrivateMember")
    private val serviceAccountJson: String = "TODO_FCM_SERVICE_ACCOUNT_JSON"
) : PushNotificationService {
    private val logger = LoggerFactory.getLogger(FcmPushNotificationService::class.java)

    override fun send(platform: String, deviceToken: String, notification: PushNotification) {
        if (platform != "android") return
        // TODO: initialize FirebaseApp with serviceAccountJson, then call
        //   FirebaseMessaging.getInstance().send(Message.builder()
        //
        // .setNotification(Notification.builder().setTitle(notification.title).setBody(notification.body).build())
        //       .putAllData(notification.data)
        //       .setToken(deviceToken)
        //       .build())
        logger.warn(
            "FcmPushNotificationService not yet implemented — dropping notification to {}",
            deviceToken.take(12),
        )
    }
}

/**
 * Apple Push Notification service (APNs) stub for iOS push notifications.
 *
 * Replace with a real implementation using the Apple HTTP/2 APNs API or a library like java-apns or
 * pushy: https://github.com/jchambers/pushy
 *
 * Required:
 * - An APNs auth key (.p8 file) from the Apple Developer portal
 * - Your Team ID and Key ID
 * - Your app's bundle identifier
 */
class ApnsPushNotificationService(
    @Suppress("UnusedPrivateMember")
    private val privateKeyPem: String = "TODO_APNS_PRIVATE_KEY_PEM",
    @Suppress("UnusedPrivateMember") private val teamId: String = "TODO_TEAM_ID",
    @Suppress("UnusedPrivateMember") private val keyId: String = "TODO_KEY_ID",
    @Suppress("UnusedPrivateMember") private val bundleId: String = "TODO_BUNDLE_ID",
) : PushNotificationService {
    private val logger = LoggerFactory.getLogger(ApnsPushNotificationService::class.java)

    override fun send(platform: String, deviceToken: String, notification: PushNotification) {
        if (platform != "ios") return
        // TODO: sign a JWT with privateKeyPem/teamId/keyId, then POST to
        //   https://api.push.apple.com/3/device/{deviceToken}
        //   with the apns-topic header set to bundleId.
        logger.warn(
            "ApnsPushNotificationService not yet implemented — dropping notification to {}",
            deviceToken.take(12),
        )
    }
}
