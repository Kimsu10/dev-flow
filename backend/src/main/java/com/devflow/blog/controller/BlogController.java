package com.devflow.blog.controller;

import com.devflow.blog.dto.BlogResponses.*;
import com.devflow.blog.service.BlogService;
import com.devflow.common.response.ApiResponse;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class BlogController {
    private final BlogService service;
    public BlogController(BlogService service) { this.service = service; }

    @GetMapping("/blog/home")
    public ApiResponse<Home> home() { return new ApiResponse<>(service.home()); }

    @GetMapping("/categories")
    public ApiResponse<List<Category>> categories() { return new ApiResponse<>(service.categories()); }

    @GetMapping("/posts")
    public ApiResponse<PostPage> posts(@RequestParam(required = false) Long categoryId,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "10") int size) {
        return new ApiResponse<>(service.posts(categoryId, page, size));
    }
    @GetMapping("/posts/{id}")
    public ApiResponse<PostDetail> post(@PathVariable long id) { return new ApiResponse<>(service.post(id)); }
}
