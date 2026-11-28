package com.devflow.blog.controller;
import com.devflow.auth.domain.Actor;
import com.devflow.blog.domain.ManagedPost;
import com.devflow.blog.dto.AiDraftDtos.Draft;
import com.devflow.blog.service.*;
import com.devflow.common.response.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/user")
public class TrialPostController {
    private final TrialPostService service;
    public TrialPostController(TrialPostService service){this.service=service;}
    @GetMapping("/ai-policy") public ApiResponse<TrialAiPolicy.Policy> policy(@AuthenticationPrincipal Actor actor){return new ApiResponse<>(service.policy(actor));}
    @PostMapping("/ai-posts") public ApiResponse<ManagedPost> publish(@Valid @RequestBody TrialPostService.Publish input,@AuthenticationPrincipal Actor actor){return new ApiResponse<>(service.publish(input,actor));}
    @GetMapping("/posts") public ApiResponse<List<ManagedPost>> mine(@AuthenticationPrincipal Actor actor){return new ApiResponse<>(service.mine(actor));}
    @GetMapping("/posts/{id}") public ApiResponse<ManagedPost> find(@PathVariable long id,@AuthenticationPrincipal Actor actor){return new ApiResponse<>(service.find(id,actor));}
    @GetMapping("/posts/{id}/ai-draft") public ApiResponse<Draft> attribution(@PathVariable long id,@AuthenticationPrincipal Actor actor){return new ApiResponse<>(service.attribution(id,actor));}
    @PutMapping("/posts/{id}") public ApiResponse<ManagedPost> edit(@PathVariable long id,@Valid @RequestBody TrialPostService.Edit input,@AuthenticationPrincipal Actor actor){return new ApiResponse<>(service.edit(id,input,actor));}
    @DeleteMapping("/posts/{id}") public ApiResponse<Void> delete(@PathVariable long id,@AuthenticationPrincipal Actor actor){service.delete(id,actor);return new ApiResponse<>(null);}
}
