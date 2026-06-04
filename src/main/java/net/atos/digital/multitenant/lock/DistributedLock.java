package net.atos.digital.multitenant.lock;

/**
 * Represents an acquired distributed lock that must be released.
 * <p>
 * This interface extends {@link AutoCloseable}, allowing it to be used
 * in a try-with-resources statement to ensure the lock is always released.
 * <pre>{@code
 * try (DistributedLock lock = lockManager.acquire("my-lock-key", Duration.ofMinutes(5))) {
 *     if (lock != null) {
 *         // ... critical section ...
 *     }
 * }
 * }</pre>
 */
public interface DistributedLock extends AutoCloseable {

    /**
     * Releases the lock. This method is automatically called when the lock
     * is used in a try-with-resources statement.
     */
    @Override
    void close();
}