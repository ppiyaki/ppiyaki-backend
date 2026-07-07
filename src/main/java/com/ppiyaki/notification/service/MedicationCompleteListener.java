package com.ppiyaki.notification.service;

import com.ppiyaki.medication.event.MedicationTakenEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class MedicationCompleteListener {

    private final MedicationCompleteDispatcher dispatcher;

    public MedicationCompleteListener(final MedicationCompleteDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMedicationTaken(final MedicationTakenEvent event) {
        dispatcher.dispatchCompletedSlots(event.seniorId(), event.targetDate());
    }
}
