

package net.atos.digital.multitenant.tenant.hibernate;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import net.atos.digital.multitenant.tenant.hibernate.schema.SchemaCreatorService;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Slf4j
public class DatabaseSchemaInitializer {
    private static final Pattern VALID_SCHEMA_NAME = Pattern.compile("^[0-9A-Za-z_]+$");

    private final SchemaCreatorService schemaCreatorService;
    private final JdbcTemplate jdbcTemplate;

    public DatabaseSchemaInitializer(SchemaCreatorService schemaCreatorService, JdbcTemplate jdbcTemplate) {
        this.schemaCreatorService = schemaCreatorService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void init() {
        List<String> schemas = new ArrayList<>();
        schemas.addAll(schemaCreatorService.getAllSchemas());
        schemas.addAll(schemaCreatorService.getAllUpcomingSchemas());
        createTenantsSchemas(schemas);
    }

    public void createTenantsSchemas(List<String> schemas){
        schemas.forEach(schemaName -> {

            try {
                validateSchemaName(schemaName);

                log.info(String.format("SET search_path TO '%s'", schemaName));
                jdbcTemplate.execute(String.format("CREATE SCHEMA IF NOT EXISTS \"%s\";", schemaName));
            } catch (DataAccessException e) {
                log.error("Error execution Schema creation", e);
            }
        });
    }

    private void validateSchemaName(String schemaName) {
        if (schemaName == null || schemaName.isBlank()) {
            throw new IllegalArgumentException("Schema name must not be null/blank");
        }
        if (!VALID_SCHEMA_NAME.matcher(schemaName).matches()) {
            throw new IllegalArgumentException("Schema name is malformed: " + schemaName);
        }
    }

    private String quoteIdentifier(String identifier) {
        return "\"" + identifier + "\"";
    }

}