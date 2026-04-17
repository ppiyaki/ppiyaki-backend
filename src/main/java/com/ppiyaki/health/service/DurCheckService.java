package com.ppiyaki.health.service;

import com.ppiyaki.common.exception.BusinessException;
import com.ppiyaki.common.exception.ErrorCode;
import com.ppiyaki.common.mfds.CachedMfdsResponse;
import com.ppiyaki.common.mfds.MfdsApiClient;
import com.ppiyaki.health.DurCheck;
import com.ppiyaki.health.DurWarningLevel;
import com.ppiyaki.health.controller.dto.DurCheckListResponse;
import com.ppiyaki.health.controller.dto.DurCheckResponse;
import com.ppiyaki.health.controller.dto.DurWarningItem;
import com.ppiyaki.health.repository.DurCheckRepository;
import com.ppiyaki.medication.MedicationSchedule;
import com.ppiyaki.medication.repository.MedicationScheduleRepository;
import com.ppiyaki.medicine.Medicine;
import com.ppiyaki.medicine.repository.MedicineRepository;
import com.ppiyaki.user.repository.CareRelationRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix = "mfds.api", name = "service-key")
public class DurCheckService {

    private static final Logger log = LoggerFactory.getLogger(DurCheckService.class);
    private static final String OP_INTERACTION = "getUsjntTabooInfoList03";
    private static final String OP_ELDERLY = "getOdsnAtentInfoList03";
    private static final String OP_DUPLICATE = "getEfcyDplctInfoList03";
    private static final int CACHE_TTL_HOURS = 24;

    private final DurCheckRepository durCheckRepository;
    private final MedicineRepository medicineRepository;
    private final MedicationScheduleRepository medicationScheduleRepository;
    private final CareRelationRepository careRelationRepository;
    private final MfdsApiClient mfdsApiClient;

    public DurCheckService(
            final DurCheckRepository durCheckRepository,
            final MedicineRepository medicineRepository,
            final MedicationScheduleRepository medicationScheduleRepository,
            final CareRelationRepository careRelationRepository,
            final MfdsApiClient mfdsApiClient
    ) {
        this.durCheckRepository = durCheckRepository;
        this.medicineRepository = medicineRepository;
        this.medicationScheduleRepository = medicationScheduleRepository;
        this.careRelationRepository = careRelationRepository;
        this.mfdsApiClient = mfdsApiClient;
    }

    @Transactional
    public DurCheckResponse check(final Long userId, final Long medicineId, final boolean forceRefresh) {
        final Medicine medicine = findMedicineById(medicineId);
        validateAccess(userId, medicine.getOwnerId());

        if (medicine.getItemSeq() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "Medicine has no itemSeq. DUR check requires itemSeq.");
        }

        final List<Medicine> activeMedicines = findActiveMedicines(medicine.getOwnerId());
        final String comboHash = computeComboHash(activeMedicines);

        if (!forceRefresh) {
            final Optional<DurCheck> cached = durCheckRepository
                    .findFirstByMedicineIdAndComboHashAndCheckedAtAfterAndWarningLevelIsNotNullOrderByCheckedAtDesc(
                            medicineId, comboHash, LocalDateTime.now().minusHours(CACHE_TTL_HOURS));
            if (cached.isPresent()) {
                log.debug("DUR Layer 2 cache hit: medicineId={} comboHash={}", medicineId, comboHash);
                return DurCheckResponse.from(cached.get(), List.of(), true);
            }
        }

        final List<DurWarningItem> warnings = new ArrayList<>();

        try {
            checkInteractions(medicine, activeMedicines, warnings);
            checkElderly(activeMedicines, warnings);
            checkDuplicate(activeMedicines, warnings);
        } catch (final BusinessException e) {
            final DurCheck failedCheck = new DurCheck(
                    medicineId, LocalDateTime.now(), null,
                    null, "ERROR: " + (e.getMessage() != null ? e.getMessage() : "Unknown error"), comboHash);
            durCheckRepository.save(failedCheck);
            final String errorMsg = e.getMessage() != null ? e.getMessage() : "DUR check failed";
            throw new BusinessException(ErrorCode.DUR_UNAVAILABLE, errorMsg);
        }

