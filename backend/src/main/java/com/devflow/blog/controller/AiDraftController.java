package com.devflow.blog.controller;

import com.devflow.auth.domain.Actor;
import com.devflow.blog.dto.AiDraftDtos.*;
import com.devflow.blog.service.AiDraftService;
import com.devflow.common.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/ai-drafts")
public class AiDraftController {
    private final AiDraftService service;
    public AiDraftController(AiDraftService service) { this.service = service; }
    @PostMapping public ApiResponse<Draft> generate(@Valid @RequestBody Request request, @AuthenticationPrincipal Actor actor) {
        return new ApiResponse<>(service.generate(request.question(), actor.id()));
    }
    @GetMapping("/{id}") public ApiResponse<Draft> find(@PathVariable String id) { return new ApiResponse<>(service.find(id)); }
}
