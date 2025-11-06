package iuh.fit.se.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import iuh.fit.event.dto.ProductInvalid;
import iuh.fit.se.dto.request.ProductRequest;
import iuh.fit.se.dto.request.ProductUpdateRequest;
import iuh.fit.se.dto.request.SearchSizeAndIDRequest;
import iuh.fit.se.dto.response.ApiResponse;
import iuh.fit.se.dto.response.OrderItemProductResponse;
import iuh.fit.se.dto.response.ProductResponse;
import iuh.fit.se.entity.enums.Status;
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

import java.io.IOException;
import java.util.List;

@RestController
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
@RequiredArgsConstructor
public class ProductController {
    ProductService productService;
    ObjectMapper objectMapper;

    @GetMapping("/searchByProduct/{productId}")
    public ApiResponse<ProductResponse> searchById(@PathVariable("productId") String productId){
        log.info("Searching for product with ID: {}", productId);
        return ApiResponse.<ProductResponse>builder()
                .code(200)
                .message("Product found")
                .result(productService.findById(productId))
                .build();
    }

    @GetMapping("/searchBySeller/{sellerId}")
    public ApiResponse<List<ProductResponse>> searchBySeller(@PathVariable("sellerId") String sellerId) {
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
            @RequestPart(value = "images",required = true)
            List<MultipartFile> files
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
    @GetMapping("/suggest")
    public ApiResponse<List<String>> suggestProducts(@RequestParam("prefix") String prefix) throws IOException {
        log.info("Fetching product suggestions for prefix: {}", prefix);
        return ApiResponse.<List<String>>builder()
                .code(200)
                .message("Product suggestions fetched successfully")
                .result(productService.suggestProducts(prefix))
                .build();
    }

    @GetMapping("/searchByCategory/{categoryId}")
    public ApiResponse<List<ProductResponse>> searchByCategory(@PathVariable("categoryId") String categoryId) {
        log.info("Searching for products by category ID: {}", categoryId);
        return ApiResponse.<List<ProductResponse>>builder()
                .code(200)
                .message("Products found for category")
                .result(productService.findAllByCategory(categoryId))
                .build();
    }

    @GetMapping("/search")
    public ApiResponse<List<ProductResponse>> searchProducts(@RequestParam("query") String query) {
        log.info("Searching for products with query: {}", query);
        return ApiResponse.<List<ProductResponse>>builder()
                .code(200)
                .message("Products found for search query")
                .result(productService.searchProducts(query))
                .build();
    }
    @PostMapping("/deleteProducts")
    @PreAuthorize("hasAuthority('DELETE_PRODUCT')")
    public ApiResponse<ProductResponse> deleteProducts(
            @RequestParam("sellerId") String sellerId,
            @RequestParam("reason") String reason
    ) {
        log.info("Deleting all products with request: {}", sellerId);
        productService.discontinueBySellerId(sellerId,reason);
        return ApiResponse.<ProductResponse>builder()
                .code(200)
                .message("Products delete successfully")
                .build();
    }

    @PostMapping("/deleteProductBySeller")
    @PreAuthorize("hasAuthority('DELETE_PRODUCT')")
    public ApiResponse<ProductResponse> deleteProductBySeller(
            @RequestParam("productId") String productId,
            @RequestParam("reason") String reason
    ) {
        log.info("Deleting product with request:  {}", productId);
        productService.deleteProductBySeller(ProductInvalid.builder()
                        .productId(productId)
                        .reason(reason)
                .build());
        return ApiResponse.<ProductResponse>builder()
                .code(200)
                .message("Product delete successfully")
                .build();
    }

    @GetMapping("/view/{id}")
    public ApiResponse<Object> deleteProductBySeller(
            @PathVariable("id") String id
    ) {
        log.info("Increase view product with request:  {}", id);
        productService.updateView(id);
        return ApiResponse.<Object>builder()
                .code(200)
                .message("Product increase view successfully")
                .build();
    }

// ==================== NEW ENDPOINTS ====================

    /**
     * Lấy danh sách sản phẩm đang chờ duyệt (PENDING)
     * Dành cho Admin
     */
    @GetMapping("/pending")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<ProductResponse>> getPendingProducts() {
        log.info("Fetching pending products for admin approval");
        return ApiResponse.<List<ProductResponse>>builder()
                .code(200)
                .message("Pending products fetched successfully")
                .result(productService.findAllByStatus(Status.PENDING))
                .build();
    }

    /**
     * Admin duyệt sản phẩm: chuyển từ PENDING sang AVAILABLE hoặc DISCONTINUED
     * @param productId ID sản phẩm
     * @param status Trạng thái mới (AVAILABLE hoặc DISCONTINUED)
     * @param reason Lý do (bắt buộc nếu status = DISCONTINUED)
     */
    @PostMapping("/approve/{productId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<ProductResponse> approveProduct(
            @PathVariable("productId") String productId,
            @RequestParam("status") Status status,
            @RequestParam(value = "reason", required = false) String reason
    ) {
        log.info("Admin approving product {} with status: {}", productId, status);

        if (status != Status.AVAILABLE && status != Status.DISCONTINUED) {
            return ApiResponse.<ProductResponse>builder()
                    .code(400)
                    .message("Status must be AVAILABLE or DISCONTINUED")
                    .build();
        }

        if (status == Status.DISCONTINUED && (reason == null || reason.isBlank())) {
            return ApiResponse.<ProductResponse>builder()
                    .code(400)
                    .message("Reason is required when status is DISCONTINUED")
                    .build();
        }

        return ApiResponse.<ProductResponse>builder()
                .code(200)
                .message("Product approved successfully")
                .result(productService.approveProduct(productId, status, reason))
                .build();
    }

    /**
     * Admin tạm ngưng sản phẩm: chuyển sang SUSPENDED
     * @param productId ID sản phẩm
     * @param reason Lý do tạm ngưng
     */
    @PostMapping("/suspend/{productId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<ProductResponse> suspendProduct(
            @PathVariable("productId") String productId,
            @RequestParam("reason") String reason
    ) {
        log.info("Suspending product {} with reason: {}", productId, reason);
        return ApiResponse.<ProductResponse>builder()
                .code(200)
                .message("Product suspended successfully")
                .result(productService.suspendProduct(productId, reason))
                .build();
    }

    /**
     * Seller đăng ký lại sản phẩm bị DISCONTINUED hoặc SUSPENDED
     * Sản phẩm sẽ chuyển về trạng thái PENDING để chờ admin duyệt lại
     */
// ...
    @PreAuthorize("hasAuthority('UPDATE_PRODUCT')")
    @PostMapping(value = "/reregister", consumes = {MediaType.MULTIPART_FORM_DATA_VALUE, MediaType.APPLICATION_JSON_VALUE})
    public ApiResponse<ProductResponse> reregisterProduct(
            @RequestParam("product") String data,
            @RequestPart(value = "images", required = false) List<MultipartFile> files
    ) throws JsonProcessingException {
        ProductUpdateRequest req = objectMapper.readValue(data, ProductUpdateRequest.class);
        log.info("Reregister product with request: {}", req);
        return ApiResponse.<ProductResponse>builder()
                .code(200)
                .message("Product re-registered successfully and pending for approval")
                .result(productService.reregisterProduct(req, files))
                .build();
    }


    /**
     * Tạm ngưng tất cả sản phẩm của seller (AVAILABLE -> SUSPENDED)
     * @param sellerId ID người bán
     * @param reason Lý do tạm ngưng
     */
    @PostMapping("/suspendAllBySeller")
    @PreAuthorize("hasAuthority('UPDATE_PRODUCT')")
    public ApiResponse<Void> suspendAllProductsBySeller(
            @RequestParam("sellerId") String sellerId,
            @RequestParam("reason") String reason
    ) {
        log.info("Suspending all products of seller {} with reason: {}", sellerId, reason);
        productService.suspendAllProductsBySeller(sellerId, reason);
        return ApiResponse.<Void>builder()
                .code(200)
                .message("All products suspended successfully")
                .build();
    }

    /**
     * Kích hoạt lại tất cả sản phẩm của seller (SUSPENDED -> AVAILABLE)
     * @param sellerId ID người bán
     */
    @PostMapping("/activateAllBySeller")
    @PreAuthorize("hasAuthority('UPDATE_PRODUCT')")
    public ApiResponse<Void> activateAllProductsBySeller(
            @RequestParam("sellerId") String sellerId
    ) {
        log.info("Activating all suspended products of seller {}", sellerId);
        productService.activateAllProductsBySeller(sellerId);
        return ApiResponse.<Void>builder()
                .code(200)
                .message("All suspended products activated successfully")
                .build();
    }

    /**
     * Lấy danh sách sản phẩm theo seller và trạng thái
     * @param sellerId ID người bán
     * @param status Trạng thái sản phẩm
     */
    @GetMapping("/searchBySellerAndStatus")
    public ApiResponse<List<ProductResponse>> searchBySellerAndStatus(
            @RequestParam("sellerId") String sellerId,
            @RequestParam("status") Status status
    ) {
        log.info("Searching for products by seller {} with status: {}", sellerId, status);
        return ApiResponse.<List<ProductResponse>>builder()
                .code(200)
                .message("Products found for seller with status: " + status)
                .result(productService.findBySellerIdAndStatus(sellerId, status))
                .build();
    }

}