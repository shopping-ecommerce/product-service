package iuh.fit.se.repository.httpclient;

import iuh.fit.se.configuration.AuthenticationRequestInterceptor;
import iuh.fit.se.dto.response.ApiResponse;
import iuh.fit.se.dto.response.SellerResponse;
import iuh.fit.se.dto.response.UserResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(
        name = "user-service"
)
public interface UserClient {
    @GetMapping("sellers/searchBySellerId/{sellerId}")
    ApiResponse<SellerResponse> searchBySellerId(@PathVariable("sellerId") String sellerId);
}
