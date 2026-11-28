package com.devflow.auth.controller;
import com.devflow.auth.domain.Actor;
import com.devflow.auth.service.AccountService;
import com.devflow.auth.service.LoginRateLimiter;
import com.devflow.common.exception.ApiException;
import com.devflow.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/admin/accounts")
public class AccountController {
 private final AccountService accounts;
 private final LoginRateLimiter limiter;
 public AccountController(AccountService accounts,LoginRateLimiter limiter){this.accounts=accounts;this.limiter=limiter;}
 public record Create(@NotBlank @Pattern(regexp="[A-Za-z0-9_.-]{3,100}") String username,
  @NotBlank @Email @Size(max=254) String email,@Size(max=72) String password,@NotNull String role) {}
 public record Status(@NotNull String status) {}
 public record ChangePassword(@NotBlank @Size(max=72) String currentPassword,
                              @NotBlank @Size(min=12,max=72) String newPassword) {
  @Override public String toString() { return "ChangePassword[redacted]"; }
 }
 @GetMapping public ApiResponse<List<AccountService.Profile>> list(){return new ApiResponse<>(accounts.list());}
 @PostMapping public ApiResponse<AccountService.Profile> create(@Valid @RequestBody Create c){return new ApiResponse<>(accounts.create(c.username(),c.email(),c.password(),c.role()));}
 @PatchMapping({"/me/password","/{id}/password"})
 public ApiResponse<Void> changePassword(@PathVariable(required=false) Long id,@Valid @RequestBody ChangePassword body,
     @AuthenticationPrincipal Actor actor,HttpServletResponse response) {
  var decision=limiter.passwordChange(actor.id());
  if(!decision.allowed()){
   response.setHeader("Retry-After",Long.toString(decision.retryAfterSeconds()));
   throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,"PASSWORD_CHANGE_RATE_LIMITED","Too many password change requests");
  }
  accounts.changePassword(actor.id(),id==null?actor.id():id,body.currentPassword(),body.newPassword());
  return new ApiResponse<>(null);
 }
 @PatchMapping("/{id}/status") public ApiResponse<Void> status(@PathVariable long id,@Valid @RequestBody Status s,@AuthenticationPrincipal Actor actor){accounts.status(id,s.status(),actor.id());return new ApiResponse<>(null);}
 @PostMapping("/{id}/token") public ApiResponse<Map<String,String>> token(@PathVariable long id){return new ApiResponse<>(Map.of("token",accounts.issue(id)));}
 @DeleteMapping("/{id}/token") public ApiResponse<Void> revoke(@PathVariable long id){accounts.revoke(id);return new ApiResponse<>(null);}
}
