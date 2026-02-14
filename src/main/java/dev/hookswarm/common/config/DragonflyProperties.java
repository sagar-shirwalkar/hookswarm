package dev.hookswarm.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "hookswarm.dragonfly")
public record DragonflyProperties(
        String host,
        int port,
        String password,
        int database,
        Duration timeout
) {}