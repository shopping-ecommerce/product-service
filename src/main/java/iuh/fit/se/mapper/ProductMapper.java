package iuh.fit.se.mapper;

import iuh.fit.se.dto.request.ProductRequest;
import iuh.fit.se.dto.response.ProductResponse;
import iuh.fit.se.entity.Product;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ProductMapper {
    ProductResponse toProductResponse(Product product);
    Product toProduct(ProductRequest productRequest);
}
