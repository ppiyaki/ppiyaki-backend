package com.ppiyaki.medication.service;

import com.ppiyaki.common.exception.BusinessException;
import com.ppiyaki.common.exception.ErrorCode;
import com.ppiyaki.medication.controller.dto.ScheduleCreateRequest;
import com.ppiyaki.medication.controller.dto.ScheduleResponse;
import com.ppiyaki.medication.controller.dto.ScheduleUpdateRequest;
import com.ppiyaki.medication.domain.MealSlot;
import com.ppiyaki.medication.domain.MedicationSchedule;
import com.ppiyaki.medication.repository.MedicationScheduleRepository;
import com.ppiyaki.medicine.Medicine;
import com.ppiyaki.medicine.repository.MedicineRepository;
import com.ppiyaki.user.domain.User;
import com.ppiyaki.user.repository.CareRelationRepository;
import com.ppiyaki.user.repository.UserRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MedicationScheduleService {

    private final MedicationScheduleRepository medicationScheduleRepository;
    private final MedicineRepository medicineRepository;
    private final CareRelationRepository careRelationRepository;
    private final UserRepository userRepository;

    public MedicationScheduleService(
            final MedicationScheduleRepository medicationScheduleRepository,
            final MedicineRepository medicineRepository,
            final CareRelationRepository careRelationRepository,
            final UserRepository userRepository
    ) {
        this.medicationScheduleRepository = medicationScheduleRepository;
        this.medicineRepository = medicineRepository;
        this.careRelationRepository = careRelationRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public ScheduleResponse create(
            final Long userId,
            final Long medicineId,
            final ScheduleCreateRequest scheduleCreateRequest
    ) {
        final Medicine medicine = findMedicineAndValidateAccess(userId, medicineId);
        final User owner = findOwner(medicine.getOwnerId());
        validateMealTimeSet(owner, scheduleCreateRequest.mealSlot());

        final String daysOfWeek = scheduleCreateRequest.daysOfWeek() != null
                ? scheduleCreateRequest.daysOfWeek() : "DAILY";
        final LocalDate startDate = scheduleCreateRequest.startDate() != null
                ? scheduleCreateRequest.startDate() : LocalDate.now();

        final MedicationSchedule schedule = new MedicationSchedule(
                medicine.getId(),
                scheduleCreateRequest.mealSlot(),
                scheduleCreateRequest.dosageQuantity(),
                com.ppiyaki.medication.domain.DosageUnit.fromInput(scheduleCreateRequest.dosageUnit()).orElse(null),
                daysOfWeek,
                startDate,
                scheduleCreateRequest.endDate()
        );

        final MedicationSchedule savedSchedule = medicationScheduleRepository.save(schedule);
        return ScheduleResponse.from(savedSchedule, owner);
    }

    @Transactional(readOnly = true)
    public List<ScheduleResponse> readAll(final Long userId, final Long medicineId) {
        final Medicine medicine = findMedicineAndValidateAccess(userId, medicineId);
        final User owner = findOwner(medicine.getOwnerId());

        final List<MedicationSchedule> schedules = medicationScheduleRepository.findByMedicineId(medicineId);
        return schedules.stream()
                .map(schedule -> ScheduleResponse.from(schedule, owner))
                .toList();
    }

    @Transactional(readOnly = true)
    public ScheduleResponse readById(
            final Long userId,
            final Long medicineId,
            final Long scheduleId
    ) {
        final Medicine medicine = findMedicineAndValidateAccess(userId, medicineId);
        final User owner = findOwner(medicine.getOwnerId());
        final MedicationSchedule schedule = findScheduleAndValidateMedicine(scheduleId, medicineId);

        return ScheduleResponse.from(schedule, owner);
    }

    @Transactional
    public ScheduleResponse update(
            final Long userId,
            final Long medicineId,
            final Long scheduleId,
            final ScheduleUpdateRequest scheduleUpdateRequest
    ) {
        final Medicine medicine = findMedicineAndValidateAccess(userId, medicineId);
        final User owner = findOwner(medicine.getOwnerId());
        final MedicationSchedule schedule = findScheduleAndValidateMedicine(scheduleId, medicineId);

        if (scheduleUpdateRequest.mealSlot() != null) {
            validateMealTimeSet(owner, scheduleUpdateRequest.mealSlot());
        }

        schedule.update(
                scheduleUpdateRequest.mealSlot(),
                scheduleUpdateRequest.dosageQuantity(),
                com.ppiyaki.medication.domain.DosageUnit.fromInput(scheduleUpdateRequest.dosageUnit()).orElse(null),
                scheduleUpdateRequest.daysOfWeek(),
                scheduleUpdateRequest.startDate(),
                scheduleUpdateRequest.endDate()
        );

        return ScheduleResponse.from(schedule, owner);
    }

    @Transactional
    public void delete(
            final Long userId,
            final Long medicineId,
            final Long scheduleId
    ) {
        findMedicineAndValidateAccess(userId, medicineId);
        final MedicationSchedule schedule = findScheduleAndValidateMedicine(scheduleId, medicineId);

        medicationScheduleRepository.delete(schedule);
    }

    private Medicine findMedicineAndValidateAccess(final Long userId, final Long medicineId) {
        final Medicine medicine = medicineRepository.findById(medicineId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.MEDICINE_NOT_FOUND, "Medicine not found: " + medicineId));

        final Long ownerId = medicine.getOwnerId();
        if (!userId.equals(ownerId)) {
            careRelationRepository.findByCaregiverIdAndSeniorIdAndDeletedAtIsNull(userId, ownerId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.CARE_RELATION_NOT_FOUND));
        }

        return medicine;
    }

    private MedicationSchedule findScheduleAndValidateMedicine(
            final Long scheduleId,
            final Long medicineId
    ) {
        final MedicationSchedule schedule = medicationScheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.SCHEDULE_NOT_FOUND, "Schedule not found: " + scheduleId));

        if (!schedule.getMedicineId().equals(medicineId)) {
            throw new BusinessException(ErrorCode.SCHEDULE_MEDICINE_MISMATCH);
        }

        return schedule;
    }

    private User findOwner(final Long ownerId) {
        return userRepository.findById(ownerId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.USER_NOT_FOUND, "Owner not found: " + ownerId));
    }

    private void validateMealTimeSet(final User owner, final MealSlot mealSlot) {
        if (mealSlot.resolveTime(owner) == null) {
            throw new BusinessException(ErrorCode.MEAL_TIMES_NOT_SET);
        }
    }
}
