package iuh.fit.se.service;

import iuh.fit.se.dto.request.ProductRequest;
import iuh.fit.se.dto.request.ProductUpdateRequest;
import iuh.fit.se.dto.request.SearchSizeAndIDRequest;
import iuh.fit.se.dto.response.OrderItemProductResponse;
import iuh.fit.se.dto.response.ProductResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ProductService {
    ProductResponse findById(String id);
    ProductResponse createProduct(ProductRequest request, List<MultipartFile> images);
    ProductResponse updateProduct(ProductUpdateRequest request, List<MultipartFile> images);
    void deleteProduct(String id);
    List<ProductResponse> findAllByCategory(String category);
    List<ProductResponse> findAllProducts();
    List<ProductResponse> findAllBySellerId(String sellerId);

    OrderItemProductResponse findByIdAndSize(SearchSizeAndIDRequest request);
}
