package com.devflow.blog.controller;

import com.devflow.blog.dto.BlogResponses.Category;
import com.devflow.blog.service.CategoryWriteService;
import com.devflow.common.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/categories")
public class CategoryWriteController {
    public record CreateCategory(@NotBlank @Size(max=100) String name) {}
    private final CategoryWriteService service;
    public CategoryWriteController(CategoryWriteService service) { this.service = service; }
    @PostMapping
    public ApiResponse<Category> create(@Valid @RequestBody CreateCategory body) {
        return new ApiResponse<>(service.create(body.name()));
    }
}
