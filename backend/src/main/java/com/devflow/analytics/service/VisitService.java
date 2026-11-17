package com.devflow.analytics.service;
import com.devflow.analytics.dto.AnalyticsDtos.*;
import com.devflow.analytics.mapper.VisitMapper;
import com.devflow.auth.service.AccountService;
import com.devflow.common.exception.ApiException;
import java.net.URI;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class VisitService {
 private final VisitMapper mapper;
 public VisitService(VisitMapper mapper){this.mapper=mapper;}
 public record Receipt(boolean counted,UUID visitor,UUID session) {}
 public record Attribution(String source,String domain) {}
 public static UUID uuid(String s){try{return s==null?null:UUID.fromString(s);}catch(IllegalArgumentException e){return null;}}
 private static ApiException invalid(){return new ApiException(HttpStatus.BAD_REQUEST,"INVALID_EVENT","Invalid analytics event");}
 public static String domain(String url){
  if(url==null || url.isBlank())return null;
  try{URI uri=URI.create(url);String host=uri.getHost();return host!=null && Set.of("http","https").contains(uri.getScheme()) && host.length()<=253?host.toLowerCase(Locale.ROOT):null;}
  catch(IllegalArgumentException e){return null;}
 }
 private static boolean host(String actual,String base){return actual!=null && (actual.equals(base) || actual.endsWith("."+base));}
 public static Attribution attribution(String referrer,String utm,String origin){
  String d=domain(referrer);String own=domain(origin);
  if(d!=null && d.equals(own))return new Attribution("INTERNAL",null);
  String u=utm==null?"":utm.toLowerCase(Locale.ROOT);
  String source;
  if(host(d,"google.com") || host(d,"google.co.kr") || u.equals("google"))source="GOOGLE";
  else if(host(d,"naver.com") || u.equals("naver"))source="NAVER";
  else if(host(d,"daum.net") || u.equals("daum"))source="DAUM";
  else if(host(d,"github.com") || u.equals("github"))source="GITHUB";
  else source=d!=null || !u.isEmpty()?"OTHER":"DIRECT";
  return new Attribution(source,d);
 }
 public static void validate(PageView p){
  String path=p.path();
  if(!path.matches("/|/posts|/posts\\?((categoryId|page)=[0-9]{1,10})(&((categoryId|page)=[0-9]{1,10}))?|/posts/[1-9][0-9]{0,17}"))throw invalid();
  if(p.postId()!=null && !path.equals("/posts/"+p.postId()) || p.postId()==null && path.matches("/posts/[0-9]+"))throw invalid();
  for(String value:Arrays.asList(p.utmSource(),p.utmMedium(),p.utmCampaign()))
   if(value!=null && !value.matches("[A-Za-z0-9_.~-]{0,100}"))throw invalid();
 }
 @Transactional public Receipt collect(PageView p,String visitorCookie,String sessionCookie,String origin){
  validate(p);
  if(mapper.exists(p.eventId()))return new Receipt(false,null,null);
  Long category=null;
  if(p.postId()!=null){var post=mapper.post(p.postId());if(post==null)return new Receipt(false,null,null);category=post.categoryId();}
  UUID visitor=uuid(visitorCookie);if(visitor==null)visitor=UUID.randomUUID();String hash=AccountService.hash(visitor.toString());
  OffsetDateTime now=OffsetDateTime.now(ZoneOffset.UTC);UUID sid=uuid(sessionCookie);
  VisitSession session=sid==null?null:mapper.session(sid,hash);
  boolean created=session==null || session.lastSeenAt().isBefore(now.minusMinutes(30));
  if(created){
   sid=UUID.randomUUID();var a=attribution(p.referrer(),p.utmSource(),origin);
   mapper.create(sid,hash,now,a.source(),a.domain(),p);
  }else mapper.touch(sid,now);
  boolean counted=mapper.event(p,sid,hash,category,now,now.toEpochSecond()/300)>0;
  if(created && !counted){mapper.removeEmpty(sid);return new Receipt(false,null,null);}
  return new Receipt(counted,visitor,sid);
 }
}
