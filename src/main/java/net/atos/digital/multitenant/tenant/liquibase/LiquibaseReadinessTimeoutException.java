package net.atos.digital.multitenant.tenant.liquibase;

public class LiquibaseReadinessTimeoutException extends RuntimeException {
    public LiquibaseReadinessTimeoutException(String message) {
        super(message);
    }
}