        final DurWarningLevel warningLevel = computeWarningLevel(warnings);
        final String warningText = buildWarningText(warnings);

        final DurCheck durCheck = new DurCheck(
                medicineId, LocalDateTime.now(), warningLevel,
                warningText, null, comboHash);
        durCheckRepository.save(durCheck);

        medicine.update(null, null, null, null, warningText);

        log.info("DUR check completed: medicineId={} level={} warnings={}",
                medicineId, warningLevel, warnings.size());

        return DurCheckResponse.from(durCheck, warnings, false);
    }

    @Transactional(readOnly = true)
    public DurCheckResponse getLatest(final Long userId, final Long medicineId) {
        final Medicine medicine = findMedicineById(medicineId);
        validateAccess(userId, medicine.getOwnerId());

        final DurCheck latest = durCheckRepository
                .findFirstByMedicineIdAndWarningLevelIsNotNullOrderByCheckedAtDesc(medicineId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEDICINE_NOT_FOUND));

        return DurCheckResponse.from(latest, List.of(), false);
    }

    @Transactional(readOnly = true)
    public DurCheckListResponse getHistory(final Long userId, final Long medicineId, final int limit) {
        final Medicine medicine = findMedicineById(medicineId);
        validateAccess(userId, medicine.getOwnerId());

        final int effectiveLimit = Math.min(Math.max(limit, 1), 50);
        final List<DurCheck> history = durCheckRepository
                .findByMedicineIdOrderByCheckedAtDesc(medicineId, PageRequest.of(0, effectiveLimit));

        final List<DurCheckResponse> responses = history.stream()
                .map(dc -> DurCheckResponse.from(dc, List.of(), false))
                .toList();

        return new DurCheckListResponse(responses);
    }

    private void checkInteractions(
            final Medicine targetMedicine,
            final List<Medicine> activeMedicines,
            final List<DurWarningItem> warnings
    ) {
        final Set<String> otherItemSeqs = activeMedicines.stream()
                .filter(m -> !m.getId().equals(targetMedicine.getId()))
                .map(Medicine::getItemSeq)
                .filter(seq -> seq != null)
                .collect(Collectors.toSet());

        if (otherItemSeqs.isEmpty()) {
            return;
        }

        final Map<String, String> params = new LinkedHashMap<>();
        params.put("itemSeq", targetMedicine.getItemSeq());
        params.put("numOfRows", "100");
        params.put("pageNo", "1");

        final CachedMfdsResponse response = mfdsApiClient.call(
                OP_INTERACTION, params, "interaction:" + targetMedicine.getItemSeq());

        for (final Map<String, Object> item : response.items()) {
            final String mixtureItemSeq = getString(item, "MIXTURE_ITEM_SEQ");
            if (mixtureItemSeq != null && otherItemSeqs.contains(mixtureItemSeq)) {
                warnings.add(new DurWarningItem(
                        "INTERACTION",
                        getString(item, "MIXTURE_ITEM_NAME"),
                        "BLOCK",
                        getString(item, "PROHBT_CONTENT"),
                        getString(item, "REMARK")
                ));
            }
        }
    }

    private void checkElderly(final List<Medicine> activeMedicines, final List<DurWarningItem> warnings) {
        for (final Medicine medicine : activeMedicines) {
            if (medicine.getItemSeq() == null) {
                continue;
            }

            final Map<String, String> params = new LinkedHashMap<>();
            params.put("itemSeq", medicine.getItemSeq());
            params.put("numOfRows", "10");
            params.put("pageNo", "1");

            final CachedMfdsResponse response = mfdsApiClient.call(
                    OP_ELDERLY, params, "elderly:" + medicine.getItemSeq());

            if (response.totalCount() > 0 && !response.items().isEmpty()) {
                final Map<String, Object> firstItem = response.items().getFirst();
                warnings.add(new DurWarningItem(
                        "ELDERLY",
                        null,
                        "WARN",
                        medicine.getName() + ": " + getString(firstItem, "PROHBT_CONTENT"),
                        getString(firstItem, "REMARK")
                ));
            }
        }
    }

    private void checkDuplicate(final List<Medicine> activeMedicines, final List<DurWarningItem> warnings) {
        final Map<String, List<String>> effectGroupToMedicines = new LinkedHashMap<>();

        for (final Medicine medicine : activeMedicines) {
            if (medicine.getItemSeq() == null) {
                continue;
            }

            final Map<String, String> params = new LinkedHashMap<>();
            params.put("itemSeq", medicine.getItemSeq());
            params.put("numOfRows", "10");
            params.put("pageNo", "1");

            final CachedMfdsResponse response = mfdsApiClient.call(
                    OP_DUPLICATE, params, "duplicate:" + medicine.getItemSeq());

            for (final Map<String, Object> item : response.items()) {
                final String effectName = getString(item, "EFFECT_NAME");
                if (effectName != null) {
                    effectGroupToMedicines
                            .computeIfAbsent(effectName, k -> new ArrayList<>())
                            .add(medicine.getName());
                }
            }
        }

        for (final Map.Entry<String, List<String>> entry : effectGroupToMedicines.entrySet()) {
            if (entry.getValue().size() >= 2) {
                warnings.add(new DurWarningItem(
                        "DUPLICATE",
                        String.join(", ", entry.getValue()),
                        "INFO",
                        "효능군 '" + entry.getKey() + "' 중복: " + String.join(", ", entry.getValue()),
                        null
                ));
            }
        }
    }

    private List<Medicine> findActiveMedicines(final Long ownerId) {
        final LocalDate today = LocalDate.now();
        final List<Medicine> allMedicines = medicineRepository.findByOwnerId(ownerId);

        return allMedicines.stream()
                .filter(m -> {
                    final List<MedicationSchedule> schedules = medicationScheduleRepository.findByMedicineId(m.getId());
                    return schedules.stream().anyMatch(s -> isActiveSchedule(s, today));
                })
                .toList();
    }

    private boolean isActiveSchedule(final MedicationSchedule schedule, final LocalDate today) {
        final boolean afterStart = schedule.getStartDate() == null
                || !schedule.getStartDate().isAfter(today);
        final boolean beforeEnd = schedule.getEndDate() == null
                || !schedule.getEndDate().isBefore(today);
        return afterStart && beforeEnd;
    }

    private String computeComboHash(final List<Medicine> activeMedicines) {
        final String sorted = activeMedicines.stream()
                .map(Medicine::getItemSeq)
                .filter(seq -> seq != null)
                .sorted()
                .collect(Collectors.joining(","));

        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            final byte[] hash = digest.digest(sorted.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (final NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private DurWarningLevel computeWarningLevel(final List<DurWarningItem> warnings) {
        if (warnings.isEmpty()) {
            return DurWarningLevel.NONE;
        }
        if (warnings.stream().anyMatch(w -> "BLOCK".equals(w.severity()))) {
            return DurWarningLevel.BLOCK;
        }
        if (warnings.stream().anyMatch(w -> "WARN".equals(w.severity()))) {
            return DurWarningLevel.WARN;
        }
        return DurWarningLevel.INFO;
    }

    private String buildWarningText(final List<DurWarningItem> warnings) {
        if (warnings.isEmpty()) {
            return null;
        }
        return warnings.stream()
                .map(DurWarningItem::description)
                .filter(d -> d != null)
                .collect(Collectors.joining("; "));
    }

    private Medicine findMedicineById(final Long medicineId) {
        return medicineRepository.findById(medicineId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEDICINE_NOT_FOUND));
    }

    private void validateAccess(final Long userId, final Long ownerId) {
        if (userId.equals(ownerId)) {
            return;
        }
        careRelationRepository.findByCaregiverIdAndSeniorIdAndDeletedAtIsNull(userId, ownerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CARE_RELATION_NOT_FOUND));
    }

    private String getString(final Map<String, Object> item, final String key) {
        final Object value = item.get(key);
        return value != null ? value.toString() : null;
    }
}
