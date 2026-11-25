package com.devflow.auth.config;
import com.devflow.auth.domain.Actor;
import com.devflow.auth.service.AccountService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.Map;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.*;
import org.springframework.web.filter.OncePerRequestFilter;
@Configuration
public class SecurityConfig {
 @Bean PasswordEncoder passwordEncoder(){return new BCryptPasswordEncoder();}
 @Bean HttpSessionSecurityContextRepository contextRepository(){return new HttpSessionSecurityContextRepository();}
 public static UsernamePasswordAuthenticationToken authentication(Actor actor){
  return new UsernamePasswordAuthenticationToken(actor,null,java.util.List.of(new SimpleGrantedAuthority("ROLE_"+actor.role())));
 }
 public static void error(HttpServletResponse response,int status,String code) throws IOException {
  response.setStatus(status);response.setContentType("application/json");
  new ObjectMapper().writeValue(response.getOutputStream(),Map.of("error",Map.of("code",code,"message",status==401?"Authentication required":status==503?"Service temporarily unavailable":"Access denied")));
 }
 private void errors(HttpSecurity http) throws Exception {
  http.exceptionHandling(e->e.authenticationEntryPoint((q,s,x)->error(s,401,"UNAUTHENTICATED"))
   .accessDeniedHandler((q,s,x)->error(s,403,"FORBIDDEN")));
 }
 @Bean @Order(1) SecurityFilterChain agent(HttpSecurity http,AccountService accounts) throws Exception {
  http.securityMatcher("/api/agent/**").csrf(c->c.disable())
   .sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
   .securityContext(c->c.securityContextRepository(new NullSecurityContextRepository()))
   .requestCache(c->c.disable()).authorizeHttpRequests(a->a.anyRequest().hasRole("SYSTEM"));
  http.addFilterAfter(new OncePerRequestFilter(){
   @Override protected void doFilterInternal(HttpServletRequest q,HttpServletResponse s,FilterChain chain) throws ServletException,IOException {
    String header=q.getHeader("Authorization");
    try {
     Actor actor=header!=null && header.startsWith("Bearer ")?accounts.token(header.substring(7)):null;
     if(actor==null){error(s,401,"INVALID_SERVICE_TOKEN");return;}
     SecurityContextHolder.getContext().setAuthentication(authentication(actor));
    } catch(org.springframework.dao.DataAccessException ex){error(s,503,"DATABASE_UNAVAILABLE");return;}
    chain.doFilter(q,s);
   }
  },SecurityContextHolderFilter.class);
  errors(http); return http.build();
 }
 @Bean SecurityFilterChain browser(HttpSecurity http,AccountService accounts,HttpSessionSecurityContextRepository repository) throws Exception {
  http.securityContext(c->c.securityContextRepository(repository))
   .requestCache(c->c.disable())
   .authorizeHttpRequests(a->a
    .requestMatchers("/api/admin/**").hasRole("ADMIN")
    .requestMatchers("/api/auth/me","/api/auth/logout").authenticated()
    .requestMatchers(HttpMethod.POST,"/api/auth/login","/api/analytics/page-views").permitAll()
    .requestMatchers(HttpMethod.GET,"/api/auth/csrf","/api/health","/api/blog/home","/api/categories","/api/posts","/api/posts/*","/api/posts/*/related").permitAll()
    .anyRequest().denyAll())
   .logout(l->l.logoutUrl("/api/auth/logout").deleteCookies("JSESSIONID")
    .logoutSuccessHandler((q,s,a)->{s.setContentType("application/json");s.getWriter().write("{\"data\":null}");}));
  // Recheck account status/role on every session request so disabling a user takes effect immediately.
  http.addFilterAfter(new OncePerRequestFilter(){
   @Override protected void doFilterInternal(HttpServletRequest q,HttpServletResponse s,FilterChain chain) throws ServletException,IOException {
    var auth=SecurityContextHolder.getContext().getAuthentication();
    if(auth!=null && auth.getPrincipal() instanceof Actor actor){
     try {
      var account=accounts.require(actor.id());
      if(account.role().equals("SYSTEM")) throw new com.devflow.common.exception.ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED,"UNAUTHENTICATED","Authentication required");
      SecurityContextHolder.getContext().setAuthentication(authentication(accounts.actor(account)));
     } catch(com.devflow.common.exception.ApiException ex){
      SecurityContextHolder.clearContext(); if(q.getSession(false)!=null) q.getSession(false).invalidate();
      error(s,401,"UNAUTHENTICATED");return;
     } catch(org.springframework.dao.DataAccessException ex){error(s,503,"DATABASE_UNAVAILABLE");return;}
    }
    chain.doFilter(q,s);
   }
  },SecurityContextHolderFilter.class);
  errors(http);return http.build();
 }
}
