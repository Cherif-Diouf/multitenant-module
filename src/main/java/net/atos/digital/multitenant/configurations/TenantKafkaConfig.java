package net.atos.digital.multitenant.configurations;

import lombok.extern.slf4j.Slf4j;
import net.atos.digital.multitenant.tenant.kafka.KafkaAdminService;
import net.atos.digital.multitenant.tenant.kafka.MultitenantKafkaMessageConverter;
import net.atos.digital.multitenant.utils.ProfileConstants;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.*;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

@Profile(ProfileConstants.MULTI_SCHEMA_OR_MULTITENANT)
@Configuration
@ConditionalOnClass(KafkaTemplate.class)
@Slf4j
public class TenantKafkaConfig {

    private final KafkaProperties kafkaProperties;

    public TenantKafkaConfig(KafkaProperties kafkaProperties) {
        this.kafkaProperties = kafkaProperties;
    }

    @Bean
    public AdminClient kafkaAdminClient() {
        Map<String, Object> props = new HashMap<>(kafkaProperties.getProperties());
        props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 5000);
        return AdminClient.create(props);
    }

    @Primary
    @Bean
    public ProducerFactory<String, Object> tenantProducerFactory() {
        Map<String, Object> configProps = new HashMap<>();

        if (kafkaProperties.getBootstrapServers() != null && !kafkaProperties.getBootstrapServers().isEmpty()) {
            configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.getBootstrapServers());
        }

        if (kafkaProperties.getProducer().getProperties() != null) {
            configProps.putAll(kafkaProperties.getProducer().getProperties());
        }

        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        configProps.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);

        configProps.put(ProducerConfig.ACKS_CONFIG, "all");
        configProps.put(ProducerConfig.RETRIES_CONFIG, 3);
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

        return new DefaultKafkaProducerFactory<>(configProps);
    }

    @Primary
    @Bean
    public KafkaTemplate<String, Object> tenantKafkaTemplate(ProducerFactory<String, Object> producerFactory,
                                                             MultitenantKafkaMessageConverter messageConverter) {
        KafkaTemplate<String, Object> template = new KafkaTemplate<>(producerFactory);
        template.setMessageConverter(messageConverter);
        return template;
    }

    @Primary
    @Bean
    public ConsumerFactory<String, Object> tenantConsumerFactory() {

        Map<String, Object> configProps = new HashMap<>();

        if (kafkaProperties.getBootstrapServers() != null && !kafkaProperties.getBootstrapServers().isEmpty()) {
            configProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.getBootstrapServers());
        }

        if (kafkaProperties.getConsumer().getProperties() != null) {
            configProps.putAll(kafkaProperties.getConsumer().getProperties());
        }

        String groupId = kafkaProperties.getConsumer().getGroupId();
        if (groupId != null) {
            configProps.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        }

        configProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        configProps.put(JsonDeserializer.TRUSTED_PACKAGES, "net.atos.digital.*");
        configProps.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);

        configProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        configProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        return new DefaultKafkaConsumerFactory<>(configProps);
    }

    @Primary
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> tenantKafkaListenerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory,
            MultitenantKafkaMessageConverter messageConverter, KafkaTemplate<String, Object> kafkaTemplate, KafkaAdminService kafkaAdminService) {

        ConcurrentKafkaListenerContainerFactory<String, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setRecordMessageConverter(messageConverter);

        ContainerProperties containerProperties = factory.getContainerProperties();
        containerProperties.setAckMode(ContainerProperties.AckMode.RECORD);
        containerProperties.setSyncCommits(true);

        factory.setCommonErrorHandler(createErrorHandler(kafkaTemplate));


        return factory;
    }

    private DefaultErrorHandler createErrorHandler(KafkaTemplate<String, Object> kafkaTemplate) {
        FixedBackOff fixedBackOff = new FixedBackOff(1000L, 3L);

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(

                new DeadLetterPublishingRecoverer(kafkaTemplate,
                        (record, ex) -> new TopicPartition(record.topic() + ".DLT", record.partition())),
                fixedBackOff
        );

        errorHandler.setRetryListeners((record, ex, deliveryAttempt) ->
                log.warn("Retry attempt {} for record: {} - Error: {}",
                        deliveryAttempt, record, ex.getMessage()));

        return errorHandler;
    }

}
