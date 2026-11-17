package com.devflow.analytics.service;
import java.util.*;
import com.devflow.auth.service.AccountService;
import org.springframework.stereotype.Component;
@Component
public class VisitRateLimiter {
 private final String salt=UUID.randomUUID().toString();
 private final Map<String,Integer> counts=new HashMap<>();
 private long minute;
 public synchronized boolean allow(String address){
  long now=System.currentTimeMillis()/60000;
  if(now!=minute){counts.clear();minute=now;}
  String key=AccountService.hash(salt+address);
  if(!counts.containsKey(key) && counts.size()>=10000)return false;
  int n=counts.merge(key,1,Integer::sum);return n<=60;
 }
}
