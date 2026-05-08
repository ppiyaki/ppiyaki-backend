package com.ppiyaki.prescription.controller.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * 처방전 확정(confirm) 요청 body.
 * spec docs/features/caregiver-dashboard.md (의존: 잔여분 의미화).
 *
 * <p>보호자가 confirm 시점에 약별 총량/잔여분을 함께 입력.
 * 입력 없는 candidate는 0/0 fallback (backward compat).
 */
public record PrescriptionConfirmRequest(
        @Valid List<MedicineAmountInput> medicineAmounts
) {

    public record MedicineAmountInput(
            @NotNull Long candidateId,
            @NotNull @Min(0) Integer totalAmount,
            @NotNull @Min(0) Integer remainingAmount
    ) {
    }
}
