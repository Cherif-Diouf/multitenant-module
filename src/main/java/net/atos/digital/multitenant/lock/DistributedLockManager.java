package net.atos.digital.multitenant.lock;

import java.time.Duration;

/**
 * A manager for acquiring and releasing distributed locks.
 * <p>
 * A distributed lock ensures that only one process in a distributed system
 * can execute a critical section of code at any given time.
 * <p>
 * Implementations can be backed by various technologies like Redis, ZooKeeper,
 * or a database table (e.g., using ShedLock or a custom advisory lock mechanism).
 */
public interface DistributedLockManager {

    /**
     * Attempts to acquire a lock for the given key.
     * <p>
     * This method should be non-blocking or have a very short timeout. It returns
     * a {@link DistributedLock} instance if the lock is successfully acquired,
     * or {@code null} if the lock is already held by another process.
     *
     * @param lockKey The unique key identifying the lock.
     * @param ttl     The Time-To-Live for the lock. This is a safety mechanism to prevent
     *                deadlocks in case a process holding the lock crashes. The lock
     *                should automatically expire after this duration.
     * @return A {@link DistributedLock} instance if the lock was acquired, otherwise {@code null}.
     */
    DistributedLock acquire(String lockKey, Duration ttl);

}