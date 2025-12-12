// File mới: src/main/java/iuh/fit/se/configuration/AsyncConfig.java
package iuh.fit.se.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class AsyncConfig {

    @Bean(name = "geminiExecutor")
    public ExecutorService geminiExecutor() {
        // Dùng 5 threads như cũ
        return Executors.newFixedThreadPool(5);
    }
}