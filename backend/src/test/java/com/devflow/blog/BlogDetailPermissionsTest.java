package com.devflow.blog;

import com.devflow.auth.domain.Actor;
import com.devflow.blog.domain.PostRow;
import com.devflow.blog.mapper.BlogMapper;
import com.devflow.blog.service.AiDraftService;
import com.devflow.blog.service.BlogService;
import com.devflow.common.exception.ApiException;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BlogDetailPermissionsTest {
    final BlogMapper mapper = mock(BlogMapper.class);
    final BlogService service = new BlogService(mapper, mock(AiDraftService.class));
    PostRow row(String status) {
        var now = OffsetDateTime.now();
        return new PostRow(1L, "Title", "", null, now, now, "Body", null, null, null, null, "PUBLIC", status);
    }
    @Test void previewRequiresAdministratorEvenWhenQueryExplicitlyRequestsIt() {
        for (Actor actor : new Actor[] { null, new Actor(2L, "reader", "USER"), new Actor(3L, "agent", "SYSTEM") }) {
            assertThrows(ApiException.class, () -> service.post(1, actor, true));
            verify(mapper).findPost(1, actor == null ? null : actor.id(), false);
        }
        verify(mapper, never()).findPost(anyLong(), any(), eq(true));
    }
    @Test void administratorPreviewUsesManagementReadAndPreservesDraftState() {
        when(mapper.findPost(1, 5L, true)).thenReturn(row("DRAFT"));
        var result = service.post(1, new Actor(5L, "admin", "ADMIN"), true);
        assertEquals("DRAFT", result.status());
        assertTrue(result.canEdit()); assertTrue(result.canDelete());
        verifyNoMoreInteractions(ignoreStubs(mapper));
    }
    @Test void ordinaryDetailDoesNotEnablePreviewAndDoesNotGrantReadersManagement() {
        when(mapper.findPost(1, 2L, false)).thenReturn(row("PUBLISHED"));
        var result = service.post(1, new Actor(2L, "reader", "USER"));
        assertFalse(result.canEdit()); assertFalse(result.canDelete());
        verify(mapper, never()).findPost(anyLong(), any(), eq(true));
    }
    @Test void administratorDoesNotReceiveActionsForAnInaccessiblePrivatePost() {
        assertThrows(ApiException.class, () -> service.post(1, new Actor(5L, "admin", "ADMIN"), true));
    }
}
