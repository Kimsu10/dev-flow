package com.devflow.blog;

import com.devflow.blog.mapper.BlogMapper;
import com.devflow.blog.service.BlogService;
import com.devflow.common.exception.ApiException;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BlogSearchTest {
    private final BlogMapper mapper = mock(BlogMapper.class);
    private final BlogService service = new BlogService(mapper);

    @Test void literalSearchUsesSameFilterForCountAndPage() {
        when(mapper.findPosts(7L, 9, 18L, "%100!%!_!!%")).thenReturn(List.of());
        service.posts(7L, 2, 9, " 100%_! ");
        verify(mapper).countPosts(7L, "%100!%!_!!%");
        verify(mapper).findPosts(7L, 9, 18L, "%100!%!_!!%");
    }

    @Test void blankSearchDoesNotAddSearchFilter() {
        service.posts(null, 0, 10, "  ");
        verify(mapper).countPosts(null, null);
        verify(mapper).findPosts(null, 10, 0L, null);
    }

    @Test void oversizedSearchFailsBeforeDatabaseAccess() {
        assertThrows(ApiException.class, () -> service.posts(null, 0, 10, "a".repeat(101)));
        verifyNoInteractions(mapper);
    }
}
