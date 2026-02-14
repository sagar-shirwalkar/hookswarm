package dev.hookswarm.common.queue;

import java.util.Map;

public record QueueMessage(String id, Map<String, String> body) {}