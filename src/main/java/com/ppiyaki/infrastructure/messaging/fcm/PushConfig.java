package com.ppiyaki.infrastructure.messaging.fcm;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(FcmProperties.class)
public class PushConfig {

    private static final Logger log = LoggerFactory.getLogger(PushConfig.class);

    @Bean
    public PushSender pushSender(final FcmProperties properties, final MeterRegistry meterRegistry) {
        if (!properties.isEnabled()) {
            log.warn("FCM disabled — fcm.project-id or fcm.credentials-json-base64 missing. Push will be no-op.");
            return new NoOpPushSender();
        }
        try {
            final byte[] credentialsJson = Base64.getDecoder().decode(
                    properties.credentialsJsonBase64().getBytes(StandardCharsets.UTF_8));
            final GoogleCredentials credentials = GoogleCredentials.fromStream(
                    new ByteArrayInputStream(credentialsJson));
            final FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(credentials)
                    .setProjectId(properties.projectId())
                    .build();
            final FirebaseApp app = FirebaseApp.getApps().isEmpty()
                    ? FirebaseApp.initializeApp(options)
                    : FirebaseApp.getInstance();
            return new FcmPushSender(FirebaseMessaging.getInstance(app), meterRegistry);
        } catch (final Exception exception) {
            log.error("FCM initialization failed — fallback to no-op push sender", exception);
            return new NoOpPushSender();
        }
    }
}
