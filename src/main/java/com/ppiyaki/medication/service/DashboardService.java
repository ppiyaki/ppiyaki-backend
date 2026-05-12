package com.ppiyaki.medication.service;

import com.ppiyaki.common.exception.BusinessException;
import com.ppiyaki.common.exception.ErrorCode;
import com.ppiyaki.common.storage.PhotoUrlAssembler;
import com.ppiyaki.medication.DayStatus;
import com.ppiyaki.medication.LogStatus;
import com.ppiyaki.medication.MealSlot;
import com.ppiyaki.medication.MedicationLog;
import com.ppiyaki.medication.MedicationSchedule;
import com.ppiyaki.medication.SlotStatus;
import com.ppiyaki.medication.controller.dto.dashboard.DailyDashboardResponse;
import com.ppiyaki.medication.controller.dto.dashboard.DailyDashboardResponse.HeaderInfo;
import com.ppiyaki.medication.controller.dto.dashboard.DailyDashboardResponse.MedicineSummary;
import com.ppiyaki.medication.controller.dto.dashboard.DailyDashboardResponse.SlotInfo;
import com.ppiyaki.medication.controller.dto.dashboard.DailyDashboardResponse.SlotMedicine;
import com.ppiyaki.medication.controller.dto.dashboard.MonthlyDashboardResponse;
import com.ppiyaki.medication.controller.dto.dashboard.WeeklyDashboardResponse;
import com.ppiyaki.medication.controller.dto.dashboard.WeeklyDashboardResponse.DayEntry;
import com.ppiyaki.medication.controller.dto.dashboard.WeeklyDashboardResponse.SlotMarker;
import com.ppiyaki.medication.repository.MedicationLogRepository;
import com.ppiyaki.medication.repository.MedicationScheduleRepository;
import com.ppiyaki.medicine.Medicine;
import com.ppiyaki.medicine.repository.MedicineRepository;
import com.ppiyaki.notification.NotificationSettings;
import com.ppiyaki.notification.repository.NotificationSettingsRepository;
import com.ppiyaki.user.User;
import com.ppiyaki.user.repository.CareRelationRepository;
import com.ppiyaki.user.repository.UserRepository;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 보호자 대시보드 조회 서비스.
 * spec docs/features/caregiver-dashboard.md.
 *
 * <p>{@link com.ppiyaki.common.storage.PhotoUrlAssembler} 의존 — 기존 MedicationLogService 패턴과 동일하게
 * ncp.storage.bucket-name 설정 시에만 빈 등록 (default 컨텍스트 contextLoads 보호).
 */
@Service
@ConditionalOnProperty(prefix = "ncp.storage", name = "bucket-name")
public class DashboardService {

    private static final Logger log = LoggerFactory.getLogger(DashboardService.class);

    private static final long DEFAULT_DELAY_THRESHOLD_MINUTES = 60;

    private final UserRepository userRepository;
    private final CareRelationRepository careRelationRepository;
    private final MedicationScheduleRepository scheduleRepository;
    private final MedicationLogRepository logRepository;
    private final MedicineRepository medicineRepository;
    private final NotificationSettingsRepository notificationSettingsRepository;
    private final PhotoUrlAssembler photoUrlAssembler;

    public DashboardService(
            final UserRepository userRepository,
            final CareRelationRepository careRelationRepository,
            final MedicationScheduleRepository scheduleRepository,
            final MedicationLogRepository logRepository,
            final MedicineRepository medicineRepository,
            final NotificationSettingsRepository notificationSettingsRepository,
            final PhotoUrlAssembler photoUrlAssembler
    ) {
        this.userRepository = userRepository;
        this.careRelationRepository = careRelationRepository;
        this.scheduleRepository = scheduleRepository;
        this.logRepository = logRepository;
        this.medicineRepository = medicineRepository;
        this.notificationSettingsRepository = notificationSettingsRepository;
        this.photoUrlAssembler = photoUrlAssembler;
    }

