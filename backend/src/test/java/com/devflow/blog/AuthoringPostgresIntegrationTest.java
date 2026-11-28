package com.devflow.blog;

import com.devflow.auth.config.SecurityConfig;
import com.devflow.auth.domain.Actor;
import com.devflow.auth.mapper.AccountMapper;
import com.devflow.blog.dto.AiDraftDtos.*;
import com.devflow.blog.service.AiResearchClient;
import com.fasterxml.jackson.databind.*;
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
class AuthoringPostgresIntegrationTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcTemplate jdbc; @Autowired AccountMapper accounts;
    @MockitoBean AiResearchClient research;
    Actor admin, user;
    @BeforeEach void fixture() {
        String name="authoring-"+UUID.randomUUID();
        long id=accounts.create(name,name+"@example.test","unused-test-hash","ADMIN"); admin=new Actor(id,name,"ADMIN");
        String username="reader-"+UUID.randomUUID();
        long uid=accounts.create(username,username+"@example.test","unused-test-hash","USER"); user=new Actor(uid,username,"USER");
    }
    ResultActions write(MockHttpServletRequestBuilder request,Object body,Actor actor) throws Exception {
        request.with(authentication(SecurityConfig.authentication(actor))).with(csrf());
        if(body!=null) request.contentType("application/json").content(json.writeValueAsString(body));
        return mvc.perform(request);
    }
    JsonNode data(MvcResult result) throws Exception { return json.readTree(result.getResponse().getContentAsString()).path("data"); }

    @Test void categoryCreationReusesNormalizedNameAndRequiresAdminAndCsrf() throws Exception {
        String name="Authoring "+UUID.randomUUID();
        long id=data(write(post("/api/admin/categories"),Map.of("name","  "+name+"  "),admin).andExpect(status().isOk()).andReturn()).path("id").asLong();
        write(post("/api/admin/categories"),Map.of("name",name.toUpperCase(Locale.ROOT).replace(" ","  ")),admin)
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(id));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM blog.categories WHERE blog.category_name_key(name)=blog.category_name_key(?)",Integer.class,name));
        write(post("/api/admin/categories"),Map.of("name","   "),admin).andExpect(status().isBadRequest());
        write(post("/api/admin/categories"),Map.of("name",name),user).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/categories").with(authentication(SecurityConfig.authentication(admin))).contentType("application/json").content("{\"name\":\"Blocked\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/categories").with(csrf()).contentType("application/json").content("{\"name\":\"Blocked\"}")).andExpect(status().isUnauthorized());
    }

    @Test void appliedProvenanceSurvivesSaveEditAndPublicationAndLegacyWrites() throws Exception {
        String question="원래 질문 <script>not HTML</script>";
        when(research.research(question)).thenReturn(new Research("# 조사 초안",List.of(new Source("공식 자료","https://example.com/docs"))));
        write(post("/api/admin/ai-drafts"),Map.of("question",question,"publicQuestionAccepted",false),admin).andExpect(status().isBadRequest());
        verifyNoInteractions(research);
        var draft=data(write(post("/api/admin/ai-drafts"),Map.of("question",question,"publicQuestionAccepted",true),admin).andExpect(status().isOk()).andReturn());
        String draftId=draft.path("id").asText();
        // Generating a preview creates no post and publishes no question.
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM blog.posts WHERE ai_draft_id=CAST(? AS uuid)",Integer.class,draftId));
        var body=new HashMap<String,Object>(Map.of("title","AI edited post","contentMarkdown","# 저자가 편집한 본문","aiDraftId",draftId));
        var created=data(write(post("/api/admin/posts"),body,admin).andExpect(status().isOk()).andReturn()); long id=created.path("id").asLong();
        assertEquals(draftId,created.path("aiDraftId").asText());
        mvc.perform(get("/api/posts/"+id)).andExpect(status().isNotFound());
        // Old API clients omitting aiDraftId must not erase attribution on update.
        body.remove("aiDraftId");body.put("contentMarkdown","# 다시 수정한 본문");body.put("version",0);
        write(put("/api/admin/posts/"+id),body,admin).andExpect(status().isOk()).andExpect(jsonPath("$.data.aiDraftId").value(draftId));
        mvc.perform(get("/api/admin/ai-drafts/"+draftId).with(authentication(SecurityConfig.authentication(admin))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.question").value(question)).andExpect(jsonPath("$.data.sources[0].url").value("https://example.com/docs"));
        write(patch("/api/admin/posts/"+id+"/publish?version=1"),null,admin).andExpect(status().isOk());
        mvc.perform(get("/api/posts/"+id)).andExpect(status().isOk()).andExpect(jsonPath("$.data.contentMarkdown").value("# 다시 수정한 본문"))
            .andExpect(jsonPath("$.data.aiAttribution.question").value(question)).andExpect(jsonPath("$.data.aiAttribution.sources[0].url").value("https://example.com/docs"));
        var direct=data(write(post("/api/admin/posts"),Map.of("title","Direct post","contentMarkdown","Manual"),admin).andExpect(status().isOk()).andReturn());
        write(patch("/api/admin/posts/"+direct.path("id").asLong()+"/publish"),null,admin).andExpect(status().isOk());
        mvc.perform(get("/api/posts/"+direct.path("id").asLong())).andExpect(status().isOk()).andExpect(jsonPath("$.data.aiAttribution").isEmpty());
        verify(research,times(1)).research(question);
    }

    @Test void aiEndpointsRejectUnauthorizedRolesAndInvalidDraftReferences() throws Exception {
        var body=Map.of("question","question","publicQuestionAccepted",true);
        write(post("/api/admin/ai-drafts"),body,user).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/ai-drafts").with(csrf()).contentType("application/json").content(json.writeValueAsString(body))).andExpect(status().isUnauthorized());
        write(post("/api/admin/posts"),Map.of("title","bad","contentMarkdown","body","aiDraftId","not-a-uuid"),admin).andExpect(status().isBadRequest());
        write(post("/api/admin/posts"),Map.of("title","missing","contentMarkdown","body","aiDraftId",UUID.randomUUID().toString()),admin).andExpect(status().isNotFound());
        verifyNoInteractions(research);
    }

    @Test void originalAiWritingKeepsQuestionAndEmptySourcesThroughPublicationAndEditing() throws Exception {
        String question="토끼와 거위의 창작 이야기";
        when(research.research(question)).thenReturn(new Research("# 함께 노는 친구들\n\n창작 이야기",List.of()));
        var draft=data(write(post("/api/admin/ai-drafts"),Map.of("question",question,"publicQuestionAccepted",true),admin).andExpect(status().isOk()).andReturn());
        assertTrue(draft.path("sources").isArray());assertEquals(0,draft.path("sources").size());
        String draftId=draft.path("id").asText();
        var saved=data(write(post("/api/admin/posts"),Map.of("title","창작 이야기","contentMarkdown","창작 본문","aiDraftId",draftId,"visibility","PUBLIC"),admin).andExpect(status().isOk()).andReturn());
        long id=saved.path("id").asLong();
        write(put("/api/admin/posts/"+id),Map.of("title","편집한 창작 이야기","contentMarkdown","수정한 본문","version",0),admin).andExpect(status().isOk());
        mvc.perform(get("/api/admin/ai-drafts/"+draftId).with(authentication(SecurityConfig.authentication(admin))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.question").value(question)).andExpect(jsonPath("$.data.sources").isEmpty());
        mvc.perform(get("/api/posts/"+id)).andExpect(status().isOk()).andExpect(jsonPath("$.data.contentMarkdown").value("수정한 본문"))
            .andExpect(jsonPath("$.data.aiAttribution.draftId").value(draftId)).andExpect(jsonPath("$.data.aiAttribution.question").value(question))
            .andExpect(jsonPath("$.data.aiAttribution.sources").isArray()).andExpect(jsonPath("$.data.aiAttribution.sources").isEmpty());
    }
}
