package com.devflow.auth.service;
import com.devflow.auth.domain.*;
import com.devflow.auth.mapper.AccountMapper;
import com.devflow.common.exception.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class AccountService {
 private final AccountMapper mapper;
 private final PasswordEncoder encoder;
 private final String dummy;
 public AccountService(AccountMapper mapper, PasswordEncoder encoder) {
  this.mapper=mapper; this.encoder=encoder; this.dummy=encoder.encode(UUID.randomUUID().toString());
 }
 public record Profile(long id,String username,String email,String role,String status) {}
 public Profile profile(Account a) { return new Profile(a.id(),a.username(),a.email(),a.role(),a.status()); }
 public Account require(long id) {
  Account a=mapper.find(id);
  if(a==null || !a.status().equals("ACTIVE")) throw new ApiException(HttpStatus.UNAUTHORIZED,"UNAUTHENTICATED","Authentication required");
  return a;
 }
 public Actor actor(Account a) { return new Actor(a.id(),a.username(),a.role()); }
 public Actor authenticate(String login,String password) {
  Account a=mapper.findLogin(login);
  if(password.getBytes(StandardCharsets.UTF_8).length>72) throw new ApiException(HttpStatus.UNAUTHORIZED,"INVALID_CREDENTIALS","Invalid credentials");
  boolean valid=encoder.matches(password,a==null || a.passwordHash()==null ? dummy : a.passwordHash());
  if(!valid || a==null || a.role().equals("SYSTEM") || !a.status().equals("ACTIVE"))
   throw new ApiException(HttpStatus.UNAUTHORIZED,"INVALID_CREDENTIALS","Invalid credentials");
  mapper.login(a.id()); return actor(a);
 }
 public Actor token(String token) {
  if(token.length()>256) return null;
  Account a=mapper.byToken(hash(token)); return a==null?null:actor(a);
 }
 public static String hash(String token) {
  try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));}
  catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
 }
 public List<Profile> list(){return mapper.list().stream().map(this::profile).toList();}
 @Transactional public Profile create(String username,String email,String password,String role) {
  if(!Set.of("USER","SYSTEM").contains(role)) throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_ROLE","Only USER or SYSTEM may be created");
  if(role.equals("USER") && (password==null || password.length()<12 || password.getBytes(StandardCharsets.UTF_8).length>72))
   throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_PASSWORD","Password must contain 12 to 72 characters");
  long id=mapper.create(username,email,role.equals("SYSTEM")?null:encoder.encode(password),role);
  return profile(mapper.find(id));
 }
 @Transactional public void status(long id,String status,long currentId) {
  Account a=mapper.lock(id);
  if(a==null) throw new ApiException(HttpStatus.NOT_FOUND,"ACCOUNT_NOT_FOUND","Account not found");
  if(id==currentId || a.role().equals("ADMIN")) throw new ApiException(HttpStatus.CONFLICT,"PROTECTED_ADMIN","Administrator status must be managed operationally");
  if(!Set.of("ACTIVE","DISABLED").contains(status)) throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_STATUS","Invalid status");
  mapper.status(id,status);
  if(status.equals("DISABLED")) mapper.revoke(id);
 }
 @Transactional public String issue(long id) {
  Account a=mapper.lock(id);
  if(a==null || !a.status().equals("ACTIVE")) throw new ApiException(HttpStatus.BAD_REQUEST,"INACTIVE_ACCOUNT","Active service account required");
  if(!a.role().equals("SYSTEM")) throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_ROLE","Service tokens require SYSTEM");
  byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);
  String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  mapper.revoke(id);mapper.token(id,hash(token));return token;
 }
 @Transactional public void revoke(long id){
  // Use the same account lock as issuance and disabling so a completed revocation covers a concurrent rotation.
  Account a=mapper.lock(id);
  if(a==null) throw new ApiException(HttpStatus.NOT_FOUND,"ACCOUNT_NOT_FOUND","Account not found");
  if(!a.role().equals("SYSTEM")) throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_ROLE","Service tokens require SYSTEM");
  mapper.revoke(id);
 }
}
