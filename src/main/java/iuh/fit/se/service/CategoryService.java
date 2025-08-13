package iuh.fit.se.service;

import iuh.fit.se.dto.request.CategoryRequest;
import iuh.fit.se.dto.request.CategoryUpdateRequest;
import iuh.fit.se.dto.response.CategoryResponse;

import java.util.List;

public interface CategoryService {
    CategoryResponse create(CategoryRequest categoryRequest);
    CategoryResponse update(CategoryUpdateRequest categoryRequest);
    void delete(String id);
    CategoryResponse getById(String id);
    List<CategoryResponse> getAll();
}
