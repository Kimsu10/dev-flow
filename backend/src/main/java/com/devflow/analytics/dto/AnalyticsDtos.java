package com.devflow.analytics.dto;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.UUID;
public final class AnalyticsDtos {
 private AnalyticsDtos() {}
 public record PageView(@NotNull UUID eventId,@NotBlank @Size(max=200) String path,
  @Positive Long postId,@Size(max=2048) String referrer,@Size(max=100) String utmSource,
  @Size(max=100) String utmMedium,@Size(max=100) String utmCampaign,boolean consent) {}
 public record Collected(boolean counted) {}
 public record Counts(long visitors,long pageViews,long visits) {}
 public record Overview(LocalDate start,LocalDate end,long visitors,long pageViews,long visits,long postCount,
  double viewsPerVisitor,long todayVisitors,long yesterdayVisitors,long totalPageViews,long totalUniqueVisitors,
  Double visitorChangePercent,Double pageViewChangePercent,Double todayChangePercent,int retentionDays) {}
 public record Traffic(LocalDate date,long visitors,long pageViews) {}
 public record Referrer(String source,long visits,double percentage) {}
 public record PostStats(long id,String title,OffsetDateTime createdAt,OffsetDateTime updatedAt,long totalViews,long periodViews) {}
 public record CategoryStats(Long id,String name,long postCount,long totalViews,long periodViews,double percentage) {}
 public record VisitSession(UUID id,String visitorHash,OffsetDateTime startedAt,OffsetDateTime lastSeenAt) {}
 public record PostTarget(long id,Long categoryId) {}
}
