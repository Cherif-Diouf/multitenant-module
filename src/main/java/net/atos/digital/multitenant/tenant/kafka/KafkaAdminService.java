package net.atos.digital.multitenant.tenant.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeTopicsResult;
import org.apache.kafka.clients.admin.TopicDescription;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

@Service
@ConditionalOnClass(KafkaTemplate.class)
@RequiredArgsConstructor
@Slf4j
public class KafkaAdminService {

    private final KafkaProperties kafkaProperties;
    private final AdminClient adminClient;

    /**
     * Gets the number of partitions for a topic
     */
    public int getPartitionCount(String topicName) {

        try {
            DescribeTopicsResult result = adminClient.describeTopics(Collections.singletonList(topicName));
            TopicDescription topicDescription = result.topicNameValues().get(topicName).get();
            int partitionCount = topicDescription.partitions().size();

            log.info("Topic '{}' has {} partitions", topicName, partitionCount);
            return partitionCount;

        } catch (ExecutionException | InterruptedException e) {
            log.error("Error retrieving information for topic '{}'", topicName, e);
            return 1;
        }
    }

    /**
     * Calculates optimal concurrency based on partition count and CPU cores
     */
    public int getOptimalConcurrency(String topicName) {
        if (topicName == null || topicName.trim().isEmpty()) {
            throw new IllegalArgumentException("Topic name cannot be null or empty");
        }

        Integer configured = kafkaProperties.getListener().getConcurrency();
        int partitionCount = getPartitionCount(topicName);

        if (Objects.nonNull(configured) && configured > 0) {
            int concurrency = Math.min(partitionCount, configured);
            log.info("Concurrency determined by configuration: topic='{}', partitions={}, configured={}, selected={}",
                    topicName, partitionCount, configured, concurrency);
            return concurrency;

        }

        int availableCpus = Runtime.getRuntime().availableProcessors();

        int optimalConcurrency = Math.min(partitionCount, availableCpus);

        log.info("Topic '{}': {} partitions, {} CPU cores -> optimal concurrency: {}",
                topicName, partitionCount, availableCpus, optimalConcurrency);

        return optimalConcurrency;
    }

    /**
     * Logs the details of a topic
     */
    public void logTopicDetails(String topicName) {

        try {
            DescribeTopicsResult result = adminClient.describeTopics(Collections.singletonList(topicName));
            TopicDescription topicDescription = result.topicNameValues().get(topicName).get();

            log.info("=== Topic Details ===");
            log.info("Topic: {}", topicDescription.name());
            log.info("Partitions: {}", topicDescription.partitions().size());
            log.info("Replication Factor: {}",
                    topicDescription.partitions().isEmpty() ? 0 : topicDescription.partitions().get(0).replicas().size());
            log.info("Internal: {}", topicDescription.isInternal());

            topicDescription.partitions().forEach(partition ->
                    log.info("Partition {}: Leader={}, Replicas={}, ISR={}",
                            partition.partition(),
                            partition.leader(),
                            partition.replicas().size(),
                            partition.isr().size())
            );

        } catch (ExecutionException | InterruptedException e) {
            log.error("Error retrieving details for topic '{}'", topicName, e);
        }
    }
}