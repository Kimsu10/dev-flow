package com.devflow.blog.controller;

import com.devflow.auth.domain.Actor;
import com.devflow.blog.domain.ManagedPost;
import com.devflow.blog.dto.PostWrite;
import com.devflow.blog.service.PostImageService;
import com.devflow.common.response.ApiResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class PostImageController {
    private final PostImageService service;
    public PostImageController(PostImageService service){this.service=service;}
    @PostMapping(value="/api/admin/posts/with-images",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ManagedPost> create(@Valid @RequestPart("post") PostWrite post,@RequestPart("imageIds") List<String> ids,@RequestPart("images") List<MultipartFile> images,@AuthenticationPrincipal Actor actor)throws IOException{return new ApiResponse<>(service.save(null,post,ids,images,actor));}
    @PutMapping(value="/api/admin/posts/{id}/with-images",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ManagedPost> update(@PathVariable long id,@Valid @RequestPart("post") PostWrite post,@RequestPart("imageIds") List<String> ids,@RequestPart("images") List<MultipartFile> images,@AuthenticationPrincipal Actor actor)throws IOException{return new ApiResponse<>(service.save(id,post,ids,images,actor));}
    @GetMapping("/api/posts/{postId}/images/{id}")
    public ResponseEntity<byte[]> read(@PathVariable long postId,@PathVariable UUID id,@AuthenticationPrincipal Actor actor){var image=service.read(postId,id.toString(),actor);return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.parseMediaType(image.type())).header("X-Content-Type-Options","nosniff").body(image.bytes());}
}
