package iuh.fit.se.repository;

import iuh.fit.se.entity.ProductElastic;
import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.List;

public interface ProductElasticRepository extends ElasticsearchRepository<ProductElastic, String> {

    // Tìm kiếm sản phẩm theo danh mục
    List<ProductElastic> findByCategoryId(String categoryId);

    // Tìm kiếm sản phẩm theo người bán
    List<ProductElastic> findBySellerId(String sellerId);

    // Tìm kiếm full-text trên originalName và description
    // Đã đổi "name" thành "originalName" cho chính xác
    @Query("{\"multi_match\": {\"query\": \"?0\", \"fields\": [\"originalName\", \"description\"], \"fuzziness\": \"AUTO\"}}")
    List<ProductElastic> searchByNameOrDescription(String query);

    // Gợi ý tìm kiếm (autocomplete) dựa trên name
    // Sửa lại câu query cho đúng với Completion Suggester
//    @Query("{\"suggest\": {\"product-suggest\": {\"prefix\": \"?0\", \"completion\": {\"field\": \"name\"}}}}")
//    List<ProductElastic> suggestByName(String prefix);

    // Xóa phương thức không cần thiết đi để tránh nhầm lẫn
    // List<ProductElastic> findByNameContainingIgnoreCase(String name);
}