package iuh.fit.se.configuration;

import iuh.fit.se.entity.Category;
import iuh.fit.se.entity.Product;
import iuh.fit.se.entity.ProductElastic;
import iuh.fit.se.mapper.ProductMapper;
import iuh.fit.se.repository.CategoryRepository;
import iuh.fit.se.repository.ProductElasticRepository;
import iuh.fit.se.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class CategoryDataLoader implements CommandLineRunner {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final ProductElasticRepository productElasticRepository;
    private final ProductMapper productMapper;

    @Override
    public void run(String... args) {
        // Step 1: Load default categories if none exist
        if (categoryRepository.count() == 0) {
            List<Category> defaultCategories = List.of(
                    Category.builder().name("Electronics").description("Electronic devices and gadgets").build(),
                    Category.builder().name("Fashion").description("Clothing, shoes and accessories").build(),
                    Category.builder().name("Books").description("Books and educational materials").build(),
                    Category.builder().name("Home").description("Home appliances and furniture").build(),
                    Category.builder().name("Sports").description("Sports equipment and apparel").build(),
                    Category.builder().name("Beauty").description("Beauty and personal care products").build(),
                    Category.builder().name("Toys").description("Toys and games for children").build(),
                    Category.builder().name("Automotive").description("Automotive parts and accessories").build(),
                    Category.builder().name("Health").description("Health and wellness products").build(),
                    Category.builder().name("Grocery").description("Groceries and food items").build(),
                    Category.builder().name("SecondHand").description("Products old").build(),
                    Category.builder().name("All").description("diversity").build()
            );

            categoryRepository.saveAll(defaultCategories);
            log.info("✅ Imported {} default categories into MongoDB", defaultCategories.size());
        } else {
            log.info("ℹ Categories already exist, skipping import.");
        }

        // Step 2: Synchronize products from MongoDB to Elasticsearch
        log.info("Starting product synchronization from MongoDB to Elasticsearch...");

        // Check if Elasticsearch index is empty
        long elasticCount = productElasticRepository.count();
        if (elasticCount > 0) {
//            productElasticRepository.deleteAll();
            log.info("ℹ Elasticsearch index 'products' already contains {} documents, skipping synchronization.", elasticCount);
            return;
        }

        // Step 3: Fetch products from MongoDB in batches and sync to Elasticsearch
        int pageSize = 100; // Adjust batch size based on your needs
        Pageable pageable = PageRequest.of(0, pageSize);
        Page<Product> productPage;
        long totalProducts = productRepository.count();
        log.info("Total products in MongoDB: {}", totalProducts);

        int pageNumber = 0;
        while (true) {
            // Fetch a page of products
            productPage = productRepository.findAll(pageable);
            if (!productPage.hasContent()) {
                break; // No more products to process
            }

            // Map products to ProductElastic and save to Elasticsearch
            List<ProductElastic> elasticProducts = productPage.getContent().stream()
                    .map(productMapper::toProductElastic)
                    .collect(Collectors.toList());

            productElasticRepository.saveAll(elasticProducts);
            log.info("Synced page {}: {} products to Elasticsearch", pageNumber, elasticProducts.size());

            // Move to the next page
            pageable = PageRequest.of(++pageNumber, pageSize);
        }

        log.info("✅ Completed product synchronization. Total products synced: {}", totalProducts);
    }
}