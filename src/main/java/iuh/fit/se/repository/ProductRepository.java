package iuh.fit.se.repository;

import iuh.fit.se.entity.Product;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ProductRepository extends MongoRepository<Product,String> {
    List<Product> findBySellerId(String sellerId);

    List<Product> findByCategoryId(String categoryId);

    List<Product> findByNameContainingIgnoreCase(String name);

}
