package com.devflow.auth.controller;
import com.devflow.auth.domain.Actor;
import com.devflow.auth.service.AccountService;
import com.devflow.common.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/admin/accounts")
public class AccountController {
 private final AccountService accounts;
 public AccountController(AccountService accounts){this.accounts=accounts;}
 public record Create(@NotBlank @Pattern(regexp="[A-Za-z0-9_.-]{3,100}") String username,
  @NotBlank @Email @Size(max=254) String email,@Size(max=72) String password,@NotNull String role) {}
 public record Status(@NotNull String status) {}
 @GetMapping public ApiResponse<List<AccountService.Profile>> list(){return new ApiResponse<>(accounts.list());}
 @PostMapping public ApiResponse<AccountService.Profile> create(@Valid @RequestBody Create c){return new ApiResponse<>(accounts.create(c.username(),c.email(),c.password(),c.role()));}
 @PatchMapping("/{id}/status") public ApiResponse<Void> status(@PathVariable long id,@Valid @RequestBody Status s,@AuthenticationPrincipal Actor actor){accounts.status(id,s.status(),actor.id());return new ApiResponse<>(null);}
 @PostMapping("/{id}/token") public ApiResponse<Map<String,String>> token(@PathVariable long id){return new ApiResponse<>(Map.of("token",accounts.issue(id)));}
 @DeleteMapping("/{id}/token") public ApiResponse<Void> revoke(@PathVariable long id){accounts.revoke(id);return new ApiResponse<>(null);}
}
