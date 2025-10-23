package iuh.fit.se.service;

import iuh.fit.event.dto.OrderCreatedEvent;
import iuh.fit.event.dto.OrderStatusChangedEvent;
import iuh.fit.event.dto.ProductInvalid;
import iuh.fit.se.dto.request.ProductRequest;
import iuh.fit.se.dto.request.ProductUpdateRequest;
import iuh.fit.se.dto.request.SearchSizeAndIDRequest;
import iuh.fit.se.dto.response.OrderItemProductResponse;
import iuh.fit.se.dto.response.ProductResponse;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

public interface ProductService {
    ProductResponse findById(String id);
    ProductResponse createProduct(ProductRequest request, List<MultipartFile> images);
    ProductResponse updateProduct(ProductUpdateRequest request, List<MultipartFile> images);
    void deleteProduct(ProductInvalid productInvalid);
    List<ProductResponse> findAllByCategory(String category);
    List<ProductResponse> findAllProducts();
    List<ProductResponse> findAllBySellerId(String sellerId);

    void deleteProductBySeller(ProductInvalid productInvalid);

    OrderItemProductResponse findByIdAndSize(SearchSizeAndIDRequest request);

    void updateStockFromOrder(OrderCreatedEvent event);
    void restoreStockFromOrder(OrderStatusChangedEvent event);

    //Elasticsearch
    List<ProductResponse> searchProducts(String query);

    List<String> suggestProducts(String prefix) throws IOException;
    void discontinueBySellerId(String sellerId, String reason);
}
