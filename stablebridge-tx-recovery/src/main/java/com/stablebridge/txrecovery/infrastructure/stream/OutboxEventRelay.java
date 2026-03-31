package com.stablebridge.txrecovery.infrastructure.stream;

import java.util.concurrent.TimeUnit;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;

import com.stablebridge.txrecovery.infrastructure.db.outbox.OutboxEventReader;
import com.stablebridge.txrecovery.infrastructure.db.outbox.OutboxEventReader.PendingOutboxEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

@RequiredArgsConstructor
@Slf4j
class OutboxEventRelay {

    static final int BATCH_SIZE = 100;
    static final int MAX_RETRIES = 5;
    static final int SEND_TIMEOUT_SECONDS = 30;

    private final OutboxEventReader outboxEventReader;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelayString = "${str.outbox.poll-interval:1000}")
    @SchedulerLock(name = "outboxEventRelay", lockAtMostFor = "PT5M", lockAtLeastFor = "PT1S")
    void relay() {
        var events = outboxEventReader.findPending(BATCH_SIZE);
        events.forEach(this::processEvent);
    }

    private void processEvent(PendingOutboxEvent event) {
        try {
            kafkaTemplate.send(event.topic(), event.partitionKey(), event.payload())
                    .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            outboxEventReader.markPublished(event.eventId());
            log.info("Relayed event {} to topic {} partition-key {}",
                    event.eventId(), event.topic(), event.partitionKey());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while relaying event {} to topic {}", event.eventId(), event.topic(), e);
            outboxEventReader.incrementRetryOrFail(event.eventId(), MAX_RETRIES);
        } catch (Exception e) {
            log.error("Failed to relay event {} to topic {}", event.eventId(), event.topic(), e);
            outboxEventReader.incrementRetryOrFail(event.eventId(), MAX_RETRIES);
        }
    }
}
