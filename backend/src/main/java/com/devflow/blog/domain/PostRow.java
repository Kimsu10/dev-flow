package com.devflow.blog.domain;

import java.time.OffsetDateTime;

// Column order is explicit in BlogMapper for constructor mapping.
public record PostRow(Long id, String title, String summary, String thumbnailUrl,
                      OffsetDateTime createdAt, OffsetDateTime publishedAt, String contentMarkdown,
                      Long categoryId, String categoryName, String categorySlug, Integer categorySortOrder, String visibility, String status) {}
