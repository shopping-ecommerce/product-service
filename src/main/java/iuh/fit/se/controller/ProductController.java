package iuh.fit.se.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import iuh.fit.se.dto.request.ProductRequest;
import iuh.fit.se.dto.request.ProductUpdateRequest;
import iuh.fit.se.dto.request.SearchSizeAndIDRequest;
import iuh.fit.se.dto.response.ApiResponse;
import iuh.fit.se.dto.response.OrderItemProductResponse;
import iuh.fit.se.dto.response.ProductResponse;
import iuh.fit.se.service.ProductService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
@RequiredArgsConstructor
public class ProductController {
    ProductService productService;
    ObjectMapper objectMapper;

    @PostMapping("/search")
    public ApiResponse<ProductResponse> searchById(@RequestParam("id") String id){
        log.info("Searching for product with ID: {}", id);
        return ApiResponse.<ProductResponse>builder()
                .code(200)
                .message("Product found")
                .result(productService.findById(id))
                .build();
    }

    @PostMapping("/searchBySeller")
    public ApiResponse<List<ProductResponse>> searchBySeller(@RequestParam("sellerId") String sellerId) {
        log.info("Searching for products by seller ID: {}", sellerId);
        return ApiResponse.<List<ProductResponse>>builder()
                .code(200)
                .message("Products found for seller")
                .result(productService.findAllBySellerId(sellerId))
                .build();
    }

    @PostMapping("/searchBySizeAndID")
    public ApiResponse<OrderItemProductResponse> searchBySizeAndID(@Valid @RequestBody SearchSizeAndIDRequest request) {
        log.info("Searching for products with request: {}", request);
        return ApiResponse.<OrderItemProductResponse>builder()
                .code(200)
                .message("Products found by size and ID")
                .result(productService.findByIdAndSize(request))
                .build();
    }

    @GetMapping("/getProducts")
    public ApiResponse<List<ProductResponse>> getAllProducts() {
        log.info("Fetching all products");
        return ApiResponse.<List<ProductResponse>>builder()
                .code(200)
                .message("Products fetched successfully")
                .result(productService.findAllProducts())
                .build();
    }

    @PreAuthorize("hasAuthority('CREATE_PRODUCT')")
    @PostMapping(value = "/create",consumes = {MediaType.MULTIPART_FORM_DATA_VALUE,MediaType.APPLICATION_JSON_VALUE})
    public ApiResponse<ProductResponse> createProduct(
            // nhận chuỗi để tránh lỗi content-type
            @RequestParam("product") String data,
            @RequestPart(value = "images") List<MultipartFile> files
    ) throws JsonProcessingException {
        ProductRequest req = objectMapper.readValue(data, ProductRequest.class);
        log.info("Received product request: {}", req);
        return ApiResponse.<ProductResponse>builder()
                .code(200)
                .message("Product created successfully")
                .result(productService.createProduct(req,files))
                .build();
    }

    @PostMapping("/update")
    @PreAuthorize("hasAuthority('UPDATE_PRODUCT')")
    public ApiResponse<ProductResponse> updateProduct(
            @RequestParam("product") String data,
            @RequestPart(value = "images" ,required = false) List<MultipartFile> files
    ) throws JsonProcessingException {
        ProductUpdateRequest req = objectMapper.readValue(data,ProductUpdateRequest.class);
        log.info("Updating product with request: {}", req);
        return ApiResponse.<ProductResponse>builder()
                .code(200)
                .message("Product updated successfully")
                .result(productService.updateProduct(req, files))
                .build();
    }
}