package com.ppiyaki.notification.service;

import com.ppiyaki.infrastructure.messaging.fcm.PushPayload;
import com.ppiyaki.infrastructure.messaging.fcm.PushSendResult;
import com.ppiyaki.infrastructure.messaging.fcm.PushSender;
import com.ppiyaki.medication.MealSlot;
import com.ppiyaki.medication.repository.MedicationScheduleRepository;
import com.ppiyaki.notification.DeviceToken;
import com.ppiyaki.notification.Notification;
import com.ppiyaki.notification.NotificationCategory;
import com.ppiyaki.notification.repository.DeviceTokenRepository;
import com.ppiyaki.notification.repository.NotificationRepository;
import com.ppiyaki.user.User;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class MedicationReminderDispatcher {

    private static final Logger log = LoggerFactory.getLogger(MedicationReminderDispatcher.class);
    private static final Map<MealSlot, String> SLOT_TITLES = Map.of(
            MealSlot.BREAKFAST, "아침약 드실 시간이에요!",
            MealSlot.LUNCH, "점심약 드실 시간이에요!",
            MealSlot.DINNER, "저녁약 드실 시간이에요!"
    );

    private final MedicationScheduleRepository medicationScheduleRepository;
    private final NotificationRepository notificationRepository;
    private final DeviceTokenRepository deviceTokenRepository;
    private final PushSender pushSender;

    public MedicationReminderDispatcher(
            final MedicationScheduleRepository medicationScheduleRepository,
            final NotificationRepository notificationRepository,
            final DeviceTokenRepository deviceTokenRepository,
            final PushSender pushSender
    ) {
        this.medicationScheduleRepository = medicationScheduleRepository;
        this.notificationRepository = notificationRepository;
        this.deviceTokenRepository = deviceTokenRepository;
        this.pushSender = pushSender;
    }

    @Transactional
    public boolean dispatchIfDue(final User senior, final MealSlot slot, final LocalDate today) {
        if (notificationRepository.existsByUserIdAndCategoryAndTargetDateAndMealSlot(
                senior.getId(), NotificationCategory.MEDICATION_REMINDER, today, slot.name())) {
            return false;
        }
        if (medicationScheduleRepository.findActiveByOwnerAndMealSlot(senior.getId(), today, slot).isEmpty()) {
            return false;
        }

        final String title = SLOT_TITLES.getOrDefault(slot, "약 드실 시간이에요");
        final String body = "삐~약드실 시간이에요~";
        final Notification notification = notificationRepository.save(
                Notification.createForMedicationReminder(senior.getId(), title, body, today, slot.name()));
        log.info("MEDICATION_REMINDER notification created (id={}, senior={}, slot={})",
                notification.getId(), senior.getId(), slot);

        final List<DeviceToken> tokens = deviceTokenRepository.findByUserIdAndIsActiveTrue(senior.getId());
        for (final DeviceToken token : tokens) {
            final PushSendResult result = pushSender.send(token.getToken(),
                    new PushPayload(title, body, Map.of("category", "MEDICATION_REMINDER", "mealSlot", slot.name())));
            if (result.tokenInvalid()) {
                token.deactivate();
            }
        }
        return true;
    }
}
