package com.devflow.blog.controller;
import com.devflow.auth.domain.Actor;
import com.devflow.blog.domain.ManagedPost;
import com.devflow.blog.dto.PostWrite;
import com.devflow.blog.service.PostWriteService;
import com.devflow.common.response.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController
public class PostWriteController {
 private final PostWriteService service;
 public PostWriteController(PostWriteService service){this.service=service;}
 @GetMapping("/api/admin/posts") public ApiResponse<List<ManagedPost>> list(){return new ApiResponse<>(service.list(false));}
 @GetMapping("/api/admin/posts/drafts") public ApiResponse<List<ManagedPost>> drafts(){return new ApiResponse<>(service.list(true));}
 @GetMapping("/api/admin/posts/{id}") public ApiResponse<ManagedPost> find(@PathVariable long id){return new ApiResponse<>(service.find(id));}
 @PostMapping({"/api/admin/posts","/api/agent/posts/drafts"})
 public ApiResponse<ManagedPost> create(@Valid @RequestBody PostWrite p,@AuthenticationPrincipal Actor actor){return new ApiResponse<>(service.create(p,actor));}
 @PutMapping({"/api/admin/posts/{id}","/api/agent/posts/drafts/{id}"})
 public ApiResponse<ManagedPost> update(@PathVariable long id,@Valid @RequestBody PostWrite p,@AuthenticationPrincipal Actor actor){return new ApiResponse<>(service.update(id,p,actor));}
 @PatchMapping("/api/admin/posts/{id}/publish") public ApiResponse<ManagedPost> publish(@PathVariable long id){return new ApiResponse<>(service.publish(id,true));}
 @PatchMapping("/api/admin/posts/{id}/unpublish") public ApiResponse<ManagedPost> unpublish(@PathVariable long id){return new ApiResponse<>(service.publish(id,false));}
 @DeleteMapping("/api/admin/posts/{id}") public ApiResponse<Void> delete(@PathVariable long id){service.delete(id);return new ApiResponse<>(null);}
}
