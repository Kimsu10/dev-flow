package com.devflow.blog.dto;
import jakarta.validation.constraints.*;
public record PostWrite(@NotBlank @Size(max=200) String title,@NotNull @Size(max=1000000) String contentMarkdown,
 @Size(max=500) String summary,@Positive Long categoryId,String status) {}
