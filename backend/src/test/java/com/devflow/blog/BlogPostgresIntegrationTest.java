package com.devflow.blog;

import com.devflow.blog.mapper.BlogMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Tag("postgres")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BlogPostgresIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired BlogMapper mapper;
    private long firstCategory;
    private long secondCategory;
    private long emptyCategory;
    private long publishedId;
    private long draftId;
    private long deletedId;
    private long futureId;

    private long category(String name, int order) {
        return jdbc.queryForObject("INSERT INTO blog.categories(name,slug,sort_order) VALUES (?,?,?) RETURNING id",
                                   Long.class, name, "integration-" + UUID.randomUUID(), order);
    }
    private long post(long category, String title, String status, OffsetDateTime date, boolean deleted) {
        return jdbc.queryForObject("""
            INSERT INTO blog.posts(category_id,slug,title,summary,content_markdown,status,published_at,deleted_at)
            VALUES (?,?,?,?,?,?,?,?) RETURNING id
            """, Long.class, category, "integration-" + UUID.randomUUID(), title, "summary", "# Body\n\n```java\nint n = 1;\n```",
            status, date, deleted ? OffsetDateTime.now().minusDays(1) : null);
    }

    @BeforeEach void fixtures() {
        firstCategory = category("Integration first", -100);
        secondCategory = category("Integration second", -90);
        emptyCategory = category("Integration empty", -80);
        OffsetDateTime time = OffsetDateTime.now().minusHours(1);
        post(firstCategory, "oldest", "PUBLISHED", time.minusDays(4), false);
        post(firstCategory, "older", "PUBLISHED", time.minusDays(3), false);
        post(firstCategory, "latest A", "PUBLISHED", time, false);
        publishedId = post(firstCategory, "latest B", "PUBLISHED", time, false);
        post(secondCategory, "single", "PUBLISHED", time.minusDays(1), false);
        draftId = post(firstCategory, "hidden draft", "DRAFT", null, false);
        deletedId = post(firstCategory, "hidden deleted", "PUBLISHED", time, true);
        futureId = post(firstCategory, "hidden future", "PUBLISHED", time.plusDays(10), false);
        post(emptyCategory, "only draft", "DRAFT", null, false);
    }

    private JsonNode response(String path) throws Exception {
        return json.readTree(mvc.perform(get(path)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    @Test void homeOrdersCategoriesAndLimitsEachToThreePublishedPosts() throws Exception {
        JsonNode sections = response("/api/blog/home").path("data").path("categories");
        assertEquals(firstCategory, sections.get(0).path("category").path("id").asLong());
        assertEquals(secondCategory, sections.get(1).path("category").path("id").asLong());
        JsonNode first = sections.get(0).path("posts");
        assertEquals(3, first.size());
        assertEquals(publishedId, first.get(0).path("id").asLong());
        assertEquals("latest B", first.get(0).path("title").asText());
        assertEquals("latest A", first.get(1).path("title").asText());
        assertEquals("older", first.get(2).path("title").asText());
        assertEquals(1, sections.get(1).path("posts").size());
        for (JsonNode section : sections) {
            assertNotEquals(emptyCategory, section.path("category").path("id").asLong());
            assertTrue(section.path("posts").size() <= 3);
            for (JsonNode post : section.path("posts")) assertFalse(post.has("contentMarkdown"));
        }
    }
    @Test void listFiltersCategoryAndHasStablePagination() throws Exception {
        String path = "/api/posts?categoryId=" + firstCategory + "&size=2";
        JsonNode first = response(path + "&page=0").path("data");
        assertEquals(4, first.path("totalElements").asInt());
        assertEquals(2, first.path("totalPages").asInt());
        assertEquals(2, first.path("items").size());
        JsonNode second = response(path + "&page=1").path("data");
        assertEquals("older", second.path("items").get(0).path("title").asText());
        assertEquals("oldest", second.path("items").get(1).path("title").asText());
    }
    @Test void detailIncludesMarkdownAndNullThumbnail() throws Exception {
        JsonNode detail = response("/api/posts/" + publishedId).path("data");
        assertTrue(detail.path("contentMarkdown").asText().contains("```java"));
        assertTrue(detail.path("post").path("thumbnailUrl").isNull());
    }
    @Test void privateDeletedFutureAndMissingPostsReturnNotFound() throws Exception {
        for (long id : new long[]{draftId, deletedId, futureId, Long.MAX_VALUE}) {
            mvc.perform(get("/api/posts/" + id)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("POST_NOT_FOUND"));
        }
    }
    @Test void invalidPaginationAndTypesReturnBadRequest() throws Exception {
        for (String query : new String[]{"page=-1", "size=0", "size=51", "categoryId=-1", "categoryId=invalid"}) {
            mvc.perform(get("/api/posts?" + query)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_PARAMETER"));
        }
    }
    @Test void categoriesIncludeEmptyOnesInConfiguredOrder() throws Exception {
        JsonNode categories = response("/api/categories").path("data");
        assertEquals(firstCategory, categories.get(0).path("id").asLong());
        assertEquals(emptyCategory, categories.get(2).path("id").asLong());
    }
    @Test void emptyCategoryListIsEmptyRatherThanAnError() throws Exception {
        JsonNode page = response("/api/posts?categoryId=" + emptyCategory).path("data");
        assertEquals(0, page.path("totalElements").asInt());
        assertEquals(0, page.path("items").size());
    }
    @Test void homeQueryDoesNotLoadMarkdownBodies() {
        assertTrue(mapper.findHomePosts().stream().allMatch(row -> row.contentMarkdown() == null));
    }

    @Test void searchIsCaseInsensitiveAndOnlyReturnsVisiblePosts() throws Exception {
        JsonNode page = response("/api/posts?categoryId=" + firstCategory + "&q=LATEST&size=1").path("data");
        assertEquals(2, page.path("totalElements").asInt());
        assertEquals(2, page.path("totalPages").asInt());
        assertEquals(publishedId, page.path("items").get(0).path("id").asLong());
        assertEquals(0, response("/api/posts?q=hidden").path("data").path("totalElements").asInt());
    }

    @Test void searchFindsBodyAndTreatsWildcardsLiterally() throws Exception {
        long id = post(firstCategory, "literal 100%_!", "PUBLISHED", OffsetDateTime.now().minusHours(1), false);
        mvc.perform(get("/api/posts").param("q", "100%_!"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1))
            .andExpect(jsonPath("$.data.items[0].id").value(id));
        mvc.perform(get("/api/posts").param("categoryId", String.valueOf(firstCategory)).param("q", "int n"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(5));
        mvc.perform(get("/api/posts").param("q", "a".repeat(101)))
            .andExpect(status().isBadRequest());
    }
}
