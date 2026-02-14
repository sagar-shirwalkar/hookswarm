package dev.hookswarm.common.config;


import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient.Builder webClientBuilder(
            @Value("${hookswarm.webclient.max-connections:500}") int maxConnections,
            @Value("${hookswarm.webclient.pending-acquire-timeout:5s}") Duration pendingAcquireTimeout,
            @Value("${hookswarm.webclient.max-idle-time:20s}") Duration maxIdleTime,
            @Value("${hookswarm.webclient.max-life-time:60s}") Duration maxLifeTime,
            @Value("${hookswarm.delivery.http-timeout-seconds:10}") long httpTimeoutSeconds) {

        ConnectionProvider provider = ConnectionProvider.builder("webhook-pool")
                .maxConnections(maxConnections)
                .pendingAcquireTimeout(pendingAcquireTimeout)
                .maxIdleTime(maxIdleTime)
                .maxLifeTime(maxLifeTime)
                .evictInBackground(Duration.ofSeconds(30))
                .build();

        HttpClient httpClient = HttpClient.create(provider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .responseTimeout(Duration.ofSeconds(httpTimeoutSeconds))
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(httpTimeoutSeconds, TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(httpTimeoutSeconds, TimeUnit.SECONDS))
                );

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient));
    }

}

