package com.ppiyaki.medication.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.ppiyaki.common.exception.BusinessException;
import com.ppiyaki.infrastructure.storage.PhotoUrlAssembler;
import com.ppiyaki.medication.controller.dto.dashboard.DailyDashboardResponse;
import com.ppiyaki.medication.domain.DayStatus;
import com.ppiyaki.medication.domain.LogStatus;
import com.ppiyaki.medication.domain.MealSlot;
import com.ppiyaki.medication.domain.MedicationLog;
import com.ppiyaki.medication.domain.MedicationSchedule;
import com.ppiyaki.medication.domain.SlotStatus;
import com.ppiyaki.medication.repository.MedicationLogRepository;
import com.ppiyaki.medication.repository.MedicationScheduleRepository;
import com.ppiyaki.medicine.Medicine;
import com.ppiyaki.medicine.repository.MedicineRepository;
import com.ppiyaki.user.domain.User;
import com.ppiyaki.user.repository.CareRelationRepository;
import com.ppiyaki.user.repository.UserRepository;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("DashboardService.getDaily")
class DashboardServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private CareRelationRepository careRelationRepository;
    @Mock
    private MedicationScheduleRepository scheduleRepository;
    @Mock
    private MedicationLogRepository logRepository;
    @Mock
    private MedicineRepository medicineRepository;
    @Mock
    private PhotoUrlAssembler photoUrlAssembler;

    @InjectMocks
    private DashboardService dashboardService;

    private static final Long SENIOR_ID = 16L;
    private static final Long CAREGIVER_ID = 17L;
    private static final LocalDate TODAY = LocalDate.now();
    private static final LocalDate YESTERDAY = TODAY.minusDays(1);

    @Test
    @DisplayName("시니어 본인은 권한 검증 통과")
    void seniorSelf_passesAccess() throws Exception {
        givenSeniorAndCaregiver();
        when(scheduleRepository.findActiveByOwnerAndDate(eq(SENIOR_ID), any())).thenReturn(List.of());
        when(logRepository.findBySeniorIdAndTargetDate(eq(SENIOR_ID), any())).thenReturn(List.of());
        when(medicineRepository.findByOwnerId(SENIOR_ID)).thenReturn(List.of());

        final DailyDashboardResponse resp = dashboardService.getDaily(SENIOR_ID, SENIOR_ID, TODAY);

        assertThat(resp.seniorId()).isEqualTo(SENIOR_ID);
        assertThat(resp.dayStatus()).isEqualTo(DayStatus.NOT_SCHEDULED);
    }

    @Test
    @DisplayName("관계 없는 사용자는 CARE_RELATION_NOT_FOUND")
    void unrelatedUser_throws() {
        when(careRelationRepository.findByCaregiverIdAndSeniorIdAndDeletedAtIsNull(999L, SENIOR_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> dashboardService.getDaily(999L, SENIOR_ID, TODAY))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("오늘 BREAKFAST 정시 인증 → SlotStatus.PERFECT, DayStatus.PERFECT")
    void today_perfect() throws Exception {
        givenSeniorAndCaregiver();
        final LocalTime breakfastTime = LocalTime.of(8, 0);
        givenSeniorMealTimes(breakfastTime, null, null);
        final MedicationSchedule schedule = scheduleOf(1L, 100L, MealSlot.BREAKFAST, "1정");
        when(scheduleRepository.findActiveByOwnerAndDate(eq(SENIOR_ID), any())).thenReturn(List.of(schedule));
        when(scheduleRepository.findByMedicineId(100L)).thenReturn(List.of(schedule));
        final MedicationLog logRow = logOf(schedule.getId(),
                LocalDateTime.of(TODAY, breakfastTime).plusMinutes(10), LogStatus.TAKEN);
        when(logRepository.findBySeniorIdAndTargetDate(eq(SENIOR_ID), any())).thenReturn(List.of(logRow));
        when(medicineRepository.findByOwnerId(SENIOR_ID)).thenReturn(List.of(medicineOf(100L, "타이레놀", 30)));
        when(photoUrlAssembler.toFullUrl(any())).thenReturn(null);

        final DailyDashboardResponse resp = dashboardService.getDaily(SENIOR_ID, SENIOR_ID, TODAY);

        assertThat(resp.dayStatus()).isEqualTo(DayStatus.PERFECT);
        assertThat(resp.slots()).extracting(DailyDashboardResponse.SlotInfo::slot,
                DailyDashboardResponse.SlotInfo::status)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(MealSlot.BREAKFAST, SlotStatus.PERFECT),
                        org.assertj.core.groups.Tuple.tuple(MealSlot.LUNCH, SlotStatus.NOT_SCHEDULED),
                        org.assertj.core.groups.Tuple.tuple(MealSlot.DINNER, SlotStatus.NOT_SCHEDULED));
    }

    @Test
    @DisplayName("오늘 BREAKFAST 1시간 초과 지연 인증 → DELAYED")
    void today_delayed() throws Exception {
        givenSeniorAndCaregiver();
        final LocalTime breakfastTime = LocalTime.of(8, 0);
        givenSeniorMealTimes(breakfastTime, null, null);
        final MedicationSchedule schedule = scheduleOf(1L, 100L, MealSlot.BREAKFAST, "1정");
        when(scheduleRepository.findActiveByOwnerAndDate(eq(SENIOR_ID), any())).thenReturn(List.of(schedule));
        when(scheduleRepository.findByMedicineId(100L)).thenReturn(List.of(schedule));
        final MedicationLog logRow = logOf(schedule.getId(),
                LocalDateTime.of(TODAY, breakfastTime).plusMinutes(70), LogStatus.TAKEN);
        when(logRepository.findBySeniorIdAndTargetDate(eq(SENIOR_ID), any())).thenReturn(List.of(logRow));
        when(medicineRepository.findByOwnerId(SENIOR_ID)).thenReturn(List.of(medicineOf(100L, "타이레놀", 30)));
        when(photoUrlAssembler.toFullUrl(any())).thenReturn(null);

        final DailyDashboardResponse resp = dashboardService.getDaily(SENIOR_ID, SENIOR_ID, TODAY);

        assertThat(resp.dayStatus()).isEqualTo(DayStatus.DELAYED);
        assertThat(resp.slots().get(0).status()).isEqualTo(SlotStatus.DELAYED);
    }

    @Test
    @DisplayName("오늘 미인증 → PENDING (자정 미경과)")
    void today_pending() throws Exception {
        givenSeniorAndCaregiver();
        givenSeniorMealTimes(LocalTime.of(8, 0), null, null);
        final MedicationSchedule schedule = scheduleOf(1L, 100L, MealSlot.BREAKFAST, "1정");
        when(scheduleRepository.findActiveByOwnerAndDate(eq(SENIOR_ID), any())).thenReturn(List.of(schedule));
        when(scheduleRepository.findByMedicineId(100L)).thenReturn(List.of(schedule));
        when(logRepository.findBySeniorIdAndTargetDate(eq(SENIOR_ID), any())).thenReturn(List.of());
        when(medicineRepository.findByOwnerId(SENIOR_ID)).thenReturn(List.of(medicineOf(100L, "타이레놀", 30)));

        final DailyDashboardResponse resp = dashboardService.getDaily(SENIOR_ID, SENIOR_ID, TODAY);

        assertThat(resp.dayStatus()).isEqualTo(DayStatus.PENDING);
        assertThat(resp.slots().get(0).status()).isEqualTo(SlotStatus.PENDING);
    }

    @Test
    @DisplayName("어제 미인증 → MISSED (자정 경과)")
    void past_missed() throws Exception {
        givenSeniorAndCaregiver();
        givenSeniorMealTimes(LocalTime.of(8, 0), null, null);
        final MedicationSchedule schedule = scheduleOf(1L, 100L, MealSlot.BREAKFAST, "1정");
        when(scheduleRepository.findActiveByOwnerAndDate(eq(SENIOR_ID), any())).thenReturn(List.of(schedule));
        when(scheduleRepository.findByMedicineId(100L)).thenReturn(List.of(schedule));
        when(logRepository.findBySeniorIdAndTargetDate(eq(SENIOR_ID), any())).thenReturn(List.of());
        when(medicineRepository.findByOwnerId(SENIOR_ID)).thenReturn(List.of(medicineOf(100L, "타이레놀", 30)));

        final DailyDashboardResponse resp = dashboardService.getDaily(SENIOR_ID, SENIOR_ID, YESTERDAY);

        assertThat(resp.dayStatus()).isEqualTo(DayStatus.MISSED);
        assertThat(resp.slots().get(0).status()).isEqualTo(SlotStatus.MISSED);
    }

    @Test
    @DisplayName("미래 일자 → DayStatus.FUTURE")
    void future_date() throws Exception {
        givenSeniorAndCaregiver();
        givenSeniorMealTimes(LocalTime.of(8, 0), null, null);
        when(scheduleRepository.findActiveByOwnerAndDate(eq(SENIOR_ID), any())).thenReturn(List.of());
        when(logRepository.findBySeniorIdAndTargetDate(eq(SENIOR_ID), any())).thenReturn(List.of());
        when(medicineRepository.findByOwnerId(SENIOR_ID)).thenReturn(List.of());

        final DailyDashboardResponse resp = dashboardService.getDaily(SENIOR_ID, SENIOR_ID, TODAY.plusDays(3));

        assertThat(resp.dayStatus()).isEqualTo(DayStatus.FUTURE);
    }

    @Test
    @DisplayName("remainingDays = MIN(remainingAmount / dailyConsumption)")
    void remainingDays_min() throws Exception {
        givenSeniorAndCaregiver();
        givenSeniorMealTimes(LocalTime.of(8, 0), null, null);

        final MedicationSchedule schA = scheduleOf(1L, 100L, MealSlot.BREAKFAST, "1정");  // medA: 1정/일
        final MedicationSchedule schB = scheduleOf(2L, 200L, MealSlot.BREAKFAST, "2정");  // medB: 2정/일

        when(scheduleRepository.findActiveByOwnerAndDate(eq(SENIOR_ID), any())).thenReturn(List.of(schA, schB));
        when(scheduleRepository.findByMedicineId(100L)).thenReturn(List.of(schA));
        when(scheduleRepository.findByMedicineId(200L)).thenReturn(List.of(schB));
        when(logRepository.findBySeniorIdAndTargetDate(eq(SENIOR_ID), any())).thenReturn(List.of());
        when(medicineRepository.findByOwnerId(SENIOR_ID)).thenReturn(List.of(
                medicineOf(100L, "약A", 30),  // 30/1 = 30
                medicineOf(200L, "약B", 10)   // 10/2 = 5  ← MIN
        ));

        final DailyDashboardResponse resp = dashboardService.getDaily(SENIOR_ID, SENIOR_ID, TODAY);

        assertThat(resp.header().remainingDays()).isEqualTo(5);
    }

    @Test
    @DisplayName("dailyConsumption=0(dosage 비정수)인 medicine은 remainingDays 분모에서 제외")
    void remainingDays_skipsZeroConsumption() throws Exception {
        givenSeniorAndCaregiver();
        givenSeniorMealTimes(LocalTime.of(8, 0), null, null);

        final MedicationSchedule sch = scheduleOf(1L, 100L, MealSlot.BREAKFAST, "반정");  // 정수 X → 0
        when(scheduleRepository.findActiveByOwnerAndDate(eq(SENIOR_ID), any())).thenReturn(List.of(sch));
        when(scheduleRepository.findByMedicineId(100L)).thenReturn(List.of(sch));
        when(logRepository.findBySeniorIdAndTargetDate(eq(SENIOR_ID), any())).thenReturn(List.of());
        when(medicineRepository.findByOwnerId(SENIOR_ID)).thenReturn(List.of(medicineOf(100L, "약A", 10)));

        final DailyDashboardResponse resp = dashboardService.getDaily(SENIOR_ID, SENIOR_ID, TODAY);

        assertThat(resp.header().remainingDays()).isNull();
    }

    @Test
    @DisplayName("medicines 슬롯 매핑 — 같은 약이 BREAKFAST+DINNER면 slots=[BREAKFAST,DINNER]")
    void medicineSummary_aggregatesSlots() throws Exception {
        givenSeniorAndCaregiver();
        givenSeniorMealTimes(LocalTime.of(8, 0), null, LocalTime.of(18, 0));
        final MedicationSchedule schA = scheduleOf(1L, 100L, MealSlot.BREAKFAST, "1정");
        final MedicationSchedule schB = scheduleOf(2L, 100L, MealSlot.DINNER, "1정");
        when(scheduleRepository.findActiveByOwnerAndDate(eq(SENIOR_ID), any())).thenReturn(List.of(schA, schB));
        when(scheduleRepository.findByMedicineId(100L)).thenReturn(List.of(schA, schB));
        when(logRepository.findBySeniorIdAndTargetDate(eq(SENIOR_ID), any())).thenReturn(List.of());
        when(medicineRepository.findByOwnerId(SENIOR_ID)).thenReturn(List.of(medicineOf(100L, "약A", 30)));

        final DailyDashboardResponse resp = dashboardService.getDaily(SENIOR_ID, SENIOR_ID, TODAY);

        assertThat(resp.medicines()).hasSize(1);
        assertThat(resp.medicines().get(0).slots()).containsExactly(MealSlot.BREAKFAST, MealSlot.DINNER);
    }

    @Test
    @DisplayName("weekly — 모든 일자 schedule 없음 → adherenceRate=null, days[7] 모두 NOT_SCHEDULED")
    void weekly_emptySchedules() throws Exception {
        givenSeniorAndCaregiver();
        givenSeniorMealTimes(LocalTime.of(8, 0), LocalTime.of(12, 30), LocalTime.of(18, 30));
        when(scheduleRepository.findActiveByOwnerAndDateRange(eq(SENIOR_ID), any(), any())).thenReturn(List.of());
        when(logRepository.findBySeniorIdAndTargetDateBetweenOrderByTargetDateAscIdAsc(eq(SENIOR_ID), any(), any()))
                .thenReturn(List.of());

        final LocalDate weekStart = TODAY.minusDays(7);
        final var resp = dashboardService.getWeekly(SENIOR_ID, SENIOR_ID, weekStart);

        org.assertj.core.api.Assertions.assertThat(resp.weekStart()).isEqualTo(weekStart);
        org.assertj.core.api.Assertions.assertThat(resp.weekEnd()).isEqualTo(weekStart.plusDays(6));
        org.assertj.core.api.Assertions.assertThat(resp.adherenceRate()).isNull();
        org.assertj.core.api.Assertions.assertThat(resp.days()).hasSize(7);
        org.assertj.core.api.Assertions.assertThat(resp.days()).allMatch(d -> d.dayStatus()
                == com.ppiyaki.medication.domain.DayStatus.NOT_SCHEDULED
                && d.slots().stream().allMatch(s -> s.status() == SlotStatus.NOT_SCHEDULED));
    }

    @Test
    @DisplayName("weekly — 과거 6일 모두 PERFECT 인증, 오늘은 PENDING → adherenceRate=100")
    void weekly_pastPerfect_todayPending() throws Exception {
        givenSeniorAndCaregiver();
        final LocalTime breakfastTime = LocalTime.of(8, 0);
        givenSeniorMealTimes(breakfastTime, null, null);

        final LocalDate weekStart = TODAY.minusDays(6);
        final MedicationSchedule schedule = scheduleOf(1L, 100L, MealSlot.BREAKFAST, "1정");
        when(scheduleRepository.findActiveByOwnerAndDateRange(eq(SENIOR_ID), any(), any()))
                .thenReturn(List.of(schedule));

        final List<MedicationLog> pastLogs = new java.util.ArrayList<>();
        for (int i = 0; i < 6; i++) {
            final LocalDate date = weekStart.plusDays(i);
            pastLogs.add(logOnDate(schedule.getId(), date, breakfastTime, 10));
        }
        // 오늘은 미인증
        when(logRepository.findBySeniorIdAndTargetDateBetweenOrderByTargetDateAscIdAsc(eq(SENIOR_ID), any(), any()))
                .thenReturn(pastLogs);

        final var resp = dashboardService.getWeekly(SENIOR_ID, SENIOR_ID, weekStart);

        // 분모: 7일 모두 schedule된 BREAKFAST = 7. 분자: 과거 6 PERFECT = 6 (오늘 PENDING은 분자 제외).
        org.assertj.core.api.Assertions.assertThat(resp.adherenceRate())
                .isEqualTo(Math.round(6.0 / 7 * 10000) / 100.0);
        org.assertj.core.api.Assertions.assertThat(resp.days().get(6).dayStatus())
                .isEqualTo(com.ppiyaki.medication.domain.DayStatus.PENDING);
    }

    @Test
    @DisplayName("monthly — schedule 없으면 days 모두 NOT_SCHEDULED")
    void monthly_emptySchedules() throws Exception {
        givenSeniorAndCaregiver();
        givenSeniorMealTimes(LocalTime.of(8, 0), LocalTime.of(12, 30), LocalTime.of(18, 30));
        when(scheduleRepository.findActiveByOwnerAndDateRange(eq(SENIOR_ID), any(), any())).thenReturn(List.of());
        when(logRepository.findBySeniorIdAndTargetDateBetweenOrderByTargetDateAscIdAsc(eq(SENIOR_ID), any(), any()))
                .thenReturn(List.of());

        final java.time.YearMonth ym = java.time.YearMonth.of(2026, 1);
        final var resp = dashboardService.getMonthly(SENIOR_ID, SENIOR_ID, ym);

        org.assertj.core.api.Assertions.assertThat(resp.yearMonth()).isEqualTo(ym);
        org.assertj.core.api.Assertions.assertThat(resp.days()).hasSize(31);
        org.assertj.core.api.Assertions.assertThat(resp.days())
                .allMatch(d -> d.dayStatus() == com.ppiyaki.medication.domain.DayStatus.NOT_SCHEDULED);
    }

    @Test
    @DisplayName("weekly — 시니어 가입 이전 날짜는 dayStatus NOT_SCHEDULED + 모든 슬롯 NOT_SCHEDULED (issue #326)")
    void weekly_beforeRegistration_returnsNotScheduled() throws Exception {
        // given — 시니어 가입일이 weekStart+3 (즉 처음 3일은 가입 전)
        final User senior = userOf(SENIOR_ID, "김장군");
        final LocalDate weekStart = TODAY.minusDays(6);
        final LocalDateTime registeredAt = weekStart.plusDays(3).atStartOfDay();
        setField(senior, "createdAt", registeredAt);
        when(userRepository.findById(SENIOR_ID)).thenReturn(Optional.of(senior));

        when(scheduleRepository.findActiveByOwnerAndDateRange(eq(SENIOR_ID), any(), any())).thenReturn(List.of());
        when(logRepository.findBySeniorIdAndTargetDateBetweenOrderByTargetDateAscIdAsc(eq(SENIOR_ID), any(), any()))
                .thenReturn(List.of());

        // when
        final var resp = dashboardService.getWeekly(SENIOR_ID, SENIOR_ID, weekStart);

        // then — 처음 3일(weekStart, +1, +2)는 가입 전 shortcut으로 NOT_SCHEDULED, 나머지 4일은 schedule 없음으로 NOT_SCHEDULED
        org.assertj.core.api.Assertions.assertThat(resp.days()).hasSize(7);
        for (int i = 0; i < 3; i++) {
            org.assertj.core.api.Assertions.assertThat(resp.days().get(i).dayStatus())
                    .isEqualTo(com.ppiyaki.medication.domain.DayStatus.NOT_SCHEDULED);
            org.assertj.core.api.Assertions.assertThat(resp.days().get(i).slots())
                    .allMatch(s -> s.status() == SlotStatus.NOT_SCHEDULED);
        }
        for (int i = 3; i < 7; i++) {
            org.assertj.core.api.Assertions.assertThat(resp.days().get(i).dayStatus())
                    .isEqualTo(com.ppiyaki.medication.domain.DayStatus.NOT_SCHEDULED);
        }
    }

    @Test
    @DisplayName("monthly — 시니어 가입 이전 날짜는 dayStatus NOT_SCHEDULED (issue #326)")
    void monthly_beforeRegistration_returnsNotScheduled() throws Exception {
        // given — 가입일 = 1월 15일, 조회는 1월
        final User senior = userOf(SENIOR_ID, "김장군");
        setField(senior, "createdAt", LocalDateTime.of(2026, 1, 15, 0, 0));
        when(userRepository.findById(SENIOR_ID)).thenReturn(Optional.of(senior));

        when(scheduleRepository.findActiveByOwnerAndDateRange(eq(SENIOR_ID), any(), any())).thenReturn(List.of());
        when(logRepository.findBySeniorIdAndTargetDateBetweenOrderByTargetDateAscIdAsc(eq(SENIOR_ID), any(), any()))
                .thenReturn(List.of());

        final java.time.YearMonth ym = java.time.YearMonth.of(2026, 1);
        final var resp = dashboardService.getMonthly(SENIOR_ID, SENIOR_ID, ym);

        // then — 1~14일은 가입 전 shortcut, 15일부터 schedule 없음. 둘 다 NOT_SCHEDULED
        org.assertj.core.api.Assertions.assertThat(resp.days()).hasSize(31);
        for (int i = 0; i < 14; i++) {
            org.assertj.core.api.Assertions.assertThat(resp.days().get(i).dayStatus())
                    .isEqualTo(com.ppiyaki.medication.domain.DayStatus.NOT_SCHEDULED);
        }
        org.assertj.core.api.Assertions.assertThat(resp.days().get(14).dayStatus())
                .isEqualTo(com.ppiyaki.medication.domain.DayStatus.NOT_SCHEDULED);
    }

    @Test
    @DisplayName("monthly — 2월(28일)은 days.size=28")
    void monthly_february28() throws Exception {
        givenSeniorAndCaregiver();
        givenSeniorMealTimes(LocalTime.of(8, 0), null, null);
        when(scheduleRepository.findActiveByOwnerAndDateRange(eq(SENIOR_ID), any(), any())).thenReturn(List.of());
        when(logRepository.findBySeniorIdAndTargetDateBetweenOrderByTargetDateAscIdAsc(eq(SENIOR_ID), any(), any()))
                .thenReturn(List.of());

        final java.time.YearMonth ym = java.time.YearMonth.of(2025, 2);
        final var resp = dashboardService.getMonthly(SENIOR_ID, SENIOR_ID, ym);

        org.assertj.core.api.Assertions.assertThat(resp.days()).hasSize(28);
    }

    @Test
    @DisplayName("weekly — 미래 일자(weekStart=today+1)는 FUTURE, adherenceRate=null")
    void weekly_futureWeek() throws Exception {
        givenSeniorAndCaregiver();
        givenSeniorMealTimes(LocalTime.of(8, 0), null, null);
        when(scheduleRepository.findActiveByOwnerAndDateRange(eq(SENIOR_ID), any(), any())).thenReturn(List.of());
        when(logRepository.findBySeniorIdAndTargetDateBetweenOrderByTargetDateAscIdAsc(eq(SENIOR_ID), any(), any()))
                .thenReturn(List.of());

        final LocalDate weekStart = TODAY.plusDays(1);
        final var resp = dashboardService.getWeekly(SENIOR_ID, SENIOR_ID, weekStart);

        org.assertj.core.api.Assertions.assertThat(resp.adherenceRate()).isNull();
        org.assertj.core.api.Assertions.assertThat(resp.days()).allMatch(d -> d.dayStatus()
                == com.ppiyaki.medication.domain.DayStatus.FUTURE);
    }

    private MedicationLog logOnDate(final Long scheduleId, final LocalDate date,
            final LocalTime mealTime, final int minutesAfter) {
        return new MedicationLog(SENIOR_ID, scheduleId, date,
                LocalDateTime.of(date, mealTime).plusMinutes(minutesAfter), LogStatus.TAKEN, null, false, SENIOR_ID);
    }

    private void givenSeniorAndCaregiver() throws Exception {
        when(userRepository.findById(SENIOR_ID)).thenReturn(Optional.of(userOf(SENIOR_ID, "김장군")));
        // userId == seniorId 케이스 외에는 caregiver lookup 호출
    }

    private void givenSeniorMealTimes(final LocalTime breakfast, final LocalTime lunch,
            final LocalTime dinner) throws Exception {
        final User senior = userOf(SENIOR_ID, "김장군");
        setField(senior, "breakfastTime", breakfast);
        setField(senior, "lunchTime", lunch);
        setField(senior, "dinnerTime", dinner);
        when(userRepository.findById(SENIOR_ID)).thenReturn(Optional.of(senior));
    }

    private User userOf(final Long id, final String nickname) throws Exception {
        final java.lang.reflect.Constructor<User> ctor = User.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        final User u = ctor.newInstance();
        setField(u, "id", id);
        setField(u, "nickname", nickname);
        // 기본 createdAt = 1년 전. 가입 이전 날짜 검증 케이스에선 별도 setField로 덮음.
        setField(u, "createdAt", LocalDateTime.now().minusYears(1));
        return u;
    }

    private MedicationSchedule scheduleOf(
            final Long id, final Long medicineId, final MealSlot slot, final String dosage) throws Exception {
        final java.lang.reflect.Constructor<MedicationSchedule> ctor = MedicationSchedule.class
                .getDeclaredConstructor();
        ctor.setAccessible(true);
        final MedicationSchedule s = ctor.newInstance();
        setField(s, "id", id);
        setField(s, "medicineId", medicineId);
        setField(s, "mealSlot", slot);
        setField(s, "dosage", dosage);
        if (dosage != null) {
            final java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(\\d+(?:\\.\\d+)?)(.*)$")
                    .matcher(dosage);
            if (m.find()) {
                setField(s, "dosageQuantity", new java.math.BigDecimal(m.group(1)));
                setField(s, "dosageUnit",
                        com.ppiyaki.medication.domain.DosageUnit.fromInput(m.group(2).trim()).orElse(null));
            }
        }
        return s;
    }

    private MedicationLog logOf(final Long scheduleId, final LocalDateTime takenAt, final LogStatus status) {
        final MedicationLog l = new MedicationLog(SENIOR_ID, scheduleId, takenAt.toLocalDate(),
                takenAt, status, null, false, SENIOR_ID);
        return l;
    }

    private Medicine medicineOf(final Long id, final String name, final int remaining) throws Exception {
        final Medicine m = new Medicine(SENIOR_ID, null, name, 30, remaining, null, null);
        setField(m, "id", id);
        return m;
    }

    private static void setField(final Object target, final String fieldName, final Object value) throws Exception {
        Class<?> clazz = target.getClass();
        while (clazz != null) {
            try {
                final Field field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (final NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        throw new NoSuchFieldException(fieldName);
    }
}
