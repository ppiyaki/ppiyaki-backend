package com.ppiyaki.notification.push;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FcmPushSender implements PushSender {

    private static final Logger log = LoggerFactory.getLogger(FcmPushSender.class);

    private final FirebaseMessaging firebaseMessaging;

    public FcmPushSender(final FirebaseMessaging firebaseMessaging) {
        this.firebaseMessaging = firebaseMessaging;
    }

    @Override
    public PushSendResult send(final String deviceToken, final PushPayload payload) {
        final Message.Builder builder = Message.builder()
                .setToken(deviceToken)
                .setNotification(Notification.builder()
                        .setTitle(payload.title())
                        .setBody(payload.body())
                        .build());
        if (payload.data() != null) {
            payload.data().forEach(builder::putData);
        }
        try {
            final String messageId = firebaseMessaging.send(builder.build());
            log.info("FCM sent (messageId={}, title={})", messageId, payload.title());
            return PushSendResult.ok();
        } catch (final FirebaseMessagingException exception) {
            final MessagingErrorCode errorCode = exception.getMessagingErrorCode();
            if (errorCode == MessagingErrorCode.UNREGISTERED || errorCode == MessagingErrorCode.INVALID_ARGUMENT) {
                log.warn("FCM token invalid (errorCode={}, token={})", errorCode, deviceToken);
                return PushSendResult.tokenInvalid(exception.getMessage());
            }
            log.error("FCM send failed (errorCode={}, token={})", errorCode, deviceToken, exception);
            return PushSendResult.failed(exception.getMessage());
        }
    }
}
