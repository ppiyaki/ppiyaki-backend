package com.ppiyaki.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ppiyaki.outbox.repository.OutboxMessageRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxService {

    public static final String MEDICATION_COMPLETE = "MEDICATION_COMPLETE";

    private final OutboxMessageRepository outboxMessageRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public OutboxService(
            final OutboxMessageRepository outboxMessageRepository,
            final ObjectMapper objectMapper,
            final Clock clock
    ) {
        this.outboxMessageRepository = outboxMessageRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxMessage enqueue(final String eventType, final Object payload) {
        final String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (final JsonProcessingException e) {

            throw new IllegalArgumentException(
                    "Failed to serialize outbox payload for eventType=" + eventType, e);
        }
        return outboxMessageRepository.save(OutboxMessage.create(eventType, json, LocalDateTime.now(clock)));
    }
}
