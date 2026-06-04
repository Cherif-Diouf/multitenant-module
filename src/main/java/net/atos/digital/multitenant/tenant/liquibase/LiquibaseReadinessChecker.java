package net.atos.digital.multitenant.tenant.liquibase;

import liquibase.integration.spring.SpringLiquibase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.atos.digital.multitenant.properties.SchemaConfigurationProperties;
import net.atos.digital.multitenant.utils.ProfileConstants;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnClass({DataSource.class, SpringLiquibase.class})
@Profile(ProfileConstants.MULTI_SCHEMA_OR_MULTITENANT)
public class LiquibaseReadinessChecker {

    private static final String LIQUIBASE_LOCK_TABLE = "DATABASECHANGELOGLOCK";
    private static final String LIQUIBASE_CHANGELOG_TABLE = "DATABASECHANGELOG";
    private static final long DEFAULT_SLEEP_INTERVAL_MS = 300000; // 5 minutes

    private final JdbcTemplate jdbcTemplate;
    private final SchemaConfigurationProperties props;

    /**
     * Waits for a given schema to be "ready" according to Liquibase status.
     * Readiness means:
     * 1. The Liquibase lock is released.
     * 2. A required tag or label (if configured) is present in the changelog.
     *
     * @param schema  The database schema to check.
     * @param timeout The maximum time to wait for readiness.
     * @throws LiquibaseReadinessTimeoutException if the timeout is reached before the schema is ready.
     * @throws InterruptedException               if the waiting thread is interrupted.
     */
    public void awaitReadiness(String schema, Duration timeout) throws InterruptedException {
        log.info("Awaiting Liquibase readiness for schema '{}' (timeout: {})...", schema, timeout);
        long startTime = System.nanoTime();
        long timeoutNanos = timeout.toNanos();

        while (System.nanoTime() - startTime < timeoutNanos) {
            if (isUnlocked(schema) && hasRequiredTagOrLabel(schema)) {
                log.info("Liquibase is ready for schema '{}'.", schema);
                return; // Success
            }
            log.debug("Schema '{}' is not ready yet. Retrying in {}ms...", schema, DEFAULT_SLEEP_INTERVAL_MS);
            Thread.sleep(DEFAULT_SLEEP_INTERVAL_MS);
        }

        // If we exit the loop, it's a timeout
        throw new LiquibaseReadinessTimeoutException("Liquibase readiness check timed out for schema '" + schema + "' after " + timeout);
    }

    public boolean isUnlocked(String schema) {
        if (!props.getReadiness().isCheckLiquibaseLock()) {
            return true;
        }
        String sql = "SELECT LOCKED FROM " + q(schema) + "." + LIQUIBASE_LOCK_TABLE;
        try {
            // queryForObject is better for a single expected value.
            // The query should always return one row. If not, something is wrong with Liquibase setup.
            Boolean locked = jdbcTemplate.queryForObject(sql, Boolean.class);
            if (Boolean.TRUE.equals(locked)) {
                log.warn("Liquibase is locked for schema '{}'.", schema);
                return false;
            }
            return true;
        } catch (EmptyResultDataAccessException e) {
            log.error("Liquibase lock table for schema '{}' seems to be empty or missing. Assuming it's not locked, but this is unusual.", schema);
            return true; // Or false depending on desired safety. True seems reasonable.
        } catch (DataAccessException e) {
            log.error("Failed to check Liquibase lock status for schema '{}'", schema, e);
            return false;
        }
    }

    public boolean hasRequiredTagOrLabel(String schema) {
        String requiredTag = props.getReadiness().getRequiredTag();
        String requiredLabel = props.getReadiness().getRequiredLabel();
        if ((requiredTag == null || requiredTag.isBlank()) && (requiredLabel == null || requiredLabel.isBlank())) {
            return true;
        }

        boolean tagCheckNeeded = requiredTag != null && !requiredTag.isBlank();
        boolean labelCheckNeeded = requiredLabel != null && !requiredLabel.isBlank();

        if (tagCheckNeeded && checkTagExists(schema, requiredTag)) {
            return true;
        }

        if (labelCheckNeeded && checkLabelExists(schema, requiredLabel)) {
            return true;
        }

        // If we reach here, it means checks were needed but failed.
        if (tagCheckNeeded) {
            log.warn("Required Liquibase tag '{}' not found in schema '{}'.", requiredTag, schema);
        }
        if (labelCheckNeeded) {
            log.warn("Required Liquibase label '{}' not found in schema '{}'.", requiredLabel, schema);
        }
        return false;
    }

    private boolean checkTagExists(String schema, String tag) {
        String sql = "SELECT COUNT(*) FROM " + q(schema) + "." + LIQUIBASE_CHANGELOG_TABLE + " WHERE TAG = ?";
        try {
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, tag);
            return count != null && count > 0;
        } catch (DataAccessException e) {
            log.error("Failed to check for Liquibase tag '{}' in schema '{}'", tag, schema, e);
            return false;
        }
    }

    private boolean checkLabelExists(String schema, String label) {
        String sql = "SELECT COUNT(*) FROM " + q(schema) + "." + LIQUIBASE_CHANGELOG_TABLE + " WHERE LABELS LIKE ?";
        try {
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, "%" + label + "%");
            return count != null && count > 0;
        } catch (DataAccessException e) {
            log.error("Failed to check for Liquibase label '{}' in schema '{}'", label, schema, e);
            return false;
        }
    }

    private String q(String ident) {
        // Basic quoting for SQL identifiers. Assumes identifier does not contain quotes.
        return "\"" + ident + "\"";
    }
}