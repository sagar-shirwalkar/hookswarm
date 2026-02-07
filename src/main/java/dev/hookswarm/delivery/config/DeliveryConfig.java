package dev.hookswarm.delivery.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class DeliveryConfig {

    @Bean
    public HttpClient webhookHttpClient(
            @Value("${hookswarm.delivery.timeout-connect-seconds:5}") int connectTimeout) {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(connectTimeout))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Bean(destroyMethod = "close")
    public ExecutorService deliveryExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

}