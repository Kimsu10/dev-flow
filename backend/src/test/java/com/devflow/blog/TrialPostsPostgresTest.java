package com.devflow.blog;

import com.devflow.auth.config.SecurityConfig;
import com.devflow.auth.domain.Actor;
import com.devflow.auth.mapper.AccountMapper;
import com.devflow.blog.dto.AiDraftDtos.*;
import com.devflow.blog.mapper.TrialAiMapper;
import com.devflow.blog.mapper.PostWriteMapper;
import com.devflow.blog.service.AiResearchClient;
import com.fasterxml.jackson.databind.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Tag("postgres") @SpringBootTest(properties={"TEST_USER_OPENAI_MODEL=gpt-6-luna","TEST_USER_DAILY_AI_REQUESTS=50"}) @AutoConfigureMockMvc(print=MockMvcPrint.NONE)
class TrialPostsPostgresTest {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;@Autowired AccountMapper accounts;@Autowired TrialAiMapper trials;
    @MockitoBean AiResearchClient research;
    @MockitoSpyBean PostWriteMapper posts;
    Actor user,other,admin;
    @BeforeEach void fixture(){user=account("USER");other=account("USER");admin=account("ADMIN");when(research.researchTrial(anyString())).thenReturn(new Research("# 체험용 초안\n\nBody",List.of(new Source("Docs","https://example.com/docs"))));}
    Actor account(String role){String name="trial-"+UUID.randomUUID();return new Actor(accounts.create(name,name+"@example.test","unused",role),name,role);}
    @AfterEach void cleanup(){for(Actor actor:List.of(user,other,admin)){
        jdbc.update("DELETE FROM blog.posts WHERE author_id=?",actor.id());jdbc.update("DELETE FROM blog.ai_drafts WHERE author_id=?",actor.id());
        jdbc.update("DELETE FROM blog.trial_ai_usage WHERE account_id=?",actor.id());jdbc.update("DELETE FROM blog.accounts WHERE id=?",actor.id());
    }}
    ResultActions request(MockHttpServletRequestBuilder request,Object body,Actor actor)throws Exception{
        if(actor!=null)request.with(authentication(SecurityConfig.authentication(actor)));
        request.with(csrf());if(body!=null)request.contentType("application/json").content(json.writeValueAsString(body));return mvc.perform(request);
    }
    JsonNode data(ResultActions result)throws Exception{return json.readTree(result.andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");}
    Map<String,Object> question(){return new HashMap<>(Map.of("question","Trial question","publicQuestionAccepted",true));}
    @Test void publishesOnlyAiInFixedCategoryAndOnlyOwnerCanEditOrDelete()throws Exception{
        var input=question();input.put("model","gpt-6.2-sol");input.put("categoryId",999999);input.put("authorId",other.id());input.put("contentMarkdown","injected body");
        var saved=data(request(post("/api/user/ai-posts"),input,user));long id=saved.path("id").asLong();
        assertEquals(trials.category().id().longValue(),saved.path("categoryId").asLong());assertEquals(user.id(),saved.path("authorId").asLong());
        assertEquals("PUBLIC",saved.path("visibility").asText());assertEquals("PUBLISHED",saved.path("status").asText());assertFalse(saved.path("contentMarkdown").asText().contains("injected"));
        var detail=data(request(get("/api/posts/"+id),null,null));assertEquals("Trial question",detail.path("aiAttribution").path("question").asText());
        assertEquals(1,data(request(get("/api/user/posts"),null,user)).size());assertEquals(0,data(request(get("/api/user/posts"),null,other)).size());
        for(var builder:List.of(get("/api/user/posts/"+id),get("/api/user/posts/"+id+"/ai-draft"),delete("/api/user/posts/"+id)))request(builder,null,other).andExpect(status().isNotFound());
        var edit=new HashMap<String,Object>(Map.of("title","Edited","contentMarkdown","Edited body","summary","Edited summary","version",0,"categoryId",999999,"visibility","PRIVATE","status","DRAFT","aiDraftId",UUID.randomUUID().toString()));
        request(put("/api/user/posts/"+id),edit,other).andExpect(status().isNotFound());
        var changed=data(request(put("/api/user/posts/"+id),edit,user));
        assertEquals("Edited body",changed.path("contentMarkdown").asText());assertEquals(saved.path("categoryId"),changed.path("categoryId"));assertEquals(saved.path("aiDraftId"),changed.path("aiDraftId"));assertEquals(saved.path("publishedAt"),changed.path("publishedAt"));assertEquals("PUBLIC",changed.path("visibility").asText());
        request(put("/api/user/posts/"+id),edit,user).andExpect(status().isConflict());
        request(delete("/api/user/posts/"+id),null,user).andExpect(status().isOk());request(get("/api/posts/"+id),null,null).andExpect(status().isNotFound());
        verify(research).researchTrial("Trial question");verify(research,never()).research(anyString());
    }
    @Test void userCannotUseAdminCreationCategoryOrDraftEndpointsAndRequestsRequireConsentCsrf()throws Exception{
        request(post("/api/admin/posts"),Map.of("title","Manual","contentMarkdown","body"),user).andExpect(status().isForbidden());
        request(post("/api/admin/ai-drafts"),question(),user).andExpect(status().isForbidden());
        request(post("/api/admin/categories"),Map.of("name","other"),user).andExpect(status().isForbidden());
        request(post("/api/user/ai-posts"),question(),admin).andExpect(status().isForbidden());request(post("/api/user/ai-posts"),question(),null).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/user/ai-posts").with(authentication(SecurityConfig.authentication(user))).contentType("application/json").content(json.writeValueAsBytes(question()))).andExpect(status().isForbidden());
        var input=question();input.put("publicQuestionAccepted",false);request(post("/api/user/ai-posts"),input,user).andExpect(status().isBadRequest());
        input.put("publicQuestionAccepted",true);input.put("question","x".repeat(1001));request(post("/api/user/ai-posts"),input,user).andExpect(status().isBadRequest());
        var policy=data(request(get("/api/user/ai-policy"),null,user));assertEquals("gpt-6-luna",policy.path("model").asText());assertEquals("체험용",policy.path("category").path("name").asText());assertEquals(50,policy.path("dailyRequests").asInt());
        verifyNoInteractions(research);
    }
    @Test void failedResearchDoesNotConsumeAllowanceAndExactlyFiftySuccessfulPublicationsAreAllowed()throws Exception{
        when(research.researchTrial(anyString())).thenThrow(new com.devflow.common.exception.ApiException(org.springframework.http.HttpStatus.BAD_GATEWAY,"AI_UNAVAILABLE","test"));
        for(int i=0;i<4;i++)request(post("/api/user/ai-posts"),question(),user).andExpect(status().isBadGateway());
        assertEquals(0,trials.used(user.id()));
        doReturn(new Research("# Original story\n\nBody",List.of())).when(research).researchTrial(anyString());
        for(int i=0;i<50;i++)request(post("/api/user/ai-posts"),question(),user).andExpect(status().isOk());
        assertEquals(50,trials.used(user.id()));
        request(post("/api/user/ai-posts"),question(),user).andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.error.code").value("TRIAL_DAILY_LIMIT"));
        verify(research,times(54)).researchTrial(anyString());
    }
    @Test void concurrentCountersCannotExceedFifty()throws Exception{
        seedUsage(49,0);
        try(var pool=Executors.newFixedThreadPool(8)){
            var tasks=new ArrayList<Future<Integer>>();for(int i=0;i<8;i++)tasks.add(pool.submit(()->trials.consume(user.id(),50)));
            int accepted=0;for(var task:tasks)if(task.get(15,TimeUnit.SECONDS)!=null)accepted++;
            assertEquals(1,accepted);assertEquals(50,trials.used(user.id()));
        }
    }
    void seedUsage(int count,int daysAgo){jdbc.update("INSERT INTO blog.trial_ai_usage(account_id,usage_date,requests) VALUES(?,(CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul')::date-?,?)",user.id(),daysAgo,count);}
    @Test void lastSlotIsCommittedAtomicallyWithThePostWhenTwoRequestsRace()throws Exception{
        seedUsage(49,0);var started=new CountDownLatch(2);
        when(research.researchTrial(anyString())).thenAnswer(call->{started.countDown();assertTrue(started.await(10,TimeUnit.SECONDS));return new Research("# Racing story\n\nBody",List.of());});
        try(var pool=Executors.newFixedThreadPool(2)){
            var tasks=new ArrayList<Future<Integer>>();for(int i=0;i<2;i++)tasks.add(pool.submit(()->request(post("/api/user/ai-posts"),question(),user).andReturn().getResponse().getStatus()));
            var statuses=new ArrayList<Integer>();for(var task:tasks)statuses.add(task.get(20,TimeUnit.SECONDS));Collections.sort(statuses);
            assertEquals(List.of(200,429),statuses);assertEquals(50,trials.used(user.id()));
            assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM blog.posts WHERE author_id=?",Integer.class,user.id()));
        }
    }
    @Test void failedPostSaveRollsBackTheUsageIncrementAndAllowsRetry()throws Exception{
        doThrow(new com.devflow.common.exception.ApiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"TEST_WRITE_FAILED","test"))
            .when(posts).create(anyString(),any(),eq("PUBLISHED"),eq(user.id()));
        request(post("/api/user/ai-posts"),question(),user).andExpect(status().isServiceUnavailable());
        assertEquals(0,trials.used(user.id()));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM blog.posts WHERE author_id=?",Integer.class,user.id()));
        reset(posts);
        request(post("/api/user/ai-posts"),question(),user).andExpect(status().isOk());assertEquals(1,trials.used(user.id()));
    }
    @Test void previousKoreanDayDoesNotReduceTodaysAllowance()throws Exception{
        seedUsage(50,1);assertEquals(0,trials.used(user.id()));
        request(post("/api/user/ai-posts"),question(),user).andExpect(status().isOk());assertEquals(1,trials.used(user.id()));
        assertEquals(50,jdbc.queryForObject("SELECT requests FROM blog.trial_ai_usage WHERE account_id=? AND usage_date=(CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul')::date-1",Integer.class,user.id()));
    }
    @Test void userCanPublishOriginalAiWritingWithoutSourcesAndRetainAttributionOnEdit()throws Exception{
        when(research.researchTrial(anyString())).thenReturn(new Research("# Original story\n\nRabbit and goose play together.",List.of()));
        var saved=data(request(post("/api/user/ai-posts"),question(),user));long id=saved.path("id").asLong();
        var draft=data(request(get("/api/user/posts/"+id+"/ai-draft"),null,user));
        assertEquals("Trial question",draft.path("question").asText());assertTrue(draft.path("sources").isArray());assertEquals(0,draft.path("sources").size());
        data(request(put("/api/user/posts/"+id),Map.of("title","Edited story","contentMarkdown","Edited original story","version",0),user));
        var published=data(request(get("/api/posts/"+id),null,null));
        assertEquals("Edited original story",published.path("contentMarkdown").asText());
        assertEquals(saved.path("aiDraftId"),published.path("aiAttribution").path("draftId"));
        assertEquals("Trial question",published.path("aiAttribution").path("question").asText());
        assertTrue(published.path("aiAttribution").path("sources").isArray());assertEquals(0,published.path("aiAttribution").path("sources").size());
    }
}
