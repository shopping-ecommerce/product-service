package iuh.fit.se.configuration;


import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public class FileServiceFeignConfig {

    @Value("${service.tokens.file-service:}")
    private String fileServiceToken; // token máy-máy (ENV/ YAML)

    @Bean
    public RequestInterceptor fileServiceAuthInterceptor() {
        return template -> {
            String bearer = null;
            var attrs = RequestContextHolder.getRequestAttributes();
            if (attrs instanceof ServletRequestAttributes sra && sra.getRequest() != null) {
                bearer = sra.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
            }

            if (bearer != null && !bearer.isBlank()) {
                template.header(HttpHeaders.AUTHORIZATION, bearer);
            } else if (fileServiceToken != null && !fileServiceToken.isBlank()) {
                template.header(HttpHeaders.AUTHORIZATION, "Bearer " + fileServiceToken);
            }
            // nếu cả 2 đều trống -> không set header (file-service dev có thể không yêu cầu auth)
        };
    }
}
