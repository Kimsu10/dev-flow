package com.devflow.blog.domain;
import java.time.OffsetDateTime;
public record ManagedPost(long id,Long categoryId,String title,String contentMarkdown,String summary,String status,
 Long authorId,String authorName,String authorRole,OffsetDateTime createdAt,OffsetDateTime updatedAt,OffsetDateTime publishedAt,long version) {}
