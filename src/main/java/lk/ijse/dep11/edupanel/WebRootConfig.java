package lk.ijse.dep11.edupanel;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class WebRootConfig {

    @Bean
    public HikariDataSource dataSource(Environment env){
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(env.getRequiredProperty("spring.datasource.url"));
        config.setUsername(env.getRequiredProperty("spring.datasource.username"));
        config.setPassword(env.getRequiredProperty("spring.datasource.password"));
        config.setDriverClassName(env.getRequiredProperty("spring.datasource.driver-class-name"));
        config.setMaximumPoolSize(env.getRequiredProperty("spring.datasource.hikari.maximum-pool-size",
                Integer.class));
        return new HikariDataSource(config);
    }
}
