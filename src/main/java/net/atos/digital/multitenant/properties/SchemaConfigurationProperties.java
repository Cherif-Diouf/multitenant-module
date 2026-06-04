package net.atos.digital.multitenant.properties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "schema-configuration")
@Data
public class SchemaConfigurationProperties {

    List<ItemSchemaConfiguration> schemas;

    ItemSchemaConfiguration defaultSchema = new ItemSchemaConfiguration(new ArrayList<>(), true, "public", new ArrayList<>());
    private boolean includeDefaultSchemaInScheduling = true;
    private ReadinessProperties readiness = new ReadinessProperties();
    private Filters filters = new Filters(Collections.emptyList());


    List<String> excludeTablesForMigration = Arrays.asList(
            "databasechangelog",
            "databasechangeloglock"
    );

    @Data
    public static class ReadinessProperties {
        private boolean checkLiquibaseLock = true;
        private String requiredTag = "";
        private String requiredLabel = "";
    }


    public record ItemSchemaConfiguration(
            List<String> contexts,
            boolean liquibaseEnabled,
            @NotBlank
            String schemaName,
            List<String> excludePathsFilter
    ) {
    }

    public record Filters(
            List<String> excludePathsFilter
    ) {
        public Filters {
            if (excludePathsFilter == null) excludePathsFilter = List.of();
        }
    }
}