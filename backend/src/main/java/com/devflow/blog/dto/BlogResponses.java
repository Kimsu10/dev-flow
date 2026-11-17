package com.devflow.blog.dto;

import com.devflow.blog.domain.CategoryRow;
import com.devflow.blog.domain.PostRow;
import java.time.OffsetDateTime;
import java.util.List;

public final class BlogResponses {
    private BlogResponses() {}

    public record Category(Long id, String name, String slug, int sortOrder) {
        public static Category from(CategoryRow row) {
            return new Category(row.id(), row.name(), row.slug(), row.sortOrder());
        }
    }
    public record PostSummary(Long id, String title, String summary, String thumbnailUrl,
                              OffsetDateTime createdAt, OffsetDateTime publishedAt, Category category) {
        public static PostSummary from(PostRow row) {
            Category category = row.categoryId() == null ? null
                : new Category(row.categoryId(), row.categoryName(), row.categorySlug(), row.categorySortOrder());
            return new PostSummary(row.id(), row.title(), row.summary(), row.thumbnailUrl(),
                                   row.createdAt(), row.publishedAt(), category);
        }
    }
    public record CategorySection(Category category, List<PostSummary> posts) {}
    public record Home(List<CategorySection> categories) {}
    public record PostPage(List<PostSummary> items, int page, int size, long totalElements, long totalPages) {}
    public record PostDetail(PostSummary post, String contentMarkdown) {}
}
