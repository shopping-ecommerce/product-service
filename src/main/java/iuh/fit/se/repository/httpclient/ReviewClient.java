package iuh.fit.se.repository.httpclient;

import iuh.fit.se.configuration.FileServiceFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "review-service",configuration = {FileServiceFeignConfig.class})
public interface ReviewClient {
    @DeleteMapping("/review/by-product/{productId}")
    void deleteByProduct(@PathVariable("productId") String productId);
}
