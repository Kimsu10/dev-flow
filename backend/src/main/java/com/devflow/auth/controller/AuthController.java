package com.devflow.auth.controller;
import com.devflow.auth.domain.Actor;
import com.devflow.auth.config.SecurityConfig;
import com.devflow.auth.service.AccountService;
import com.devflow.auth.service.LoginRateLimiter;
import com.devflow.common.response.ApiResponse;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.*;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/auth")
public class AuthController {
 private final AccountService accounts; private final HttpSessionSecurityContextRepository repository; private final LoginRateLimiter limiter;
 public AuthController(AccountService accounts,HttpSessionSecurityContextRepository repository,LoginRateLimiter limiter){this.accounts=accounts;this.repository=repository;this.limiter=limiter;}
 public record Login(@NotBlank @Size(max=254) String username,@NotBlank @Size(max=72) String password) {}
 public record Csrf(String token,String headerName) {}
 @GetMapping("/csrf") public ApiResponse<Csrf> csrf(CsrfToken token){return new ApiResponse<>(new Csrf(token.getToken(),token.getHeaderName()));}
 @PostMapping("/login") public ApiResponse<AccountService.Profile> login(@Valid @RequestBody Login body,HttpServletRequest q,HttpServletResponse s){
  var decision=limiter.identity(body.username());
  if(!decision.allowed()){
   s.setHeader("Retry-After",Long.toString(decision.retryAfterSeconds()));
   throw new com.devflow.common.exception.ApiException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,"LOGIN_RATE_LIMITED","Too many login requests");
  }
  Actor actor=accounts.authenticate(body.username(),body.password());
  if(q.getSession(false)!=null) q.changeSessionId(); else q.getSession(true);
  var context=SecurityContextHolder.createEmptyContext();context.setAuthentication(SecurityConfig.authentication(actor));
  SecurityContextHolder.setContext(context);repository.saveContext(context,q,s);
  new HttpSessionCsrfTokenRepository().saveToken(null,q,s);
  return new ApiResponse<>(accounts.profile(accounts.require(actor.id())));
 }
 @GetMapping("/me") public ApiResponse<AccountService.Profile> me(@AuthenticationPrincipal Actor actor){return new ApiResponse<>(accounts.profile(accounts.require(actor.id())));}
}
