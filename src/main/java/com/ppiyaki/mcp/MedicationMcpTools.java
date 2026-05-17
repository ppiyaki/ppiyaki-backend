package com.ppiyaki.mcp;

import com.ppiyaki.common.exception.BusinessException;
import com.ppiyaki.common.exception.ErrorCode;
import com.ppiyaki.medication.domain.MedicationSchedule;
import com.ppiyaki.medication.repository.MedicationScheduleRepository;
import com.ppiyaki.medicine.Medicine;
import com.ppiyaki.medicine.repository.MedicineRepository;
import com.ppiyaki.user.domain.User;
import com.ppiyaki.user.repository.UserRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class MedicationMcpTools {

    private final MedicineRepository medicineRepository;
    private final MedicationScheduleRepository scheduleRepository;
    private final UserRepository userRepository;

    public MedicationMcpTools(
            final MedicineRepository medicineRepository,
            final MedicationScheduleRepository scheduleRepository,
            final UserRepository userRepository
    ) {
        this.medicineRepository = medicineRepository;
        this.scheduleRepository = scheduleRepository;
        this.userRepository = userRepository;
    }

    @Tool(description = "Get today's medication schedule for the user. Returns medicine names, scheduled times (resolved from the senior's meal times), and dosages for today.")
    public List<ScheduleSummary> getTodaySchedules(final ToolContext toolContext) {
        final Long userId = resolveUserId(toolContext);
        final User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return List.of();
        }
        final List<Medicine> medicines = medicineRepository.findByOwnerId(userId);
        final LocalDate today = LocalDate.now();
        final String todayDay = dayOfWeekToKorean(today.getDayOfWeek());

        final List<ScheduleSummary> result = new ArrayList<>();
        for (final Medicine medicine : medicines) {
            final List<MedicationSchedule> schedules = scheduleRepository.findByMedicineId(medicine.getId());
            for (final MedicationSchedule schedule : schedules) {
                if (!isActiveToday(schedule, today, todayDay)) {
                    continue;
                }
                final LocalTime resolved = schedule.getMealSlot().resolveTime(user);
                result.add(new ScheduleSummary(
                        medicine.getName(),
                        resolved != null ? resolved.toString() : null,
                        schedule.composeDosageText()
                ));
            }
        }
        return result;
    }

    @Tool(description = "Get remaining amount of medicines for the user. Returns medicine names with remaining and total amounts.")
    public List<MedicineRemainingInfo> getMedicineRemaining(
            @ToolParam(description = "Optional medicine name filter. If null, returns all medicines.") final String medicineName,
            final ToolContext toolContext
    ) {
        final Long userId = resolveUserId(toolContext);
        final List<Medicine> medicines = medicineRepository.findByOwnerId(userId);

        return medicines.stream()
                .filter(m -> medicineName == null || medicineName.isBlank()
                        || m.getName().contains(medicineName))
                .map(m -> new MedicineRemainingInfo(
                        m.getName(),
                        m.getRemainingAmount(),
                        m.getTotalAmount()))
                .toList();
    }

    /**
     * Spring AI 도구 호출은 Reactor BoundedElastic 풀에서 실행되어 SecurityContextHolder의
     * ThreadLocal 인증이 비어있다 (`spring.reactor.context-propagation: auto`로도 복원되지 않음).
     * ChatSessionService가 prompt 빌드 시 .toolContext(Map.of("userId", userId))로 명시 전달하는 값을 사용한다.
     */
    private static Long resolveUserId(final ToolContext toolContext) {
        if (toolContext == null) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN, "ToolContext is missing userId");
        }
        final Object value = toolContext.getContext().get("userId");
        if (!(value instanceof Long userId)) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN, "ToolContext userId is missing or not a Long");
        }
        return userId;
    }

    private boolean isActiveToday(
            final MedicationSchedule schedule,
            final LocalDate today,
            final String todayDay
    ) {
        if (schedule.getStartDate() != null && today.isBefore(schedule.getStartDate())) {
            return false;
        }
        if (schedule.getEndDate() != null && today.isAfter(schedule.getEndDate())) {
            return false;
        }
        final String days = schedule.getDaysOfWeek();
        return days == null || "DAILY".equalsIgnoreCase(days) || days.contains(todayDay);
    }

    private String dayOfWeekToKorean(final DayOfWeek dow) {
        return switch (dow) {
            case MONDAY -> "월";
            case TUESDAY -> "화";
            case WEDNESDAY -> "수";
            case THURSDAY -> "목";
            case FRIDAY -> "금";
            case SATURDAY -> "토";
            case SUNDAY -> "일";
        };
    }

    public record ScheduleSummary(
            String medicineName,
            String scheduledTime,
            String dosage
    ) {
    }

    public record MedicineRemainingInfo(
            String medicineName,
            Integer remainingAmount,
            Integer totalAmount
    ) {
    }
}
