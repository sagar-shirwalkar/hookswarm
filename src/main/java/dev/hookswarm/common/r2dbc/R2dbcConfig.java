package dev.hookswarm.common.r2dbc;

import dev.hookswarm.delivery.model.DeliveryStatus;
import dev.hookswarm.subscription.model.SubscriptionStatus;
import io.r2dbc.spi.ConnectionFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.r2dbc.config.AbstractR2dbcConfiguration;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Configuration
@EnableR2dbcRepositories(basePackages = "dev.hookswarm")
public class R2dbcConfig extends AbstractR2dbcConfiguration {

    private final ConnectionFactory connectionFactory;

    public R2dbcConfig(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @Override
    public ConnectionFactory connectionFactory() {
        return connectionFactory;
    }

    @Override
    protected List<Object> getCustomConverters() {
        return List.of(
                // Set<String> <-> text[]
                new ArrayToStringSetConverter(),
                new StringSetToArrayConverter(),

                // DeliveryStatus enum
                new StringToDeliveryStatusConverter(),
                new DeliveryStatusToStringConverter(),

                // SubscriptionStatus enum
                new StringToSubscriptionStatusConverter(),
                new SubscriptionStatusToStringConverter()
        );
    }

    @ReadingConverter
    @Component
    public static class ArrayToStringSetConverter implements Converter<String[], Set<String>> {
        @Override
        public Set<String> convert(String[] source) {
            return source == null ? Set.of() : Arrays.stream(source).collect(Collectors.toSet());
        }
    }

    @WritingConverter
    @Component
    public static class StringSetToArrayConverter implements Converter<Set<String>, String[]> {
        @Override
        public String[] convert(Set<String> source) {
            return source == null ? new String[0] : source.toArray(String[]::new);
        }
    }

    @ReadingConverter
    @Component
    public static class StringToDeliveryStatusConverter implements Converter<String, DeliveryStatus> {
        @Override
        public DeliveryStatus convert(String source) {
            return source == null ? null : DeliveryStatus.valueOf(source);
        }
    }

    @WritingConverter
    @Component
    public static class DeliveryStatusToStringConverter implements Converter<DeliveryStatus, String> {
        @Override
        public String convert(DeliveryStatus source) {
            return source == null ? null : source.name();
        }
    }

    @ReadingConverter
    @Component
    public static class StringToSubscriptionStatusConverter implements Converter<String, SubscriptionStatus> {
        @Override
        public SubscriptionStatus convert(String source) {
            return source == null ? null : SubscriptionStatus.valueOf(source);
        }
    }

    @WritingConverter
    @Component
    public static class SubscriptionStatusToStringConverter implements Converter<SubscriptionStatus, String> {
        @Override
        public String convert(SubscriptionStatus source) {
            return source == null ? null : source.name();
        }
    }

}