package dev.hookswarm.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import java.time.Duration;

@ConfigurationProperties(prefix = "hookswarm.dragonfly")
public record DragonflyProperties(
        @DefaultValue("dragonfly") String host,
        @DefaultValue("6379") int port,
        String password,
        @DefaultValue("0") int database,
        @DefaultValue("10s") Duration timeout
) {}