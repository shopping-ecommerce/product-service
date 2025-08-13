package iuh.fit.se.mapper;

import iuh.fit.se.dto.request.CategoryRequest;
import iuh.fit.se.dto.response.CategoryResponse;
import iuh.fit.se.entity.Category;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CategoryMapper {
    CategoryResponse toCategoryResponse(Category category);
    Category toCategory(CategoryRequest request);
}
