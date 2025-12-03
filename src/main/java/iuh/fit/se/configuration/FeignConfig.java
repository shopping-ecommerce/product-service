package iuh.fit.se.configuration;
import feign.Retryer;
import feign.codec.ErrorDecoder;
import feign.FeignException;
import feign.RetryableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Date;

@Configuration
@Slf4j
public class FeignConfig {

    /**
     * Custom Retryer với exponential backoff
     * - Bắt đầu với 2 giây delay
     * - Tăng gấp đôi mỗi lần retry (exponential)
     * - Tối đa 15 giây giữa các lần retry
     * - Retry tối đa 3 lần
     */
    @Bean
    public Retryer retryer() {
        return new Retryer.Default(
                2000L,     // period: initial interval 2 seconds
                15000L,    // maxPeriod: max interval 15 seconds
                4          // maxAttempts: 4 attempts total (1 initial + 3 retries)
        );
    }

    /**
     * Custom ErrorDecoder để xử lý rate limit errors
     * - HTTP 429 (Too Many Requests) → RetryableException
     * - HTTP 503 (Service Unavailable) → RetryableException
     * - Các lỗi khác → Exception thông thường
     */
    @Bean
    public ErrorDecoder errorDecoder() {
        return (methodKey, response) -> {
            int status = response.status();

            // Rate limit exceeded - retry sau 10 giây
            if (status == 429) {
                log.warn("Rate limit exceeded (429) for {}, will retry with backoff", methodKey);
                return new RetryableException(
                        status,
                        "Rate limit exceeded - Too Many Requests",
                        response.request().httpMethod(),
                        new Date(System.currentTimeMillis() + 10000L), // retry after 10s
                        response.request()
                );
            }

            // Service temporarily unavailable - retry
            if (status == 503) {
                log.warn("Service unavailable (503) for {}, will retry", methodKey);
                return new RetryableException(
                        status,
                        "Service Unavailable",
                        response.request().httpMethod(),
                        new Date(System.currentTimeMillis() + 5000L), // retry after 5s
                        response.request()
                );
            }

            // Timeout errors - retry
            if (status == 504 || status == 408) {
                log.warn("Timeout error ({}) for {}, will retry", status, methodKey);
                return new RetryableException(
                        status,
                        "Timeout error",
                        response.request().httpMethod(),
                        new Date(System.currentTimeMillis() + 3000L), // retry after 3s
                        response.request()
                );
            }

            // Các lỗi khác không retry
            log.error("Non-retryable error {} for {}", status, methodKey);
            return FeignException.errorStatus(methodKey, response);
        };
    }
}