package com.ppiyaki.infrastructure.messaging.fcm;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FcmPushSender implements PushSender {

    private static final Logger log = LoggerFactory.getLogger(FcmPushSender.class);

    private static final String METRIC_SENT = "ppiyaki.push.sent.total";
    private static final String METRIC_TIMER = "ppiyaki.push.send.seconds";
    private static final String RESULT_SUCCESS = "success";
    private static final String RESULT_TOKEN_INVALID = "token_invalid";
    private static final String RESULT_FAILED = "failed";
    private static final String CATEGORY_UNKNOWN = "unknown";

    private final FirebaseMessaging firebaseMessaging;
    private final MeterRegistry meterRegistry;

    public FcmPushSender(final FirebaseMessaging firebaseMessaging, final MeterRegistry meterRegistry) {
        this.firebaseMessaging = firebaseMessaging;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public PushSendResult send(final String deviceToken, final PushPayload payload) {
        final String category = extractCategory(payload);
        final Timer.Sample sample = Timer.start(meterRegistry);
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
            record(category, RESULT_SUCCESS, sample);
            return PushSendResult.ok();
        } catch (final FirebaseMessagingException exception) {
            final MessagingErrorCode errorCode = exception.getMessagingErrorCode();
            if (errorCode == MessagingErrorCode.UNREGISTERED || errorCode == MessagingErrorCode.INVALID_ARGUMENT) {
                log.warn("FCM token invalid (errorCode={}, token={})", errorCode, deviceToken);
                record(category, RESULT_TOKEN_INVALID, sample);
                return PushSendResult.tokenInvalid(exception.getMessage());
            }
            log.error("FCM send failed (errorCode={}, token={})", errorCode, deviceToken, exception);
            record(category, RESULT_FAILED, sample);
            return PushSendResult.failed(exception.getMessage());
        }
    }

    private void record(final String category, final String result, final Timer.Sample sample) {
        meterRegistry.counter(METRIC_SENT, "category", category, "result", result).increment();
        sample.stop(meterRegistry.timer(METRIC_TIMER, "category", category, "result", result));
    }

    private String extractCategory(final PushPayload payload) {
        if (payload.data() == null) {
            return CATEGORY_UNKNOWN;
        }
        return payload.data().getOrDefault("category", CATEGORY_UNKNOWN);
    }
}
