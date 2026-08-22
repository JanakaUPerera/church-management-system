package com.churchmanagement.config;

/**
 * Identifies whether this machine is the designated "primary" client on the
 * LAN — the one responsible for singleton administrative duties (applying
 * schema migrations, running scheduled automatic backups) so they don't run
 * redundantly on every open client at once.
 *
 * <p>Driven by the same {@code db.run-migrations} flag the database setup
 * wizard already writes to this machine's local {@code application.properties}
 * (see {@link AppHome}): {@code false} marks a secondary client. A missing
 * value (dev mode, or a properties file saved before this flag existed)
 * defaults to {@code true} so existing single-machine environments keep
 * working unchanged.</p>
 */
public final class PrimaryMachine {
    private PrimaryMachine() {
    }

    public static boolean isPrimary() {
        return !"false".equalsIgnoreCase(DatabaseConfig.getProperty("db.run-migrations"));
    }
}
