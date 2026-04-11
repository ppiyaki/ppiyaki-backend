package com.ppiyaki.medicine.service;

import com.ppiyaki.common.exception.BusinessException;
import com.ppiyaki.common.exception.ErrorCode;
import com.ppiyaki.medication.repository.MedicationScheduleRepository;
import com.ppiyaki.medicine.Medicine;
import com.ppiyaki.medicine.controller.dto.MedicineCreateRequest;
import com.ppiyaki.medicine.controller.dto.MedicineDeleteResponse;
import com.ppiyaki.medicine.controller.dto.MedicineResponse;
import com.ppiyaki.medicine.controller.dto.MedicineUpdateRequest;
import com.ppiyaki.medicine.repository.MedicineRepository;
import com.ppiyaki.user.User;
import com.ppiyaki.user.UserRole;
import com.ppiyaki.user.repository.CareRelationRepository;
import com.ppiyaki.user.repository.UserRepository;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MedicineService {

    private final MedicineRepository medicineRepository;
    private final MedicationScheduleRepository medicationScheduleRepository;
    private final UserRepository userRepository;
    private final CareRelationRepository careRelationRepository;

    public MedicineService(
            final MedicineRepository medicineRepository,
            final MedicationScheduleRepository medicationScheduleRepository,
            final UserRepository userRepository,
            final CareRelationRepository careRelationRepository
    ) {
        this.medicineRepository = Objects.requireNonNull(medicineRepository,
                "medicineRepository must not be null");
        this.medicationScheduleRepository = Objects.requireNonNull(medicationScheduleRepository,
                "medicationScheduleRepository must not be null");
        this.userRepository = Objects.requireNonNull(userRepository,
                "userRepository must not be null");
        this.careRelationRepository = Objects.requireNonNull(careRelationRepository,
                "careRelationRepository must not be null");
    }

    @Transactional
    public MedicineResponse create(final Long userId, final MedicineCreateRequest medicineCreateRequest) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(medicineCreateRequest, "medicineCreateRequest must not be null");

        final Long ownerId = resolveOwnerId(userId, medicineCreateRequest.seniorId());

        final Medicine medicine = new Medicine(
                ownerId,
                null,
                medicineCreateRequest.name(),
                medicineCreateRequest.totalAmount(),
                medicineCreateRequest.remainingAmount(),
                medicineCreateRequest.durWarningText()
        );

        final Medicine savedMedicine = medicineRepository.save(medicine);
        return MedicineResponse.from(savedMedicine);
    }

    @Transactional(readOnly = true)
    public List<MedicineResponse> readAll(final Long userId, final Long seniorId) {
        Objects.requireNonNull(userId, "userId must not be null");

        final Long ownerId = resolveOwnerIdForRead(userId, seniorId);

        final List<Medicine> medicines = medicineRepository.findByOwnerId(ownerId);
        return medicines.stream()
                .map(MedicineResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public MedicineResponse readById(final Long userId, final Long medicineId) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(medicineId, "medicineId must not be null");

        final Medicine medicine = findMedicineById(medicineId);
        validateAccess(userId, medicine.getOwnerId());

        return MedicineResponse.from(medicine);
    }

    @Transactional
    public MedicineResponse update(
            final Long userId,
            final Long medicineId,
            final MedicineUpdateRequest medicineUpdateRequest
    ) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(medicineId, "medicineId must not be null");
        Objects.requireNonNull(medicineUpdateRequest, "medicineUpdateRequest must not be null");

        final Medicine medicine = findMedicineById(medicineId);
        validateAccess(userId, medicine.getOwnerId());

        medicine.update(
                medicineUpdateRequest.name(),
                medicineUpdateRequest.totalAmount(),
                medicineUpdateRequest.remainingAmount(),
                medicineUpdateRequest.durWarningText()
        );

        return MedicineResponse.from(medicine);
    }

    @Transactional
    public MedicineDeleteResponse delete(final Long userId, final Long medicineId) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(medicineId, "medicineId must not be null");

        final Medicine medicine = findMedicineById(medicineId);
        validateAccess(userId, medicine.getOwnerId());

        final int deletedScheduleCount = medicationScheduleRepository.deleteByMedicineId(medicineId);
        medicineRepository.delete(medicine);

        return new MedicineDeleteResponse(medicineId, deletedScheduleCount);
    }

    private Medicine findMedicineById(final Long medicineId) {
        return medicineRepository.findById(medicineId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.MEDICINE_NOT_FOUND, "Medicine not found: " + medicineId));
    }

    private User findUserById(final Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.USER_NOT_FOUND, "User not found: " + userId));
    }

    private Long resolveOwnerId(final Long userId, final Long seniorId) {
        final User user = findUserById(userId);

        if (seniorId == null) {
            if (user.getRole() == UserRole.CAREGIVER) {
                throw new BusinessException(ErrorCode.CARE_RELATION_REQUIRED);
            }
            return userId;
        }

        if (user.getRole() != UserRole.CAREGIVER) {
            throw new BusinessException(ErrorCode.CARE_RELATION_NOT_CAREGIVER);
        }

        validateCareRelation(userId, seniorId);
        return seniorId;
    }

    private Long resolveOwnerIdForRead(final Long userId, final Long seniorId) {
        final User user = findUserById(userId);

        if (seniorId == null) {
            if (user.getRole() == UserRole.CAREGIVER) {
                throw new BusinessException(ErrorCode.CARE_RELATION_REQUIRED);
            }
            return userId;
        }

        if (user.getRole() != UserRole.CAREGIVER) {
            throw new BusinessException(ErrorCode.CARE_RELATION_NOT_CAREGIVER);
        }

        validateCareRelation(userId, seniorId);
        return seniorId;
    }

    private void validateAccess(final Long userId, final Long ownerId) {
        if (userId.equals(ownerId)) {
            return;
        }

        validateCareRelation(userId, ownerId);
    }

    private void validateCareRelation(final Long caregiverId, final Long seniorId) {
        careRelationRepository.findByCaregiverIdAndSeniorIdAndDeletedAtIsNull(caregiverId, seniorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CARE_RELATION_NOT_FOUND));
    }
}
