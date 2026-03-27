package com.stablebridge.txrecovery.domain.exception;

public class EventSerializationException extends StrException {

    public EventSerializationException(String eventId, Throwable cause) {
        super("STR-5001", "Failed to serialize event %s".formatted(eventId), cause);
    }
}
