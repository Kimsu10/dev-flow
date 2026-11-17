package com.devflow.auth.config;
import com.devflow.auth.mapper.AccountMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
@Component
public class AdminBootstrap implements ApplicationRunner {
 private final AccountMapper mapper; private final PasswordEncoder encoder;
 private final String username,email,password;
 public AdminBootstrap(AccountMapper mapper,PasswordEncoder encoder,
  @Value("${AUTH_ADMIN_USERNAME:}") String username,@Value("${AUTH_ADMIN_EMAIL:}") String email,
  @Value("${AUTH_ADMIN_PASSWORD:}") String password){this.mapper=mapper;this.encoder=encoder;this.username=username;this.email=email;this.password=password;}
 @Override public void run(ApplicationArguments args){
  if(username.isBlank() && email.isBlank() && password.isBlank()) return;
  if(!username.matches("[A-Za-z0-9_.-]{3,100}") || !email.contains("@") || email.length()>254 || password.length()<12 || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72)
   throw new IllegalStateException("Invalid administrator bootstrap configuration");
  var existing=mapper.findLogin(username);
  if(existing!=null){
   if(!existing.role().equals("ADMIN") || !existing.email().equalsIgnoreCase(email)) throw new IllegalStateException("Administrator bootstrap identity conflict");
   return;
  }
  mapper.create(username,email,encoder.encode(password),"ADMIN");
 }
}
