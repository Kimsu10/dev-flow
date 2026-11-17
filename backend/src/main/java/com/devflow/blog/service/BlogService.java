package com.devflow.blog.service;

import com.devflow.blog.domain.PostRow;
import com.devflow.blog.dto.BlogResponses.*;
import com.devflow.blog.mapper.BlogMapper;
import com.devflow.common.exception.ApiException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class BlogService {
    private final BlogMapper mapper;
    public BlogService(BlogMapper mapper) { this.mapper = mapper; }

    public Home home() {
        var grouped = new LinkedHashMap<Long, CategorySection>();
        for (PostRow row : mapper.findHomePosts()) {
            var post = PostSummary.from(row);
            grouped.computeIfAbsent(row.categoryId(), id -> new CategorySection(post.category(), new ArrayList<>()))
                   .posts().add(post);
        }
        return new Home(List.copyOf(grouped.values()));
    }
    public List<Category> categories() {
        return mapper.findCategories().stream().map(Category::from).toList();
    }
    public PostPage posts(Long categoryId, int page, int size) {
        if (page < 0 || size < 1 || size > 50 || (categoryId != null && categoryId < 1)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", "페이지는 0 이상, 크기는 1~50, 카테고리 ID는 양수여야 합니다.");
        }
        long total = mapper.countPosts(categoryId);
        var items = mapper.findPosts(categoryId, size, (long) page * size).stream().map(PostSummary::from).toList();
        return new PostPage(items, page, size, total, (total + size - 1) / size);
    }
    public PostDetail post(long id) {
        var row = mapper.findPost(id);
        if (row == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "POST_NOT_FOUND", "게시글을 찾을 수 없습니다.");
        }
        return new PostDetail(PostSummary.from(row), row.contentMarkdown());
    }
}
