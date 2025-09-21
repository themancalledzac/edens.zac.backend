package edens.zac.portfolio.backend.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;

@Configuration
class DatabaseInfoLogger {

    private static final Logger log = LoggerFactory.getLogger(DatabaseInfoLogger.class);

    private final Environment environment;
    private final DataSource dataSource;

    DatabaseInfoLogger(Environment environment, DataSource dataSource) {
        this.environment = environment;
        this.dataSource = dataSource;
    }

    @PostConstruct
    void logDatabaseInfo() {
        String[] activeProfiles = environment.getActiveProfiles();
        String profiles = activeProfiles.length == 0 ? "(none)" : String.join(", ", activeProfiles);
        String urlFromEnv = environment.getProperty("spring.datasource.url");
        String username = environment.getProperty("spring.datasource.username");

        // Log environment-configured values
        log.info("Active Spring profiles: {}", profiles);
        log.info("Configured datasource URL: {}", urlFromEnv);
        log.info("Configured datasource username: {}", username);

        // Try to log driver/product actually connected (without exposing sensitive data)
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            String productName = meta.getDatabaseProductName();
            String productVersion = meta.getDatabaseProductVersion();
            String driverName = meta.getDriverName();
            String driverVersion = meta.getDriverVersion();
            log.info("Connected to DB: {} {} | Driver: {} {}", productName, productVersion, driverName, driverVersion);
        } catch (Exception e) {
            log.warn("Could not obtain database metadata for logging: {}", e.getMessage());
        }
    }
}
