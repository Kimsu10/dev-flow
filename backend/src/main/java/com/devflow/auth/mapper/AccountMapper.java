package com.devflow.auth.mapper;
import com.devflow.auth.domain.Account;
import java.util.List;
import org.apache.ibatis.annotations.*;
@Mapper
public interface AccountMapper {
 String COLS = "id,username,email,password_hash,role,status,created_at,updated_at,last_login_at";
 @Select("SELECT " + COLS + " FROM blog.accounts WHERE lower(username)=lower(#{login}) OR lower(email)=lower(#{login})")
 Account findLogin(String login);
 @Select("SELECT " + COLS + " FROM blog.accounts WHERE id=#{id}") Account find(long id);
 @Select("SELECT " + COLS + " FROM blog.accounts WHERE id=#{id} FOR UPDATE") Account lock(long id);
 @Select("SELECT " + COLS + " FROM blog.accounts ORDER BY id LIMIT 500") List<Account> list();
 @Select("INSERT INTO blog.accounts(username,email,password_hash,role) VALUES(#{username},#{email},#{hash},#{role}) RETURNING id")
 long create(@Param("username") String username,@Param("email") String email,@Param("hash") String hash,@Param("role") String role);
 @Update("UPDATE blog.accounts SET last_login_at=now() WHERE id=#{id}") void login(long id);
 @Update("UPDATE blog.accounts SET status=#{status},updated_at=now() WHERE id=#{id}")
 int status(@Param("id") long id,@Param("status") String status);
 @Insert("INSERT INTO blog.service_tokens(account_id,token_hash,expires_at) VALUES(#{id},#{hash},now() + interval '90 days')")
 void token(@Param("id") long id,@Param("hash") String hash);
 @Update("UPDATE blog.service_tokens SET revoked_at=now() WHERE account_id=#{id} AND revoked_at IS NULL") void revoke(long id);
 @Select("SELECT a.id,a.username,a.email,a.password_hash,a.role,a.status,a.created_at,a.updated_at,a.last_login_at FROM blog.accounts a JOIN blog.service_tokens t ON t.account_id=a.id WHERE t.token_hash=#{hash} AND t.revoked_at IS NULL AND t.expires_at>now() AND a.role='SYSTEM' AND a.status='ACTIVE'")
 Account byToken(String hash);
}
