package com.ppiyaki.medication.service;

import com.ppiyaki.common.exception.BusinessException;
import com.ppiyaki.common.exception.ErrorCode;
import com.ppiyaki.medication.MedicationSchedule;
import com.ppiyaki.medication.controller.dto.ScheduleCreateRequest;
import com.ppiyaki.medication.controller.dto.ScheduleResponse;
import com.ppiyaki.medication.controller.dto.ScheduleUpdateRequest;
import com.ppiyaki.medication.repository.MedicationScheduleRepository;
import com.ppiyaki.medicine.Medicine;
import com.ppiyaki.medicine.repository.MedicineRepository;
import com.ppiyaki.user.repository.CareRelationRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MedicationScheduleService {

    private final MedicationScheduleRepository medicationScheduleRepository;
    private final MedicineRepository medicineRepository;
    private final CareRelationRepository careRelationRepository;

    public MedicationScheduleService(
            final MedicationScheduleRepository medicationScheduleRepository,
            final MedicineRepository medicineRepository,
            final CareRelationRepository careRelationRepository
    ) {
        this.medicationScheduleRepository = Objects.requireNonNull(
                medicationScheduleRepository, "medicationScheduleRepository must not be null");
        this.medicineRepository = Objects.requireNonNull(
                medicineRepository, "medicineRepository must not be null");
        this.careRelationRepository = Objects.requireNonNull(
                careRelationRepository, "careRelationRepository must not be null");
    }

    @Transactional
    public ScheduleResponse create(
            final Long userId,
            final Long medicineId,
            final ScheduleCreateRequest scheduleCreateRequest
    ) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(medicineId, "medicineId must not be null");
        Objects.requireNonNull(scheduleCreateRequest, "scheduleCreateRequest must not be null");

        final Medicine medicine = findMedicineAndValidateAccess(userId, medicineId);

        final String daysOfWeek = scheduleCreateRequest.daysOfWeek() != null
                ? scheduleCreateRequest.daysOfWeek() : "DAILY";
        final LocalDate startDate = scheduleCreateRequest.startDate() != null
                ? scheduleCreateRequest.startDate() : LocalDate.now();

        final MedicationSchedule schedule = new MedicationSchedule(
                medicine.getId(),
                scheduleCreateRequest.scheduledTime(),
                scheduleCreateRequest.dosage(),
                daysOfWeek,
                startDate,
                scheduleCreateRequest.endDate()
        );

        final MedicationSchedule savedSchedule = medicationScheduleRepository.save(schedule);
        return ScheduleResponse.from(savedSchedule);
    }

    @Transactional(readOnly = true)
    public List<ScheduleResponse> readAll(final Long userId, final Long medicineId) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(medicineId, "medicineId must not be null");

        findMedicineAndValidateAccess(userId, medicineId);

        final List<MedicationSchedule> schedules = medicationScheduleRepository.findByMedicineId(medicineId);
        return schedules.stream()
                .map(ScheduleResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ScheduleResponse readById(
            final Long userId,
            final Long medicineId,
            final Long scheduleId
    ) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(medicineId, "medicineId must not be null");
        Objects.requireNonNull(scheduleId, "scheduleId must not be null");

        findMedicineAndValidateAccess(userId, medicineId);
        final MedicationSchedule schedule = findScheduleAndValidateMedicine(scheduleId, medicineId);

        return ScheduleResponse.from(schedule);
    }

    @Transactional
    public ScheduleResponse update(
            final Long userId,
            final Long medicineId,
            final Long scheduleId,
            final ScheduleUpdateRequest scheduleUpdateRequest
    ) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(medicineId, "medicineId must not be null");
        Objects.requireNonNull(scheduleId, "scheduleId must not be null");
        Objects.requireNonNull(scheduleUpdateRequest, "scheduleUpdateRequest must not be null");

        findMedicineAndValidateAccess(userId, medicineId);
        final MedicationSchedule schedule = findScheduleAndValidateMedicine(scheduleId, medicineId);

        schedule.update(
                scheduleUpdateRequest.scheduledTime(),
                scheduleUpdateRequest.dosage(),
                scheduleUpdateRequest.daysOfWeek(),
                scheduleUpdateRequest.startDate(),
                scheduleUpdateRequest.endDate()
        );

        return ScheduleResponse.from(schedule);
    }

    @Transactional
    public void delete(
            final Long userId,
            final Long medicineId,
            final Long scheduleId
    ) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(medicineId, "medicineId must not be null");
        Objects.requireNonNull(scheduleId, "scheduleId must not be null");

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
}
