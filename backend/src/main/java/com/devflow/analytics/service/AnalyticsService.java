package com.devflow.analytics.service;
import com.devflow.analytics.dto.AnalyticsDtos.*;
import com.devflow.analytics.mapper.AnalyticsMapper;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
@Transactional(readOnly=true)
public class AnalyticsService {
 private final AnalyticsMapper mapper;
 private final int retention;
 public AnalyticsService(AnalyticsMapper mapper,@Value("${analytics.retention-days:365}") int retention){this.mapper=mapper;this.retention=retention;}
 public static Double change(long current,long previous){return previous==0?null:Math.round((current-previous)*10000.0/previous)/100.0;}
 public Overview overview(AnalyticsRange r){
  Counts c=mapper.counts(r.from(),r.until()),p=mapper.counts(r.previous().from(),r.previous().until()),t=mapper.totals();
  var today=LocalDate.now(AnalyticsRange.ZONE);var day=new AnalyticsRange(today,today);var yesterday=day.previous();
  long visitors=mapper.counts(day.from(),day.until()).visitors(),previous=mapper.counts(yesterday.from(),yesterday.until()).visitors();
  return new Overview(r.start(),r.end(),c.visitors(),c.pageViews(),c.visits(),mapper.postCount(),
   c.visitors()==0?0:Math.round(c.pageViews()*100.0/c.visitors())/100.0,visitors,previous,t.pageViews(),t.visitors(),
   change(c.visitors(),p.visitors()),change(c.pageViews(),p.pageViews()),change(visitors,previous),retention);
 }
 public List<Traffic> traffic(AnalyticsRange r,Long post){
  List<Traffic> rows=post==null?mapper.traffic(r.from(),r.until()):mapper.postTraffic(post,r.from(),r.until());
  Map<LocalDate,Traffic> days=new HashMap<>();rows.forEach(v->days.put(v.date(),v));
  return r.start().datesUntil(r.end().plusDays(1)).map(d->days.getOrDefault(d,new Traffic(d,0,0))).toList();
 }
 public List<Referrer> referrers(AnalyticsRange r){
  var rows=mapper.referrers(r.from(),r.until());long total=rows.stream().mapToLong(Referrer::visits).sum();
  return rows.stream().map(x->new Referrer(x.source(),x.visits(),total==0?0:x.visits()*100.0/total)).toList();
 }
 public List<PostStats> popular(AnalyticsRange r,boolean allTime){return allTime?mapper.allTime(r.from(),r.until()):mapper.popular(r.from(),r.until());}
 public List<PostStats> recent(AnalyticsRange r){return mapper.recent(r.from(),r.until());}
 public List<CategoryStats> categories(AnalyticsRange r){
  var rows=mapper.categories(r.from(),r.until());long total=rows.stream().mapToLong(CategoryStats::periodViews).sum();
  return rows.stream().map(x->new CategoryStats(x.id(),x.name(),x.postCount(),x.totalViews(),x.periodViews(),total==0?0:x.periodViews()*100.0/total)).toList();
 }
}
