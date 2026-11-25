package com.devflow.blog.controller;

import com.devflow.blog.dto.BlogResponses.*;
import com.devflow.blog.service.BlogService;
import com.devflow.common.response.ApiResponse;
import com.devflow.auth.domain.Actor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class BlogController {
    private final BlogService service;
    public BlogController(BlogService service) { this.service = service; }
    @ModelAttribute
    public void preventStalePrivateResponses(HttpServletResponse response) { response.setHeader("Cache-Control", "private, no-store"); }

    @GetMapping("/blog/home")
    public ApiResponse<Home> home() { return new ApiResponse<>(service.home()); }

    @GetMapping("/categories")
    public ApiResponse<List<Category>> categories() { return new ApiResponse<>(service.categories()); }

    @GetMapping("/posts")
    public ApiResponse<PostPage> posts(@RequestParam(required = false) Long categoryId,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "10") int size,
                                      @RequestParam(required = false) String q) {
        return new ApiResponse<>(service.posts(categoryId, page, size, q));
    }
    @GetMapping("/posts/{id}")
    public ApiResponse<PostDetail> post(@PathVariable long id,@AuthenticationPrincipal Actor actor,
                                       @RequestParam(defaultValue="false") boolean preview) {
        return new ApiResponse<>(service.post(id,actor,preview));
    }
    @GetMapping("/posts/{id}/related")
    public ApiResponse<PostPage> related(@PathVariable long id, @AuthenticationPrincipal Actor actor,
                                        @RequestParam(defaultValue="false") boolean preview,
                                        @RequestParam(defaultValue="0") int page,
                                        @RequestParam(defaultValue="3") int size) {
        return new ApiResponse<>(service.related(id, actor, preview, page, size));
    }
}
