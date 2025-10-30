package iuh.fit.se.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enable scheduling cho batch jobs
 * Chỉ cần thêm file này là batch job sẽ tự chạy
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}