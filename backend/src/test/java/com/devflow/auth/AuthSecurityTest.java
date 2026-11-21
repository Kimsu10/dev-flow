package com.devflow.auth;
import com.devflow.auth.domain.Account;
import com.devflow.auth.mapper.AccountMapper;
import com.devflow.auth.service.AccountService;
import com.devflow.blog.mapper.PostWriteMapper;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest(properties={"spring.flyway.enabled=false","spring.datasource.url=jdbc:h2:mem:auth;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password=","OPENAI_API_KEY="})
@AutoConfigureMockMvc(print=MockMvcPrint.NONE)
class AuthSecurityTest {
 @Autowired MockMvc mvc;
 @Autowired PasswordEncoder encoder;
 @MockitoBean AccountMapper accounts;
 @MockitoBean PostWriteMapper posts;
 String password;
 Account account(long id,String role,String hash){var t=OffsetDateTime.now();return new Account(id,"test"+id,"test"+id+"@example.test",hash,role,"ACTIVE",t,t,null);}
 @BeforeEach void prepare(){
  password=UUID.randomUUID().toString();
  var admin=account(1,"ADMIN",encoder.encode(password));
  when(accounts.findLogin("owner")).thenReturn(admin);when(accounts.find(1)).thenReturn(admin);
 }
 MockHttpSession login() throws Exception {
  return (MockHttpSession)mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json")
    .content("{\"username\":\"owner\",\"password\":\""+password+"\",\"role\":\"SYSTEM\"}"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.data.role").value("ADMIN")).andReturn().getRequest().getSession(false);
 }
 @Test void anonymousAndCsrfBoundaries() throws Exception {
  mvc.perform(get("/api/admin/posts")).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
  mvc.perform(post("/api/auth/login").contentType("application/json").content("{}")) .andExpect(status().isForbidden());
  mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json").content("{}")) .andExpect(status().isBadRequest());
  mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andExpect(jsonPath("$.data.token").isString());
 }
 @Test void loginLogoutAndDisabledSession() throws Exception {
  var session=login();mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk());
  mvc.perform(post("/api/auth/logout").session(session)).andExpect(status().isForbidden());
  mvc.perform(post("/api/auth/logout").session(session).with(csrf())).andExpect(status().isOk());
  Assertions.assertTrue(session.isInvalid());
  session=login();when(accounts.find(1)).thenReturn(null);
  mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());
 }
 @Test void userCannotWriteAndServiceCredentialsAreIsolated() throws Exception {
  var user=account(1,"USER",encoder.encode(password));when(accounts.findLogin("owner")).thenReturn(user);when(accounts.find(1)).thenReturn(user);
  var session=(MockHttpSession)mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json").content("{\"username\":\"owner\",\"password\":\""+password+"\"}"))
   .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
  mvc.perform(get("/api/admin/posts").session(session)).andExpect(status().isForbidden());
  mvc.perform(post("/api/admin/posts").session(session).with(csrf()).contentType("application/json").content("{}")) .andExpect(status().isForbidden());
  mvc.perform(post("/api/agent/posts/drafts").session(session)).andExpect(status().isUnauthorized());
  String token=UUID.randomUUID().toString();var agent=account(3,"SYSTEM",null);when(accounts.byToken(AccountService.hash(token))).thenReturn(agent);
  mvc.perform(get("/api/admin/posts").header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/auth/me").header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized());
  when(accounts.findLogin("agent")).thenReturn(agent);
  mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json").content("{\"username\":\"agent\",\"password\":\""+password+"\"}")) .andExpect(status().isUnauthorized());
 }
 @Test void wrongPasswordFailsWithoutTrustingRole() throws Exception {
  mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json").content("{\"username\":\"owner\",\"password\":\"incorrect\",\"role\":\"ADMIN\"}")) .andExpect(status().isUnauthorized());
 }
 @Test void missingAiCredentialsReturnExplicitUnavailableWhileManualAdminQueriesStillWork() throws Exception {
  var session=login();
  mvc.perform(post("/api/admin/ai-drafts").session(session).with(csrf()).contentType("application/json")
    .content("{\"question\":\"Explain transaction isolation\",\"publicQuestionAccepted\":true}"))
    .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.error.code").value("AI_NOT_CONFIGURED"));
  mvc.perform(get("/api/admin/posts").session(session)).andExpect(status().isOk());
 }
}
