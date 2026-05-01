package com.ppiyaki.common.mcp;

import com.ppiyaki.medication.MedicationSchedule;
import com.ppiyaki.medication.repository.MedicationScheduleRepository;
import com.ppiyaki.medicine.Medicine;
import com.ppiyaki.medicine.repository.MedicineRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class MedicationMcpTools {

    private final MedicineRepository medicineRepository;
    private final MedicationScheduleRepository scheduleRepository;

    public MedicationMcpTools(
            final MedicineRepository medicineRepository,
            final MedicationScheduleRepository scheduleRepository
    ) {
        this.medicineRepository = medicineRepository;
        this.scheduleRepository = scheduleRepository;
    }

    @Tool(description = "Get today's medication schedule for the user. Returns medicine names, scheduled times, and dosages for today.")
    public List<ScheduleSummary> getTodaySchedules() {
        final Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
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
                result.add(new ScheduleSummary(
                        medicine.getName(),
                        schedule.getScheduledTime() != null ? schedule.getScheduledTime().toString() : null,
                        schedule.getDosage()
                ));
            }
        }
        return result;
    }

    @Tool(description = "Get remaining amount of medicines for the user. Returns medicine names with remaining and total amounts.")
    public List<MedicineRemainingInfo> getMedicineRemaining(
            @ToolParam(description = "Optional medicine name filter. If null, returns all medicines.") final String medicineName
    ) {
        final Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
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
