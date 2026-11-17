package com.devflow.analytics.controller;
import com.devflow.analytics.dto.AnalyticsDtos.*;
import com.devflow.analytics.service.*;
import com.devflow.common.response.ApiResponse;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/admin/analytics")
public class AnalyticsController {
 private final AnalyticsService service;
 public AnalyticsController(AnalyticsService service){this.service=service;}
 @ModelAttribute("range") AnalyticsRange range(@RequestParam(required=false) Integer days,
  @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate start,
  @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate end){return AnalyticsRange.of(days,start,end);}
 @GetMapping("/overview") public ApiResponse<Overview> overview(@ModelAttribute("range") AnalyticsRange r){return new ApiResponse<>(service.overview(r));}
 @GetMapping("/traffic") public ApiResponse<List<Traffic>> traffic(@ModelAttribute("range") AnalyticsRange r){return new ApiResponse<>(service.traffic(r,null));}
 @GetMapping("/referrers") public ApiResponse<List<Referrer>> referrers(@ModelAttribute("range") AnalyticsRange r){return new ApiResponse<>(service.referrers(r));}
 @GetMapping("/posts/popular") public ApiResponse<List<PostStats>> popular(@ModelAttribute("range") AnalyticsRange r,@RequestParam(defaultValue="false") boolean allTime){return new ApiResponse<>(service.popular(r,allTime));}
 @GetMapping("/posts/recent") public ApiResponse<List<PostStats>> recent(@ModelAttribute("range") AnalyticsRange r){return new ApiResponse<>(service.recent(r));}
 @GetMapping("/posts/{id}/traffic") public ApiResponse<List<Traffic>> post(@PathVariable long id,@ModelAttribute("range") AnalyticsRange r){return new ApiResponse<>(service.traffic(r,id));}
 @GetMapping("/categories") public ApiResponse<List<CategoryStats>> categories(@ModelAttribute("range") AnalyticsRange r){return new ApiResponse<>(service.categories(r));}
}
