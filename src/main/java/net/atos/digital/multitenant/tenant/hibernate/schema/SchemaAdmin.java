package net.atos.digital.multitenant.tenant.hibernate.schema;

import jakarta.annotation.PostConstruct;
import liquibase.integration.spring.SpringLiquibase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.atos.digital.multitenant.tenant.properties.TenantData;
import net.atos.digital.multitenant.utils.ProfileConstants;
import net.atos.digital.multitenant.utils.SchemaUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Locale;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnClass({DataSource.class, SpringLiquibase.class})
@Profile(ProfileConstants.MULTI_SCHEMA_OR_MULTITENANT)
public class SchemaAdmin {

    private final JdbcTemplate jdbcTemplate;
    private String dbProductName = "";
    @Value("${app.migration.audit.schema:public}")
    private String auditSchema;

    @Value("${app.migration.audit.table:migration_audit_log}")
    private String auditTable;

    @PostConstruct
    void detectDatabase() {
        DataSource ds = jdbcTemplate.getDataSource();
        if (ds == null) {
            log.warn("No DataSource available to detect database type.");
            return;
        }
        try (Connection c = ds.getConnection()) {
            String name = c.getMetaData().getDatabaseProductName();
            dbProductName = name == null ? "" : name.toLowerCase(Locale.ROOT);
            log.info("Detected database: {}", dbProductName);
        } catch (SQLException e) {
            log.warn("Could not detect database type.", e);
        }
    }

    public boolean isH2() {
        return dbProductName.contains("h2");
    }

    public boolean isPostgres() {
        return dbProductName.contains("postgres");
    }

    public boolean schemaExists(String schemaName) {
        String sql = "SELECT COUNT(*) FROM INFORMATION_SCHEMA.SCHEMATA WHERE LOWER(SCHEMA_NAME) = LOWER(?)";
        try {
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, schemaName);
            return count != null && count > 0;
        } catch (Exception e) {
            log.error("Error while checking for existence of schema {}", schemaName, e);
            return false;
        }
    }

    public boolean tableExists(String schemaName, String tableName) {
        String sql = "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE LOWER(TABLE_SCHEMA) = LOWER(?) AND LOWER(TABLE_NAME) = LOWER(?)";
        try {
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, schemaName, tableName);
            return count != null && count > 0;
        } catch (Exception e) {
            log.error("Error while checking for existence of table {}.{}", schemaName, tableName, e);
            return false;
        }
    }

    public void createSchemaIfNotExists(String schemaName) {
        jdbcTemplate.execute("CREATE SCHEMA IF NOT EXISTS " + SchemaUtils.quote(schemaName));
    }

    /**
     * Supprime définitivement un schéma et tout son contenu (CASCADE).
     * Opération destructrice : réservée au dé-provisioning d'un tenant.
     */
    public void dropSchemaIfExists(String schemaName) {
        jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + SchemaUtils.quote(schemaName) + " CASCADE");
        log.info("Schema '{}' dropped.", schemaName);
    }

    public void renameSchema(String fromSchema, String toSchema) {
        jdbcTemplate.execute("ALTER SCHEMA " + SchemaUtils.quote(fromSchema) + " RENAME TO " + SchemaUtils.quote(toSchema));
    }

    /**
     * Records a successful migration step in the audit log.
     */
    public void markMigrationStep(TenantData tenant, String step, String message, String detailsArchive) {
        String fqtn = SchemaUtils.quote(auditSchema) + "." + SchemaUtils.quote(auditTable);
        String sql = "INSERT INTO " + fqtn + " (tenant_name, step_name, status, details, archive_details, event_timestamp, alert_sent) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try {
            jdbcTemplate.update(sql, tenant.tenantName(), step, "COMPLETED", message, detailsArchive, java.sql.Timestamp.from(Instant.now()), false);
            log.info("Audit: Tenant '{}' completed migration step '{}'.", tenant.tenantName(), step);
        } catch (DataAccessException e) {
            // Log error but don't fail the migration for an audit failure.
            log.error("Failed to mark migration step '{}' for tenant '{}' in audit log.", step, tenant.tenantName(), e);
        }
    }

    /**
     * Records a failed migration in the audit log.
     */
    public void markMigrationFailed(TenantData tenant, String errorMessage, String detailsArchive) {
        String fqtn = SchemaUtils.quote(auditSchema) + "." + SchemaUtils.quote(auditTable);
        String sql = "INSERT INTO " + fqtn + " (tenant_name, status, details, archive_details, event_timestamp, alert_sent) VALUES (?, ?, ?, ?, ?, ?)";
        try {
            // Truncate error message if it's too long for the DB column
            String details = (errorMessage != null && errorMessage.length() > 2048) ? errorMessage.substring(0, 2048) : errorMessage;
            jdbcTemplate.update(sql, tenant.tenantName(), "FAILED", details, detailsArchive, java.sql.Timestamp.from(Instant.now()), false);
            log.info("Audit: Tenant '{}' migration failed.", tenant.tenantName());
        } catch (DataAccessException e) {
            log.error("CRITICAL: Failed to mark migration as FAILED for tenant '{}' in audit log.", tenant.tenantName(), e);
        }
    }

    /**
     * Restores the data of a table from a source schema to a destination schema.
     * The destination table is truncated first.
     *
     * @param fromSchema Source schema (e.g., the archive)
     * @param toSchema   Destination schema (e.g., the new active)
     * @param tableName  The name of the table to restore
     */
    public void restoreTableData(String fromSchema, String toSchema, String tableName) {
        String qualifiedFrom = SchemaUtils.quote(fromSchema) + "." + SchemaUtils.quote(tableName);
        String qualifiedTo = SchemaUtils.quote(toSchema) + "." + SchemaUtils.quote(tableName);

        log.debug("Restoring data from {} to {}", qualifiedFrom, qualifiedTo);

        String truncateSql;
        if (isPostgres()) {
            // RESTART IDENTITY handles sequences linked to identity columns, and CASCADE handles foreign keys.
            truncateSql = "TRUNCATE TABLE " + qualifiedTo + " RESTART IDENTITY CASCADE";
        } else { // For H2
            truncateSql = "TRUNCATE TABLE " + qualifiedTo;
        }
        jdbcTemplate.execute(truncateSql);

        jdbcTemplate.execute("INSERT INTO " + qualifiedTo + " SELECT * FROM " + qualifiedFrom);
    }

    /**
     * Truncates a table, deleting all its data. This operation is used to ensure idempotency
     * before restoring data. Note: TRUNCATE is a DDL command and may cause an implicit commit.
     * The 'RESTART IDENTITY CASCADE' clause is specific to PostgreSQL but provides robust behavior.
     *
     * @param schemaName The schema of the table.
     * @param tableName The name of the table to truncate.
     */
    public void truncateTable(String schemaName, String tableName) {
        String qualifiedName = SchemaUtils.quote(schemaName) + "." + SchemaUtils.quote(tableName);
        jdbcTemplate.execute("TRUNCATE TABLE " + qualifiedName + " RESTART IDENTITY CASCADE");
    }


}