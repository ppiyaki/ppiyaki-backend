package com.ppiyaki.notification.service;

import com.ppiyaki.medication.MealSlot;
import com.ppiyaki.user.User;
import com.ppiyaki.user.UserRole;
import com.ppiyaki.user.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MedicationReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(MedicationReminderScheduler.class);

    private final UserRepository userRepository;
    private final MedicationReminderDispatcher dispatcher;
    private final Clock clock;

    public MedicationReminderScheduler(
            final UserRepository userRepository,
            final MedicationReminderDispatcher dispatcher,
            final Clock clock
    ) {
        this.userRepository = userRepository;
        this.dispatcher = dispatcher;
        this.clock = clock;
    }

    @Scheduled(cron = "0 * * * * *", zone = "Asia/Seoul")
    public void dispatchDue() {
        final LocalDate today = LocalDate.now(clock);
        final LocalTime now = LocalTime.now(clock).withSecond(0).withNano(0);
        log.info("MedicationReminderScheduler tick at {}", now);
        run(today, now);
    }

    public int run(final LocalDate today, final LocalTime currentMinute) {
        int dispatched = 0;
        final List<User> seniors = userRepository.findAllByRoleWithMealTimesSet(UserRole.SENIOR);
        for (final User senior : seniors) {
            for (final MealSlot slot : MealSlot.values()) {
                final LocalTime mealTime = slot.resolveTime(senior);
                if (mealTime == null) {
                    continue;
                }
                if (!mealTime.withSecond(0).withNano(0).equals(currentMinute)) {
                    continue;
                }
                if (dispatcher.dispatchIfDue(senior, slot, today)) {
                    dispatched++;
                }
            }
        }
        return dispatched;
    }
}
