package com.devflow.analytics.service;
import com.devflow.analytics.mapper.VisitMapper;
import java.time.OffsetDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.*;
import org.springframework.transaction.annotation.Transactional;
@Configuration @EnableScheduling
public class AnalyticsRetention {
 private final VisitMapper mapper;private final int days;
 public AnalyticsRetention(VisitMapper mapper,@Value("${analytics.retention-days:365}") int days){
  if(days<90 || days>3650)throw new IllegalArgumentException("Analytics retention must be between 90 and 3650 days");this.mapper=mapper;this.days=days;
 }
 @Scheduled(cron="0 20 3 * * *",zone="Asia/Seoul") @Transactional
 public void purge(){var cutoff=OffsetDateTime.now().minusDays(days);mapper.purgeEvents(cutoff);mapper.purgeSessions(cutoff);}
}
