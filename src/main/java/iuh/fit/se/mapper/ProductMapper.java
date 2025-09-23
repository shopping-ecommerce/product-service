package iuh.fit.se.mapper;

import iuh.fit.se.dto.request.ProductRequest;
import iuh.fit.se.dto.response.ProductResponse;
import iuh.fit.se.entity.Product;
import iuh.fit.se.entity.ProductElastic;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.springframework.data.elasticsearch.core.suggest.Completion;

@Mapper(componentModel = "spring")
public interface ProductMapper {
    // Mapping từ Product (MongoDB) sang ProductResponse
    ProductResponse toProductResponse(Product product);

    // Mapping từ ProductRequest sang Product (MongoDB)
    Product toProduct(ProductRequest productRequest);

    // Mapping từ ProductElastic (Elasticsearch) sang ProductResponse
    // Sử dụng originalName để lấy tên sản phẩm
    @Mapping(source = "originalName", target = "name")
    ProductResponse toProductResponse(ProductElastic productElastic);

    // --- LOGIC MAPPING TỪ PRODUCT SANG PRODUCTELASTIC ---
    // Sử dụng @Named để MapStruct có thể gọi các phương thức mapping tùy chỉnh
    @Mapping(source = "name", target = "nameSuggest", qualifiedByName = "stringToCompletion")
    @Mapping(source = "name", target = "originalName")
    @Mapping(source = "status", target = "status")
    @Mapping(target = "id", source = "id")
    @Mapping(target = "description", source = "description")
    @Mapping(target = "images", source = "images")
    @Mapping(target = "sizes", source = "sizes")
    @Mapping(target = "percentDiscount", source = "percentDiscount")
    @Mapping(target = "categoryId", source = "categoryId")
    @Mapping(target = "sellerId", source = "sellerId")
    @Mapping(target = "createdAt", source = "createdAt") // Added mapping for createdAt
    ProductElastic toProductElastic(Product product);

    // Phương thức default để chuyển đổi từ String sang Completion
    @Named("stringToCompletion")
    default Completion stringToCompletion(String name) {
        if (name == null) {
            return null;
        }
        return new Completion(new String[]{name});
    }
}