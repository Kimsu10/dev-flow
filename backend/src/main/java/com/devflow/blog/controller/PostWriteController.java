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
 @GetMapping("/api/admin/posts") public ApiResponse<List<ManagedPost>> list(@AuthenticationPrincipal Actor actor){return new ApiResponse<>(service.list(false,actor));}
 @GetMapping("/api/admin/posts/drafts") public ApiResponse<List<ManagedPost>> drafts(@AuthenticationPrincipal Actor actor){return new ApiResponse<>(service.list(true,actor));}
 @GetMapping("/api/admin/posts/mine") public ApiResponse<List<ManagedPost>> mine(@AuthenticationPrincipal Actor actor){return new ApiResponse<>(service.mine(actor));}
 @GetMapping("/api/admin/posts/{id}") public ApiResponse<ManagedPost> find(@PathVariable long id,@AuthenticationPrincipal Actor actor){return new ApiResponse<>(service.find(id,actor));}
 @PostMapping({"/api/admin/posts","/api/agent/posts/drafts"})
 public ApiResponse<ManagedPost> create(@Valid @RequestBody PostWrite p,@AuthenticationPrincipal Actor actor){rejectPending(p);return new ApiResponse<>(service.create(p,actor));}
 @PutMapping({"/api/admin/posts/{id}","/api/agent/posts/drafts/{id}"})
 public ApiResponse<ManagedPost> update(@PathVariable long id,@Valid @RequestBody PostWrite p,@AuthenticationPrincipal Actor actor){rejectPending(p);return new ApiResponse<>(service.update(id,p,actor));}
 private void rejectPending(PostWrite p){if(p.contentMarkdown().contains(com.devflow.blog.service.PostImageService.PENDING))throw new com.devflow.common.exception.ApiException(org.springframework.http.HttpStatus.BAD_REQUEST,"INVALID_IMAGES","Pending images require multipart save");}
 @PatchMapping("/api/admin/posts/{id}/publish") public ApiResponse<ManagedPost> publish(@PathVariable long id,@RequestParam(required=false) Long version,@AuthenticationPrincipal Actor actor){return new ApiResponse<>(service.publish(id,true,version,actor));}
 @PatchMapping("/api/admin/posts/{id}/unpublish") public ApiResponse<ManagedPost> unpublish(@PathVariable long id,@RequestParam(required=false) Long version,@AuthenticationPrincipal Actor actor){return new ApiResponse<>(service.publish(id,false,version,actor));}
 @DeleteMapping("/api/admin/posts/{id}") public ApiResponse<Void> delete(@PathVariable long id,@AuthenticationPrincipal Actor actor){service.delete(id,actor);return new ApiResponse<>(null);}
}
