package com.devflow.blog.service;

import com.devflow.blog.dto.BlogResponses.Category;
import com.devflow.blog.mapper.CategoryWriteMapper;
import com.devflow.common.exception.ApiException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryWriteService {
    private final CategoryWriteMapper mapper;
    public CategoryWriteService(CategoryWriteMapper mapper) { this.mapper = mapper; }

    @Transactional
    public Category create(String raw) {
        String name = raw == null ? "" : raw.replaceAll("[\\s\\p{Z}]+", " ").strip();
        if (name.isEmpty() || name.length() > 100 || name.codePoints().anyMatch(Character::isISOControl)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CATEGORY", "카테고리 이름은 1~100자로 입력하세요.");
        }
        return Category.from(mapper.createOrFind(name, UUID.randomUUID().toString()));
    }
}
