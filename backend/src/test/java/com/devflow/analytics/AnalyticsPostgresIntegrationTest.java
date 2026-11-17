package com.devflow.analytics;
import com.devflow.analytics.dto.AnalyticsDtos.*;
import com.devflow.analytics.mapper.*;
import com.devflow.analytics.service.*;
import com.devflow.auth.config.SecurityConfig;
import com.devflow.auth.domain.Actor;
import com.devflow.auth.mapper.AccountMapper;
import com.fasterxml.jackson.databind.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@Tag("postgres") @SpringBootTest @AutoConfigureMockMvc(print=MockMvcPrint.NONE) @Transactional
class AnalyticsPostgresIntegrationTest {
 @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;
 @Autowired VisitService visits;@Autowired AnalyticsService analytics;@Autowired AccountMapper accounts;
 @Autowired org.mybatis.spring.SqlSessionTemplate sqlSession;
 long category,post,admin,user;
 @BeforeEach void setup(){
  String key=UUID.randomUUID().toString();
  category=jdbc.queryForObject("INSERT INTO blog.categories(name,slug) VALUES('analytics-test',?) RETURNING id",Long.class,key);
  post=jdbc.queryForObject("INSERT INTO blog.posts(category_id,slug,title,status,published_at) VALUES(?,?,'analytics-test','PUBLISHED',now()) RETURNING id",Long.class,category,key);
  admin=accounts.create("analytics-admin-"+key,key+"@admin.test","unused-hash","ADMIN");
  user=accounts.create("analytics-user-"+key,key+"@user.test","unused-hash","USER");
 }
 PageView event(String path,Long id){return new PageView(UUID.randomUUID(),path,id,"https://www.google.com/search?q=never-store-this",null,null,null,true);}
 @Test void collectionDeduplicatesAndKeepsFirstSessionAttribution(){
  var a=visits.collect(event("/posts/"+post,post),null,null,"https://blog.test");assertTrue(a.counted());
  var duplicate=visits.collect(event("/posts/"+post,post),a.visitor().toString(),a.session().toString(),"https://blog.test");assertFalse(duplicate.counted());
  var e=event("/",null);var b=visits.collect(e,a.visitor().toString(),a.session().toString(),"https://blog.test");assertTrue(b.counted());
  assertEquals(a.session(),b.session());
  assertFalse(visits.collect(e,null,null,"https://blog.test").counted());
  assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM blog.page_view_events WHERE session_id=?",Integer.class,a.session()));
  assertEquals("www.google.com",jdbc.queryForObject("SELECT referrer_domain FROM blog.visitor_sessions WHERE id=?",String.class,a.session()));
  assertEquals("GOOGLE",jdbc.queryForObject("SELECT source FROM blog.visitor_sessions WHERE id=?",String.class,a.session()));
  assertEquals(category,jdbc.queryForObject("SELECT category_id FROM blog.page_view_events WHERE session_id=? AND post_id=?",Long.class,a.session(),post));
  jdbc.update("UPDATE blog.visitor_sessions SET last_seen_at=now()-interval '31 minutes' WHERE id=?",a.session());sqlSession.clearCache();
  var next=visits.collect(event("/posts",null),a.visitor().toString(),a.session().toString(),"https://blog.test");assertNotEquals(a.session(),next.session());
 }
 @Test void hiddenPostsDoNotCountAndAnalyticsOnlyAllowsAdmin()throws Exception {
  jdbc.update("UPDATE blog.posts SET status='DRAFT' WHERE id=?",post);
  assertFalse(visits.collect(event("/posts/"+post,post),null,null,"https://blog.test").counted());
  mvc.perform(get("/api/admin/analytics/overview")).andExpect(status().isUnauthorized());
  var ordinary=SecurityConfig.authentication(new Actor(user,"user","USER"));
  mvc.perform(get("/api/admin/analytics/overview").with(authentication(ordinary))).andExpect(status().isForbidden());
  var owner=SecurityConfig.authentication(new Actor(admin,"owner","ADMIN"));
  for(String route:new String[]{"overview","traffic","referrers","posts/popular","posts/recent","categories","posts/"+post+"/traffic"})
   mvc.perform(get("/api/admin/analytics/"+route).with(authentication(owner))).andExpect(status().isOk());
  mvc.perform(get("/api/admin/analytics/overview?days=8").with(authentication(owner))).andExpect(status().isBadRequest());
  mvc.perform(get("/api/admin/analytics/overview?start=2026-01-01&end=2026-01-07").with(authentication(owner))).andExpect(status().isOk());
 }
 @Test void csrfBotsAdminsAndAuthorizationHeadersAreExcluded()throws Exception {
  String body=json.writeValueAsString(event("/",null));
  mvc.perform(post("/api/analytics/page-views").contentType("application/json").content(body)).andExpect(status().isForbidden());
  mvc.perform(post("/api/analytics/page-views").with(csrf()).header("User-Agent","Googlebot").contentType("application/json").content(body)).andExpect(jsonPath("$.data.counted").value(false));
  mvc.perform(post("/api/analytics/page-views").with(csrf()).header("Authorization","Bearer ignored").contentType("application/json").content(body)).andExpect(jsonPath("$.data.counted").value(false));
  mvc.perform(post("/api/analytics/page-views").with(csrf()).header("DNT","1").contentType("application/json").content(body)).andExpect(jsonPath("$.data.counted").value(false));
  mvc.perform(post("/api/analytics/page-views").with(csrf()).with(authentication(SecurityConfig.authentication(new Actor(admin,"owner","ADMIN")))).contentType("application/json").content(body)).andExpect(jsonPath("$.data.counted").value(false));
  mvc.perform(post("/api/analytics/page-views").with(csrf()).header("Origin","https://evil.test").contentType("application/json").content(body)).andExpect(status().isForbidden());
  mvc.perform(post("/api/analytics/page-views").with(csrf()).contentType("application/json").content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.data.counted").value(true)).andExpect(result->assertEquals(2,result.getResponse().getHeaders("Set-Cookie").size()));
 }
 @Test void aggregatesCountUniqueVisitorsAndUseSeoulMidnight(){
  UUID session=UUID.randomUUID();String visitor="a".repeat(64);
  jdbc.update("INSERT INTO blog.visitor_sessions(id,visitor_hash,started_at,last_seen_at,source) VALUES(?,?,?::timestamptz,?::timestamptz,'NAVER')",session,visitor,"2026-01-01T14:59:00Z","2026-01-01T15:01:00Z");
  for(String time:List.of("2026-01-01T14:59:00Z","2026-01-01T15:01:00Z"))
   jdbc.update("INSERT INTO blog.page_view_events(id,session_id,visitor_hash,post_id,category_id,path,occurred_at,dedup_bucket) VALUES(?,?,?,?,?,?,?::timestamptz,?)",UUID.randomUUID(),session,visitor,post,category,"/posts/"+post,time,OffsetDateTime.parse(time).toEpochSecond()/300);
  var range=AnalyticsRange.of(null,LocalDate.of(2026,1,1),LocalDate.of(2026,1,2));
  var overview=analytics.overview(range);assertEquals(1,overview.visitors());assertEquals(2,overview.pageViews());assertEquals(2.0,overview.viewsPerVisitor());
  var traffic=analytics.traffic(range,null);assertEquals(1,traffic.get(0).pageViews());assertEquals(1,traffic.get(1).pageViews());
  assertEquals(2,analytics.popular(range,false).stream().filter(p->p.id()==post).findFirst().orElseThrow().periodViews());
  var cat=analytics.categories(range).stream().filter(c->Objects.equals(c.id(),category)).findFirst().orElseThrow();assertEquals(2,cat.periodViews());assertEquals(100.0,cat.percentage());
  assertEquals(1,analytics.referrers(range).get(0).visits());
 }
}
