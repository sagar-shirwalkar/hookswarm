package dev.hookswarm.event.model;


// Distinguish created new event (201) from returned existing event via idempotency key (200)
public record EventIngestResult(Event event, boolean created) {}