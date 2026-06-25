package dio.budgeting.config;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jpa.autoconfigure.EntityManagerFactoryDependsOnPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;

@Configuration(proxyBeanMethods = false)
public class FlywayConfig {

    @Bean(initMethod = "migrate")
    Flyway flyway(DataSource dataSource,
                  @Value("${spring.flyway.locations:classpath:db/migration}") String locations) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations(StringUtils.commaDelimitedListToStringArray(locations))
                .load();
    }

    @Bean
    static EntityManagerFactoryDependsOnPostProcessor entityManagerFactoryDependsOnFlywayPostProcessor() {
        return new EntityManagerFactoryDependsOnPostProcessor(Flyway.class);
    }
}
