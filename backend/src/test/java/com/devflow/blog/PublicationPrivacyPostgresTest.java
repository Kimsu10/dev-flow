package com.devflow.blog;

import com.devflow.auth.config.SecurityConfig;
import com.devflow.auth.domain.Actor;
import com.devflow.auth.mapper.AccountMapper;
import com.devflow.blog.dto.AiDraftDtos.*;
import com.devflow.blog.service.AiResearchClient;
import com.fasterxml.jackson.databind.*;
import java.time.OffsetDateTime;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Tag("postgres") @SpringBootTest @AutoConfigureMockMvc(print=MockMvcPrint.NONE) @Transactional
class PublicationPrivacyPostgresTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcTemplate jdbc; @Autowired AccountMapper accounts;
    @MockitoBean AiResearchClient research;
    Actor author, other, reader;
    @BeforeEach void fixture() {
        author=account("ADMIN");other=account("ADMIN");reader=account("USER");
    }
    Actor account(String role) {
        String name="publication-"+UUID.randomUUID();
        return new Actor(accounts.create(name,name+"@example.test","unused-test-hash",role),name,role);
    }
    ResultActions write(MockHttpServletRequestBuilder request,Object body,Actor actor) throws Exception {
        request.with(authentication(SecurityConfig.authentication(actor))).with(csrf());
        if(body!=null)request.contentType("application/json").content(json.writeValueAsString(body));
        return mvc.perform(request);
    }
    ResultActions read(String path,Actor actor) throws Exception {
        var request=get(path);
        if(actor!=null)request.with(authentication(SecurityConfig.authentication(actor)));
        return mvc.perform(request);
    }
    JsonNode data(ResultActions result) throws Exception { return json.readTree(result.andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data"); }
    Map<String,Object> body(String title,String visibility,String date) {
        var p=new HashMap<String,Object>();p.put("title",title);p.put("contentMarkdown","# Body");
        if(visibility!=null)p.put("visibility",visibility);if(date!=null)p.put("publishedAt",date);return p;
    }
    void assertAbsent(JsonNode array,long id) { for(var item:array)assertNotEquals(id,item.path("id").asLong()); }

    @Test void privateContentIsAuthorOnlyAcrossListsUrlsSourcesAndPublicTransitions() throws Exception {
        String title="secret-"+UUID.randomUUID();
        when(research.research("Original question")).thenReturn(new Research("# AI",List.of(new Source("Docs","https://example.com/docs"))));
        String aiId=data(write(post("/api/admin/ai-drafts"),Map.of("question","Original question","publicQuestionAccepted",true),author)).path("id").asText();
        var p=body(title,"PRIVATE","2020-02-03T12:34:56.123456+09:00");p.put("aiDraftId",aiId);
        JsonNode saved=data(write(post("/api/admin/posts"),p,author));long id=saved.path("id").asLong();
        String created=saved.path("createdAt").asText(), date=saved.path("publishedAt").asText();
        read("/api/posts/"+id,author).andExpect(status().isOk()).andExpect(jsonPath("$.data.visibility").value("PRIVATE"))
            .andExpect(header().string("Cache-Control","private, no-store"));
        for(Actor actor:Arrays.asList(null,other,reader))read("/api/posts/"+id,actor).andExpect(status().isNotFound());
        read("/api/admin/posts/"+id,other).andExpect(status().isNotFound());
        read("/api/admin/ai-drafts/"+aiId,other).andExpect(status().isNotFound());
        read("/api/admin/analytics/posts/"+id+"/traffic",other).andExpect(status().isNotFound());
        write(put("/api/admin/posts/"+id),body("stolen",null,null),other).andExpect(status().isNotFound());
        write(delete("/api/admin/posts/"+id),null,other).andExpect(status().isNotFound());
        write(patch("/api/admin/posts/"+id+"/publish"),null,other).andExpect(status().isNotFound());
        assertAbsent(data(read("/api/admin/posts",other)),id);
        assertAbsent(data(read("/api/admin/posts/mine",other)),id);
        assertTrue(data(read("/api/admin/posts/mine",author)).toString().contains(title));
        read("/api/posts?q="+title,null).andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));
        assertFalse(data(read("/api/blog/home",null)).toString().contains(title));
        // Exactly the same save endpoint publishes normal and AI-authored posts; dates are not reset.
        var publish=body(title,"PUBLIC",null);publish.put("version",0);
        JsonNode published=data(write(put("/api/admin/posts/"+id),publish,author));
        assertEquals(created,published.path("createdAt").asText());assertEquals(date,published.path("publishedAt").asText());
        for(Actor actor:Arrays.asList(null,other,reader))read("/api/posts/"+id,actor).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.aiAttribution.question").value("Original question"));
        read("/api/admin/ai-drafts/"+aiId,other).andExpect(status().isOk());
        read("/api/posts?q="+title,null).andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1));
        // Other admins may edit public posts, but cannot take ownership by hiding them.
        write(put("/api/admin/posts/"+id),body(title,"PRIVATE",null),other).andExpect(status().isForbidden());
        var hide=body(title,"PRIVATE",null);hide.put("version",1);
        data(write(put("/api/admin/posts/"+id),hide,author));
        read("/api/posts/"+id,other).andExpect(status().isNotFound());
        read("/api/admin/ai-drafts/"+aiId,other).andExpect(status().isNotFound());
        read("/api/posts?q="+title,null).andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));
        // Explicit My Posts publication transition also preserves the chosen publication timestamp.
        JsonNode again=data(write(patch("/api/admin/posts/"+id+"/publish?version=2"),null,author));
        assertEquals(date,again.path("publishedAt").asText());assertEquals(created,again.path("createdAt").asText());
    }

    @Test void omittedDateUsesSaveTimeAndEditsPreserveOrReplaceDateWithoutChangingCreation() throws Exception {
        var before=OffsetDateTime.now().minusSeconds(1);
        var saved=data(write(post("/api/admin/posts"),body("date defaults","PRIVATE",null),author));
        var date=OffsetDateTime.parse(saved.path("publishedAt").asText());
        assertTrue(date.isAfter(before));assertTrue(date.isBefore(OffsetDateTime.now().plusSeconds(1)));
        long id=saved.path("id").asLong();String created=saved.path("createdAt").asText();
        var unchanged=data(write(put("/api/admin/posts/"+id),body("edited","PRIVATE",null),author));
        assertEquals(saved.path("publishedAt"),unchanged.path("publishedAt"));
        String chosen="2016-04-05T06:07:08.123456+09:00";
        var changed=data(write(put("/api/admin/posts/"+id),body("edited again","PUBLIC",chosen),author));
        assertEquals(OffsetDateTime.parse(chosen).toInstant(),OffsetDateTime.parse(changed.path("publishedAt").asText()).toInstant());
        assertEquals(created,changed.path("createdAt").asText());
        data(write(patch("/api/admin/posts/"+id+"/unpublish"),null,author));
        var republished=data(write(patch("/api/admin/posts/"+id+"/publish"),null,author));
        assertEquals(changed.path("publishedAt"),republished.path("publishedAt"));
    }

    @Test void listsAndCountsUsePublicationDateAndExcludePrivateAndFutureArticles() throws Exception {
        String suffix=UUID.randomUUID().toString();
        long category=jdbc.queryForObject("INSERT INTO blog.categories(name,slug) VALUES(?,?) RETURNING id",Long.class,suffix,suffix);
        var newer=body("newer-"+suffix,"PUBLIC","2022-01-01T00:00:00Z");newer.put("categoryId",category);
        long newerId=data(write(post("/api/admin/posts"),newer,author)).path("id").asLong();
        var older=body("older-"+suffix,"PUBLIC","2020-01-01T00:00:00Z");older.put("categoryId",category);
        long olderId=data(write(post("/api/admin/posts"),older,author)).path("id").asLong();
        var secret=body("private-"+suffix,"PRIVATE","2025-01-01T00:00:00Z");secret.put("categoryId",category);
        long secretId=data(write(post("/api/admin/posts"),secret,author)).path("id").asLong();
        var future=body("future-"+suffix,"PUBLIC","2099-01-01T00:00:00Z");future.put("categoryId",category);
        long futureId=data(write(post("/api/admin/posts"),future,author)).path("id").asLong();
        var list=data(read("/api/posts?categoryId="+category,null));
        assertEquals(2,list.path("totalElements").asInt());assertEquals(newerId,list.path("items").get(0).path("id").asLong());
        assertEquals(olderId,list.path("items").get(1).path("id").asLong());
        var related=data(read("/api/posts/"+newerId,null)).path("relatedPosts");
        assertEquals(1,related.size());assertEquals(olderId,related.get(0).path("id").asLong());
        assertAbsent(related,secretId);assertAbsent(related,futureId);
        read("/api/posts/"+futureId,null).andExpect(status().isNotFound());
        var mine=data(read("/api/admin/posts/mine",author));
        assertEquals(futureId,mine.get(0).path("id").asLong());assertEquals(secretId,mine.get(1).path("id").asLong());
    }

    @Test void existingStyleRowsKeepDatesStatusAndDefaultPublicVisibility() throws Exception {
        long id=jdbc.queryForObject("INSERT INTO blog.posts(slug,title,content_markdown,status,created_at,published_at) VALUES(?,?,'body','PUBLISHED','2010-01-01Z','2012-01-01Z') RETURNING id",Long.class,UUID.randomUUID().toString(),"legacy");
        var old=data(read("/api/admin/posts/"+id,author));
        assertEquals("PUBLIC",old.path("visibility").asText());assertEquals("PUBLISHED",old.path("status").asText());
        var updated=data(write(put("/api/admin/posts/"+id),body("legacy edited",null,null),author));
        assertEquals(old.path("createdAt"),updated.path("createdAt"));assertEquals(old.path("publishedAt"),updated.path("publishedAt"));
        assertEquals("PUBLISHED",updated.path("status").asText());
    }
}
