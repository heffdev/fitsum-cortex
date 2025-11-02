package ai.fitsum.cortex.api.advisor;

/**
 * Minimal placeholder for tests after Spring AI 1.0.3 migration.
 * Only provides the SensitivityLevel enum required by unit tests.
 */
public final class SensitivityGuardAdvisor {
    public SensitivityGuardAdvisor(Object ignored) { /* placeholder */ }

    public enum SensitivityLevel {
        NONE,
        PII,
        CONFIDENTIAL
    }
}

