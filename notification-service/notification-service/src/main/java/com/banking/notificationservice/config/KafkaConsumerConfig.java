package com.banking.notificationservice.config;

import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;

@Configuration
public class KafkaConsumerConfig {

    /**
     * Configures retry, backoff and Dead Letter Topic handling.
     *
     * One Kafka message fails
     * 1. First attempt
     *
     *    Original listener processes the event and fails.
     * 2. Retry 1 — after 1 second
     * 3. Retry 2 — after 2 seconds
     * 4. Retry 3 — after 4 seconds
     * 5. Still failing?
     *    Send to DLT
     *    The original event is published to a separate dead-letter topic.
     */
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> kafkaTemplate) {

        // Send failed messages to <original-topic>-dlt
        DeadLetterPublishingRecoverer recoverer =
                new DeadLetterPublishingRecoverer(
                        kafkaTemplate,
                        (record, exception) ->
                                new TopicPartition(
                                        record.topic() + "-dlt",
                                        -1
                                )
                );

        // Ensure DLT publishing failures are not silently ignored.
        recoverer.setFailIfSendResultIsError(true);

        // 3 retries after the first attempt.
        ExponentialBackOffWithMaxRetries backOff =
                new ExponentialBackOffWithMaxRetries(3);

        backOff.setInitialInterval(1000L);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(5000L);

        DefaultErrorHandler errorHandler =
                new DefaultErrorHandler(recoverer, backOff);

        // Invalid input should not be retried.
        errorHandler.addNotRetryableExceptions(
                IllegalArgumentException.class
        );

        return errorHandler;
    }

    /**
     * Configures how Kafka listener methods consume records.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<Object, Object>
    kafkaListenerContainerFactory(
            ConsumerFactory<Object, Object> consumerFactory,
            DefaultErrorHandler kafkaErrorHandler) {

        ConcurrentKafkaListenerContainerFactory<Object, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(consumerFactory);

        // Commit offsets after each successfully processed record.
        factory.getContainerProperties()
                .setAckMode(ContainerProperties.AckMode.RECORD);

        factory.setCommonErrorHandler(kafkaErrorHandler);

        return factory;
    }
}
