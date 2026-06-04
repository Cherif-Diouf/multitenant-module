package net.atos.digital.multitenant.lock;

import liquibase.integration.spring.SpringLiquibase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.atos.digital.multitenant.utils.ProfileConstants;
import net.atos.digital.multitenant.utils.SchemaUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;

/**
 * A distributed lock manager implementation using a shared database table.
 * The lock table itself is expected to be created and managed by Liquibase.
 */
@Slf4j
@RequiredArgsConstructor
@Profile(ProfileConstants.MULTI_SCHEMA_OR_MULTITENANT)
@Component
@ConditionalOnClass({DataSource.class, SpringLiquibase.class})
public class JdbcTableDistributedLockManager implements DistributedLockManager {

    private final JdbcTemplate jdbcTemplate;

    // It's better to get these from application properties for flexibility.
    @Value("${app.lock.schema:public}")
    private String lockSchema;
    @Value("${app.lock.table:app_lock}")
    private String lockTable;

    private static final String INSERT_SQL = "INSERT INTO %s(lock_key, owner_id, expires_at) VALUES(?, ?, ?)";
    private static final String CLEANUP_SQL = "DELETE FROM %s WHERE expires_at < ?";

    private final String ownerId = LockOwner.getOwnerId();

    @Override
    public DistributedLock acquire(String lockKey, Duration ttl) {
        String fqtn = getFullyQualifiedTableName();
        Instant now = Instant.now();
        Instant expiresAt = now.plus(ttl);

        // Clean up expired locks to prevent table bloat and deadlocks.
        try {
            int cleaned = jdbcTemplate.update(CLEANUP_SQL.formatted(fqtn), java.sql.Timestamp.from(now));
            if (cleaned > 0) {
                log.debug("Cleaned up {} expired locks from {}", cleaned, fqtn);
            }
        } catch (DataAccessException e) {
            // Non-critical failure, the process can continue.
            log.warn("Lock cleanup failed on table {}. This is not critical but should be monitored. Error: {}", fqtn, e.getMessage());
        }

        // Attempt to acquire the lock by inserting a new row.
        // The database's primary key constraint on 'lock_key' ensures atomicity.
        try {
            int rowsInserted = jdbcTemplate.update(INSERT_SQL.formatted(fqtn), lockKey, ownerId, java.sql.Timestamp.from(expiresAt));
            if (rowsInserted == 1) {
                log.debug("Lock '{}' acquired by owner '{}'", lockKey, ownerId);
                return new JdbcDistributedLock(jdbcTemplate, fqtn, lockKey, ownerId);
            }
            // This case should ideally not happen if the PK is set up correctly.
            return null;
        } catch (DuplicateKeyException e) {
            // This is the expected exception when the lock is already taken.
            log.debug("Failed to acquire lock '{}', it is already held by another process.", lockKey);
            return null;
        } catch (DataAccessException e) {
            log.error("An unexpected database error occurred while trying to acquire lock '{}'", lockKey, e);
            return null;
        }
    }

    private String getFullyQualifiedTableName() {
        return SchemaUtils.quote(lockSchema) + "." + SchemaUtils.quote(lockTable);
    }

    /**
     * Represents a lock held in the database. Implements AutoCloseable for try-with-resources.
     */
    @RequiredArgsConstructor
    private static class JdbcDistributedLock implements DistributedLock {
        private final JdbcTemplate jdbcTemplate;
        private final String fullyQualifiedTableName;
        private final String lockKey;
        private final String ownerId;

        private static final String DELETE_SQL = "DELETE FROM %s WHERE lock_key = ? AND owner_id = ?";

        @Override
        public void close() {
            try {
                int rowsDeleted = jdbcTemplate.update(DELETE_SQL.formatted(fullyQualifiedTableName), lockKey, ownerId);
                if (rowsDeleted == 1) {
                    log.debug("Lock '{}' released by owner '{}'", lockKey, ownerId);
                } else {
                    log.warn("Attempted to release lock '{}' by owner '{}', but it was not found or already released. This could indicate the lock expired.", lockKey, ownerId);
                }
            } catch (DataAccessException e) {
                log.error("Failed to release lock '{}' for owner '{}'", lockKey, ownerId, e);
                // Do not re-throw, as this is called in a close() context.
            }
        }
    }

    /**
     * Helper to generate a unique identifier for the current application instance.
     */
    private static final class LockOwner {
        static String getOwnerId() {
            try {
                String hostname = InetAddress.getLocalHost().getHostName();
                String processName = ManagementFactory.getRuntimeMXBean().getName();
                return processName + "@" + hostname;
            } catch (UnknownHostException e) {
                log.warn("Could not determine hostname for lock owner ID, falling back to process name only.", e);
                return ManagementFactory.getRuntimeMXBean().getName();
            }
        }
    }
}