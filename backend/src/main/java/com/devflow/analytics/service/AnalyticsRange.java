package com.devflow.analytics.service;
import com.devflow.common.exception.ApiException;
import java.time.*;
import java.time.temporal.ChronoUnit;
import org.springframework.http.HttpStatus;
public record AnalyticsRange(LocalDate start,LocalDate end) {
 public static final ZoneId ZONE=ZoneId.of("Asia/Seoul");
 public static AnalyticsRange of(Integer days,LocalDate start,LocalDate end){
  LocalDate today=LocalDate.now(ZONE);
  if((start==null)!=(end==null)) throw invalid();
  if(start==null){int n=days==null?7:days;if(n!=7 && n!=30 && n!=90) throw invalid();end=today;start=end.minusDays(n-1);}
  if(end.isAfter(today) || start.isAfter(end) || ChronoUnit.DAYS.between(start,end)>=366) throw invalid();
  return new AnalyticsRange(start,end);
 }
 private static ApiException invalid(){return new ApiException(HttpStatus.BAD_REQUEST,"INVALID_PERIOD","Use 7, 30, 90 days or an inclusive range of at most 366 days ending today or earlier");}
 public OffsetDateTime from(){return start.atStartOfDay(ZONE).toOffsetDateTime();}
 public OffsetDateTime until(){return end.plusDays(1).atStartOfDay(ZONE).toOffsetDateTime();}
 public AnalyticsRange previous(){long n=ChronoUnit.DAYS.between(start,end)+1;return new AnalyticsRange(start.minusDays(n),start.minusDays(1));}
}
