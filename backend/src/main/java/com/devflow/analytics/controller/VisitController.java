package com.devflow.analytics.controller;
import com.devflow.analytics.dto.AnalyticsDtos.*;
import com.devflow.analytics.service.*;
import com.devflow.auth.domain.Actor;
import com.devflow.common.exception.ApiException;
import com.devflow.common.response.ApiResponse;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/analytics")
public class VisitController {
 private final VisitService service;private final VisitRateLimiter limiter;private final boolean secure;private final String publicOrigin;
 public VisitController(VisitService service,VisitRateLimiter limiter,@Value("${server.servlet.session.cookie.secure:true}") boolean secure,@Value("${analytics.public-origin:}") String origin){this.service=service;this.limiter=limiter;this.secure=secure;this.publicOrigin=origin;}
 private String cookie(HttpServletRequest q,String name){if(q.getCookies()!=null)for(var c:q.getCookies())if(c.getName().equals(name))return c.getValue();return null;}
 private void cookie(HttpServletResponse s,String name,UUID value,long age){s.addHeader(HttpHeaders.SET_COOKIE,ResponseCookie.from(name,value.toString()).httpOnly(true).secure(secure).sameSite("Lax").path("/api/analytics").maxAge(age).build().toString());}
 @PostMapping("/page-views") public ApiResponse<Collected> collect(@Valid @RequestBody PageView p,@AuthenticationPrincipal Actor actor,HttpServletRequest q,HttpServletResponse s){
  String ua=Objects.toString(q.getHeader("User-Agent"),"").toLowerCase(Locale.ROOT);
  if(!p.consent() || "1".equals(q.getHeader("DNT")) || "1".equals(q.getHeader("Sec-GPC")) || q.getHeader("Authorization")!=null || actor!=null && !actor.role().equals("USER") || ua.matches(".*(bot|crawler|spider|headless|slurp|preview|curl|python|wget).*"))return new ApiResponse<>(new Collected(false));
  String own=publicOrigin.isBlank()?q.getScheme()+"://"+q.getHeader("Host"):publicOrigin;
  String origin=q.getHeader("Origin");
  if(origin!=null && !origin.equals(own))throw new ApiException(HttpStatus.FORBIDDEN,"INVALID_ORIGIN","Same-origin collection only");
  if(!limiter.allow(q.getRemoteAddr()))throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,"RATE_LIMITED","Too many analytics requests");
  var r=service.collect(p,cookie(q,"dv_visitor"),cookie(q,"dv_visit"),own);
  if(r.visitor()!=null){cookie(s,"dv_visitor",r.visitor(),365L*86400);cookie(s,"dv_visit",r.session(),1800);}
  return new ApiResponse<>(new Collected(r.counted()));
 }
}
