package iuh.fit.se.configuration;

import iuh.fit.se.entity.Category;
import iuh.fit.se.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class CategoryDataLoader implements CommandLineRunner {

    private final CategoryRepository categoryRepository;

    @Override
    public void run(String... args) {
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
            System.out.println("✅ Imported default categories into MongoDB");
        } else {
            System.out.println("ℹ Categories already exist, skip importing.");
        }
    }
}