    private long resolveDelayThresholdMinutes(final Long callerId, final Long seniorId) {
        if (callerId.equals(seniorId)) {
            return DEFAULT_DELAY_THRESHOLD_MINUTES;
        }
        return notificationSettingsRepository.findByCaregiverIdAndSeniorId(callerId, seniorId)
                .map(NotificationSettings::getMedicationDelayThresholdMinutes)
                .map(Integer::longValue)
                .orElse(DEFAULT_DELAY_THRESHOLD_MINUTES);
    }

    @Transactional(readOnly = true)
    public DailyDashboardResponse getDaily(final Long userId, final Long seniorId, final LocalDate date) {
        log.info("/dashboard/daily seniorId={} date={}", seniorId, date);
        validateAccess(userId, seniorId);

        final User senior = userRepository.findById(seniorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        final User caregiver = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        final List<MedicationSchedule> schedules = scheduleRepository.findActiveByOwnerAndDate(seniorId, date);
        final List<MedicationLog> logs = logRepository.findBySeniorIdAndTargetDate(seniorId, date);
        final List<Medicine> medicines = medicineRepository.findByOwnerId(seniorId);

        final Map<Long, Medicine> medicineById = new HashMap<>();
        for (final Medicine m : medicines) {
            medicineById.put(m.getId(), m);
        }

        final Map<Long, MedicationLog> logByScheduleId = new HashMap<>();
        for (final MedicationLog l : logs) {
            logByScheduleId.put(l.getScheduleId(), l);
        }

        final Map<MealSlot, List<MedicationSchedule>> schedulesBySlot = new HashMap<>();
        for (final MedicationSchedule s : schedules) {
            schedulesBySlot.computeIfAbsent(s.getMealSlot(), k -> new ArrayList<>()).add(s);
        }

        final LocalDate today = LocalDate.now();
        final long delayThresholdMinutes = resolveDelayThresholdMinutes(userId, seniorId);
        final List<SlotInfo> slots = new ArrayList<>();
        for (final MealSlot slot : MealSlot.values()) {
            slots.add(buildSlotInfo(slot, senior, schedulesBySlot.get(slot), logByScheduleId, medicineById, date,
                    today, delayThresholdMinutes));
        }

        final DayStatus dayStatus = deriveDayStatus(slots, date, today);
        final List<MedicineSummary> medicineSummaries = buildMedicineSummaries(schedules, medicineById);
        final Integer remainingDays = computeRemainingDays(medicines, today);

        final HeaderInfo header = new HeaderInfo(senior.getNickname(), caregiver.getNickname(), remainingDays);
        return new DailyDashboardResponse(seniorId, date, dayStatus, header, slots, medicineSummaries);
    }

    @Transactional(readOnly = true)
    public WeeklyDashboardResponse getWeekly(final Long userId, final Long seniorId, final LocalDate weekStart) {
        log.info("/dashboard/weekly seniorId={} weekStart={}", seniorId, weekStart);
        validateAccess(userId, seniorId);

        final User senior = userRepository.findById(seniorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        final LocalDate weekEnd = weekStart.plusDays(6);
        final List<MedicationSchedule> schedules = scheduleRepository.findActiveByOwnerAndDateRange(
                seniorId, weekStart, weekEnd);
        final List<MedicationLog> logs = logRepository.findBySeniorIdAndTargetDateBetweenOrderByTargetDateAscIdAsc(
                seniorId, weekStart, weekEnd);

        final Map<LocalDate, Map<Long, MedicationLog>> logsByDateAndSchedule = new HashMap<>();
        for (final MedicationLog l : logs) {
            logsByDateAndSchedule
                    .computeIfAbsent(l.getTargetDate(), k -> new HashMap<>())
                    .put(l.getScheduleId(), l);
        }

        final LocalDate today = LocalDate.now();
        final long delayThresholdMinutes = resolveDelayThresholdMinutes(userId, seniorId);
        final List<DayEntry> dayEntries = new ArrayList<>();
        int adherenceNumerator = 0;
        int adherenceDenominator = 0;
        for (int i = 0; i < 7; i++) {
            final LocalDate date = weekStart.plusDays(i);
            final List<SlotMarker> slotMarkers = new ArrayList<>();
            final Map<Long, MedicationLog> logBySchedule = logsByDateAndSchedule.getOrDefault(date, Map.of());
            for (final MealSlot slot : MealSlot.values()) {
                final SlotStatus status = deriveSlotStatusForDate(
                        slot, senior, schedules, logBySchedule, date, today, delayThresholdMinutes);
                slotMarkers.add(new SlotMarker(slot, status));
                if (date.isAfter(today) || status == SlotStatus.NOT_SCHEDULED) {
                    continue;
                }
                adherenceDenominator++;
                if (status == SlotStatus.PERFECT || status == SlotStatus.DELAYED) {
                    adherenceNumerator++;
                }
            }
            final DayStatus dayStatus = deriveDayStatusFromMarkers(slotMarkers, date, today);
            dayEntries.add(new DayEntry(date, dayStatus, slotMarkers));
        }

        final Double adherenceRate = adherenceDenominator == 0
                ? null
                : Math.round(((double) adherenceNumerator / adherenceDenominator) * 10000.0) / 100.0;

        return new WeeklyDashboardResponse(seniorId, weekStart, weekEnd, adherenceRate, dayEntries);
    }

    @Transactional(readOnly = true)
    public MonthlyDashboardResponse getMonthly(final Long userId, final Long seniorId, final YearMonth yearMonth) {
        log.info("/dashboard/monthly seniorId={} yearMonth={}", seniorId, yearMonth);
        validateAccess(userId, seniorId);

        final User senior = userRepository.findById(seniorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        final LocalDate monthStart = yearMonth.atDay(1);
        final LocalDate monthEnd = yearMonth.atEndOfMonth();
        final List<MedicationSchedule> schedules = scheduleRepository.findActiveByOwnerAndDateRange(
                seniorId, monthStart, monthEnd);
        final List<MedicationLog> logs = logRepository.findBySeniorIdAndTargetDateBetweenOrderByTargetDateAscIdAsc(
                seniorId, monthStart, monthEnd);

        final Map<LocalDate, Map<Long, MedicationLog>> logsByDateAndSchedule = new HashMap<>();
        for (final MedicationLog l : logs) {
            logsByDateAndSchedule
                    .computeIfAbsent(l.getTargetDate(), k -> new HashMap<>())
                    .put(l.getScheduleId(), l);
        }

        final LocalDate today = LocalDate.now();
        final long delayThresholdMinutes = resolveDelayThresholdMinutes(userId, seniorId);
        final List<MonthlyDashboardResponse.DayEntry> dayEntries = new ArrayList<>();
        for (int day = 1; day <= yearMonth.lengthOfMonth(); day++) {
            final LocalDate date = yearMonth.atDay(day);
            final List<SlotMarker> markers = new ArrayList<>();
            final Map<Long, MedicationLog> logBySchedule = logsByDateAndSchedule.getOrDefault(date, Map.of());
            for (final MealSlot slot : MealSlot.values()) {
                final SlotStatus status = deriveSlotStatusForDate(
                        slot, senior, schedules, logBySchedule, date, today, delayThresholdMinutes);
                markers.add(new SlotMarker(slot, status));
            }
            final DayStatus dayStatus = deriveDayStatusFromMarkers(markers, date, today);
            dayEntries.add(new MonthlyDashboardResponse.DayEntry(date, dayStatus));
        }

        return new MonthlyDashboardResponse(seniorId, yearMonth, dayEntries);
    }

    private SlotStatus deriveSlotStatusForDate(
            final MealSlot slot,
            final User senior,
            final List<MedicationSchedule> allSchedules,
            final Map<Long, MedicationLog> logBySchedule,
            final LocalDate date,
            final LocalDate today,
            final long delayThresholdMinutes
    ) {
        final LocalTime mealTime = slot.resolveTime(senior);
        final List<MedicationSchedule> slotSchedules = allSchedules.stream()
                .filter(s -> s.getMealSlot() == slot)
                .filter(s -> (s.getStartDate() == null || !s.getStartDate().isAfter(date))
                        && (s.getEndDate() == null || !s.getEndDate().isBefore(date)))
                .toList();
        if (slotSchedules.isEmpty() || mealTime == null) {
            return SlotStatus.NOT_SCHEDULED;
        }
        MedicationLog representativeLog = null;
        for (final MedicationSchedule s : slotSchedules) {
            final MedicationLog l = logBySchedule.get(s.getId());
            if (l != null) {
                representativeLog = l;
                break;
            }
        }
        return deriveSlotStatus(representativeLog, mealTime, date, today, delayThresholdMinutes);
    }

    private DayStatus deriveDayStatusFromMarkers(
            final List<SlotMarker> markers, final LocalDate date, final LocalDate today
    ) {
        if (date.isAfter(today)) {
            return DayStatus.FUTURE;
        }
        final EnumSet<SlotStatus> present = EnumSet.noneOf(SlotStatus.class);
        for (final SlotMarker m : markers) {
            if (m.status() != SlotStatus.NOT_SCHEDULED) {
                present.add(m.status());
            }
        }
        if (present.isEmpty()) {
            return DayStatus.PERFECT;
        }
        if (present.contains(SlotStatus.MISSED)) {
            return DayStatus.MISSED;
        }
        if (present.contains(SlotStatus.DELAYED)) {
            return DayStatus.DELAYED;
        }
        if (present.contains(SlotStatus.PENDING)) {
            return DayStatus.PENDING;
        }
        return DayStatus.PERFECT;
    }

    private SlotInfo buildSlotInfo(
            final MealSlot slot,
            final User senior,
            final List<MedicationSchedule> slotSchedules,
            final Map<Long, MedicationLog> logByScheduleId,
            final Map<Long, Medicine> medicineById,
            final LocalDate date,
            final LocalDate today,
            final long delayThresholdMinutes
    ) {
        final LocalTime mealTime = slot.resolveTime(senior);
        if (slotSchedules == null || slotSchedules.isEmpty() || mealTime == null) {
            return new SlotInfo(slot, SlotStatus.NOT_SCHEDULED, mealTime, null, null, List.of());
        }

        // 슬롯 안의 schedule 중 하나라도 log가 있는지 — 같은 슬롯의 log는 동일 시점에 묶여 처리됨
        MedicationLog representativeLog = null;
        for (final MedicationSchedule s : slotSchedules) {
            final MedicationLog l = logByScheduleId.get(s.getId());
            if (l != null) {
                representativeLog = l;
                break;
            }
        }

        final SlotStatus status = deriveSlotStatus(representativeLog, mealTime, date, today, delayThresholdMinutes);
        final LocalDateTime takenAt = representativeLog != null ? representativeLog.getTakenAt() : null;
        final String photoUrl = representativeLog != null
                ? photoUrlAssembler.toFullUrl(representativeLog.getPhotoObjectKey()) : null;

        final List<SlotMedicine> slotMedicines = new ArrayList<>();
        for (final MedicationSchedule s : slotSchedules) {
            final Medicine m = medicineById.get(s.getMedicineId());
            if (m != null) {
                slotMedicines.add(new SlotMedicine(m.getId(), m.getName(), s.getDosage()));
            }
        }
        return new SlotInfo(slot, status, mealTime, takenAt, photoUrl, slotMedicines);
    }

    private SlotStatus deriveSlotStatus(
            final MedicationLog logRow,
            final LocalTime mealTime,
            final LocalDate date,
            final LocalDate today,
            final long delayThresholdMinutes
    ) {
        final boolean pastDate = date.isBefore(today);
        if (logRow == null || logRow.getStatus() != LogStatus.TAKEN) {
            return pastDate ? SlotStatus.MISSED : SlotStatus.PENDING;
        }
        final LocalDateTime mealDateTime = LocalDateTime.of(date, mealTime);
        final long minutesLate = Duration.between(mealDateTime, logRow.getTakenAt()).toMinutes();
        if (minutesLate <= delayThresholdMinutes) {
            return SlotStatus.PERFECT;
        }
        return SlotStatus.DELAYED;
    }

    private DayStatus deriveDayStatus(final List<SlotInfo> slots, final LocalDate date, final LocalDate today) {
        if (date.isAfter(today)) {
            return DayStatus.FUTURE;
        }
        final EnumSet<SlotStatus> present = EnumSet.noneOf(SlotStatus.class);
        for (final SlotInfo s : slots) {
            if (s.status() != SlotStatus.NOT_SCHEDULED) {
                present.add(s.status());
            }
        }
        if (present.isEmpty()) {
            return DayStatus.PERFECT;
        }
        if (present.contains(SlotStatus.MISSED)) {
            return DayStatus.MISSED;
        }
        if (present.contains(SlotStatus.DELAYED)) {
            return DayStatus.DELAYED;
        }
        if (present.contains(SlotStatus.PENDING)) {
            return DayStatus.PENDING;
        }
        return DayStatus.PERFECT;
    }

    private List<MedicineSummary> buildMedicineSummaries(
            final List<MedicationSchedule> schedules,
            final Map<Long, Medicine> medicineById
    ) {
        final Map<Long, EnumSet<MealSlot>> slotsByMedicine = new HashMap<>();
        for (final MedicationSchedule s : schedules) {
            slotsByMedicine
                    .computeIfAbsent(s.getMedicineId(), k -> EnumSet.noneOf(MealSlot.class))
                    .add(s.getMealSlot());
        }
        final List<MedicineSummary> summaries = new ArrayList<>();
        for (final Map.Entry<Long, EnumSet<MealSlot>> e : slotsByMedicine.entrySet()) {
            final Medicine m = medicineById.get(e.getKey());
            if (m == null) {
                continue;
            }
            final List<MealSlot> orderedSlots = new ArrayList<>(e.getValue());
            orderedSlots.sort(Comparator.comparingInt(Enum::ordinal));
            summaries.add(new MedicineSummary(m.getId(), m.getName(), orderedSlots));
        }
        summaries.sort(Comparator.comparingLong(MedicineSummary::medicineId));
        return summaries;
    }

    private Integer computeRemainingDays(final List<Medicine> medicines, final LocalDate today) {
        Integer minDays = null;
        for (final Medicine m : medicines) {
            if (m.getRemainingAmount() == null) {
                continue;
            }
            final List<MedicationSchedule> activeSchedules = scheduleRepository.findByMedicineId(m.getId()).stream()
                    .filter(s -> (s.getStartDate() == null || !s.getStartDate().isAfter(today))
                            && (s.getEndDate() == null || !s.getEndDate().isBefore(today)))
                    .toList();
            int dailyConsumption = 0;
            for (final MedicationSchedule s : activeSchedules) {
                final java.math.BigDecimal q = s.getDosageQuantity();
                if (q != null) {
                    dailyConsumption += q.setScale(0, java.math.RoundingMode.CEILING).intValueExact();
                }
            }
            if (dailyConsumption == 0) {
                continue;
            }
            final int days = m.getRemainingAmount() / dailyConsumption;
            if (minDays == null || days < minDays) {
                minDays = days;
            }
        }
        return minDays;
    }

    private void validateAccess(final Long userId, final Long seniorId) {
        if (userId.equals(seniorId)) {
            return;
        }
        careRelationRepository.findByCaregiverIdAndSeniorIdAndDeletedAtIsNull(userId, seniorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CARE_RELATION_NOT_FOUND));
    }
}
