package com.devflow.auth;
import com.devflow.auth.mapper.AccountMapper;
import com.devflow.auth.service.AccountService;
import com.fasterxml.jackson.databind.*;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@Tag("postgres") @SpringBootTest @AutoConfigureMockMvc(print=MockMvcPrint.NONE) @Transactional
class AuthPostgresIntegrationTest {
 @Autowired org.mybatis.spring.SqlSessionTemplate sqlSession;
 @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcTemplate jdbc;
 @Autowired AccountMapper accounts; @Autowired PasswordEncoder encoder;
 String password,username,email; long adminId;
 @BeforeEach void fixtures(){
  String suffix=UUID.randomUUID().toString();username="auth-"+suffix; email=username+"@example.test";
  password=UUID.randomUUID().toString();adminId=accounts.create(username,email,encoder.encode(password),"ADMIN");
 }
 JsonNode data(MvcResult r)throws Exception{return json.readTree(r.getResponse().getContentAsString()).path("data");}
 MockHttpSession login(String name,String pass)throws Exception {
  var initial=mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
  var session=(MockHttpSession)initial.getRequest().getSession(false);String oldId=session.getId();
  var csrf=data(initial);
  var result=mvc.perform(post("/api/auth/login").session(session).header(csrf.path("headerName").asText(),csrf.path("token").asText())
   .contentType("application/json").content(json.writeValueAsString(java.util.Map.of("username",name,"password",pass)))) .andExpect(status().isOk()).andReturn();
  assertNotEquals(oldId,result.getRequest().getSession(false).getId());
  mvc.perform(post("/api/admin/posts").session(session).header(csrf.path("headerName").asText(),csrf.path("token").asText()).contentType("application/json").content("{}")) .andExpect(status().isForbidden());
  return (MockHttpSession)result.getRequest().getSession(false);
 }
 ResultActions write(MockHttpSession session,String method,String path,Object body)throws Exception {
  var csrf=data(mvc.perform(get("/api/auth/csrf").session(session)).andExpect(status().isOk()).andReturn());
  var request=request(org.springframework.http.HttpMethod.valueOf(method),path).session(session).header(csrf.path("headerName").asText(),csrf.path("token").asText());
  if(body!=null)request.contentType("application/json").content(json.writeValueAsString(body));
  return mvc.perform(request);
 }
 java.util.Map<String,Object> body(String title,String status){return java.util.Map.of("title",title,"contentMarkdown","# Review\n\n**content**","summary","summary","status",status);}
 @Test void completeSessionAndAgentReviewWorkflow()throws Exception {
  var admin=login(email,password);
  mvc.perform(get("/api/auth/me").session(admin)).andExpect(jsonPath("$.data.role").value("ADMIN")).andExpect(jsonPath("$.data.passwordHash").doesNotExist());
  var accountBody=java.util.Map.of("username","system-"+UUID.randomUUID(),"email",UUID.randomUUID()+"@example.test","role","SYSTEM");
  long agentId=data(write(admin,"POST","/api/admin/accounts",accountBody).andExpect(status().isOk()).andReturn()).path("id").asLong();
  String token=data(write(admin,"POST","/api/admin/accounts/"+agentId+"/token",null).andExpect(status().isOk()).andReturn()).path("token").asText();
  String stored=jdbc.queryForObject("SELECT token_hash FROM blog.service_tokens WHERE account_id=? AND revoked_at IS NULL",String.class,agentId);
  assertEquals(AccountService.hash(token),stored);assertNotEquals(token,stored);
  assertNull(accounts.find(agentId).passwordHash());
  long draft=data(mvc.perform(post("/api/agent/posts/drafts").header("Authorization","Bearer "+token).contentType("application/json").content(json.writeValueAsString(body("agent draft","IN_REVIEW"))))
    .andExpect(status().isOk()).andExpect(jsonPath("$.data.authorRole").value("SYSTEM")).andReturn()).path("id").asLong();
  mvc.perform(get("/api/posts/"+draft)).andExpect(status().isNotFound());
  mvc.perform(put("/api/agent/posts/drafts/"+draft).header("Authorization","Bearer "+token).contentType("application/json").content(json.writeValueAsString(body("edited","IN_REVIEW")))) .andExpect(status().isOk());
  mvc.perform(post("/api/agent/posts/drafts").header("Authorization","Bearer "+token).contentType("application/json").content(json.writeValueAsString(body("publish forbidden","PUBLISHED")))) .andExpect(status().isBadRequest());
  mvc.perform(patch("/api/admin/posts/"+draft+"/publish").header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
  long otherId=accounts.create("other-"+UUID.randomUUID(),UUID.randomUUID()+"@example.test",null,"SYSTEM");
  String otherToken=UUID.randomUUID().toString();accounts.token(otherId,AccountService.hash(otherToken));
  mvc.perform(put("/api/agent/posts/drafts/"+draft).header("Authorization","Bearer "+otherToken).contentType("application/json").content(json.writeValueAsString(body("other agent","DRAFT")))) .andExpect(status().isForbidden());
  jdbc.update("UPDATE blog.service_tokens SET expires_at=now()-interval '1 second' WHERE account_id=?",otherId);
  sqlSession.clearCache();
  mvc.perform(post("/api/agent/posts/drafts").header("Authorization","Bearer "+otherToken)).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/admin/posts/drafts").session(admin)).andExpect(status().isOk());
  write(admin,"PATCH","/api/admin/posts/"+draft+"/publish",null).andExpect(status().isOk());
  mvc.perform(get("/api/posts/"+draft)).andExpect(status().isOk());
  mvc.perform(put("/api/agent/posts/drafts/"+draft).header("Authorization","Bearer "+token).contentType("application/json").content(json.writeValueAsString(body("after publish","DRAFT")))) .andExpect(status().isForbidden());
  write(admin,"PATCH","/api/admin/posts/"+draft+"/unpublish",null).andExpect(status().isOk());
  mvc.perform(get("/api/posts/"+draft)).andExpect(status().isNotFound());
  long own=data(write(admin,"POST","/api/admin/posts",body("owner draft","DRAFT")).andExpect(status().isOk()).andReturn()).path("id").asLong();
  mvc.perform(put("/api/agent/posts/drafts/"+own).header("Authorization","Bearer "+token).contentType("application/json").content(json.writeValueAsString(body("foreign","DRAFT")))) .andExpect(status().isForbidden());
  write(admin,"PUT","/api/admin/posts/"+own,body("owner updated","DRAFT")).andExpect(status().isOk());
  write(admin,"DELETE","/api/admin/posts/"+own,null).andExpect(status().isOk());
  mvc.perform(get("/api/admin/posts/"+own).session(admin)).andExpect(status().isNotFound());
  String next=data(write(admin,"POST","/api/admin/accounts/"+agentId+"/token",null).andExpect(status().isOk()).andReturn()).path("token").asText();
  mvc.perform(post("/api/agent/posts/drafts").header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized());
  write(admin,"DELETE","/api/admin/accounts/"+agentId+"/token",null).andExpect(status().isOk());
  mvc.perform(post("/api/agent/posts/drafts").header("Authorization","Bearer "+next)).andExpect(status().isUnauthorized());
  write(admin,"POST","/api/auth/logout",null).andExpect(status().isOk());assertTrue(admin.isInvalid());
 }
 @Test void userAccountPrivilegesCsrfAndDisabledSessions()throws Exception {
  var admin=login(username,password);String user="user-"+UUID.randomUUID();
  write(admin,"POST","/api/admin/accounts",java.util.Map.of("username",user,"email",user+"@example.test","role","ADMIN","password",password)).andExpect(status().isBadRequest());
  long userId=data(write(admin,"POST","/api/admin/accounts",java.util.Map.of("username",user,"email",user+"@example.test","role","USER","password",password)).andExpect(status().isOk()).andReturn()).path("id").asLong();
  assertTrue(encoder.matches(password,accounts.find(userId).passwordHash()));
  var session=login(user,password);
  mvc.perform(get("/api/posts").session(session)).andExpect(status().isOk());
  mvc.perform(get("/api/admin/posts").session(session)).andExpect(status().isForbidden());
  write(session,"POST","/api/admin/posts",body("forbidden","DRAFT")).andExpect(status().isForbidden());
  mvc.perform(post("/api/admin/posts").session(admin).contentType("application/json").content(json.writeValueAsString(body("no csrf","DRAFT")))) .andExpect(status().isForbidden());
  write(admin,"PATCH","/api/admin/accounts/"+userId+"/status",java.util.Map.of("status","DISABLED")).andExpect(status().isOk());
  mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());
 }
 @Test void staleEditorCannotOverwriteOrPublishNewerPost()throws Exception {
  var admin=login(username,password);
  var created=data(write(admin,"POST","/api/admin/posts",body("original","DRAFT")).andExpect(status().isOk()).andReturn());
  long id=created.path("id").asLong();
  assertEquals(0,created.path("version").asLong());
  var update=new java.util.HashMap<String,Object>(body("first tab","DRAFT"));update.put("version",0);
  write(admin,"PUT","/api/admin/posts/"+id,update).andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value(1));
  update.put("title","stale second tab");
  write(admin,"PUT","/api/admin/posts/"+id,update).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("POST_CONFLICT"));
  mvc.perform(get("/api/admin/posts/"+id).session(admin)).andExpect(jsonPath("$.data.title").value("first tab")).andExpect(jsonPath("$.data.version").value(1));
  write(admin,"PATCH","/api/admin/posts/"+id+"/publish?version=0",null).andExpect(status().isConflict());
  write(admin,"PATCH","/api/admin/posts/"+id+"/publish?version=1",null).andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value(2));
  update.put("version",1);
  write(admin,"PUT","/api/admin/posts/"+id,update).andExpect(status().isConflict());
  write(admin,"PATCH","/api/admin/posts/"+id+"/unpublish?version=2",null).andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value(3));
  update.put("version",-1);
  write(admin,"PUT","/api/admin/posts/"+id,update).andExpect(status().isBadRequest());
 }
}
