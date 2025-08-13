package iuh.fit.se.controller;

import iuh.fit.se.dto.request.CategoryRequest;
import iuh.fit.se.dto.request.CategoryUpdateRequest;
import iuh.fit.se.dto.response.ApiResponse;
import iuh.fit.se.dto.response.CategoryResponse;
import iuh.fit.se.service.CategoryService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/categories")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CategoryController {
    CategoryService categoryService;

    @GetMapping
    public ApiResponse<List<CategoryResponse>> getAll() {
        return ApiResponse.<List<CategoryResponse>>builder()
                .code(200)
                .message("Categories retrieved successfully")
                .result(categoryService.getAll())
                .build();
    }

    @GetMapping("/{id}")
    public ApiResponse<CategoryResponse> getById(@PathVariable String id) {
        return ApiResponse.<CategoryResponse>builder()
                .code(200)
                .message("Category retrieved successfully")
                .result(categoryService.getById(id))
                .build();
    }

    @PostMapping("/create")
    public ApiResponse<CategoryResponse> create(@Valid @RequestBody CategoryRequest request) {
        return ApiResponse.<CategoryResponse>builder()
                .code(200)
                .message("Category created successfully")
                .result( categoryService.create(request))
                .build();
    }

    // PUT /api/categories/{id}
    @PostMapping("/update")
    public ApiResponse<CategoryResponse> update(@Valid @RequestBody CategoryUpdateRequest request) {
        return ApiResponse.<CategoryResponse>builder()
                .code(200)
                .message("Category updated successfully")
                .result(categoryService.update(request))
                .build();
    }

    @GetMapping("/delete/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        categoryService.delete(id);
        return ApiResponse.<Void>builder()
                .code(200)
                .message("Category deleted successfully")
                .build();
    }
}
