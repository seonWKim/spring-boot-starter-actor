package io.github.seonwkim.actor.autoconfigure;

import javax.sql.DataSource;
import org.springframework.boot.jdbc.init.DataSourceScriptDatabaseInitializer;
import org.springframework.boot.jdbc.init.PlatformPlaceholderDatabaseDriverResolver;
import org.springframework.boot.sql.init.DatabaseInitializationMode;
import org.springframework.boot.sql.init.DatabaseInitializationSettings;

/**
 * Creates a module's tables from its schema file for the database in use ({@code @@platform@@} in
 * the location: h2, postgresql, mysql, ...), when {@code mode} allows it. Each module subclasses it,
 * so beans that need the tables can depend on their initializer by type.
 */
public abstract class SchemaScriptInitializer extends DataSourceScriptDatabaseInitializer {

    protected SchemaScriptInitializer(DataSource dataSource, DatabaseInitializationMode mode, String schemaLocation) {
        super(dataSource, settings(dataSource, mode, schemaLocation));
    }

    private static DatabaseInitializationSettings settings(
            DataSource dataSource, DatabaseInitializationMode mode, String schemaLocation) {
        DatabaseInitializationSettings settings = new DatabaseInitializationSettings();
        settings.setMode(mode);
        if (mode != DatabaseInitializationMode.NEVER) {
            // Finding the database's schema file opens a connection: only when it may run.
            settings.setSchemaLocations(
                    new PlatformPlaceholderDatabaseDriverResolver().resolveAll(dataSource, schemaLocation));
        }
        return settings;
    }
}
