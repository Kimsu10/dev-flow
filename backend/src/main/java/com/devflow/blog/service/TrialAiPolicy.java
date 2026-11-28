package com.devflow.blog.service;
import com.devflow.blog.dto.BlogResponses.Category;
import com.devflow.blog.mapper.TrialAiMapper;
import com.devflow.common.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class TrialAiPolicy {
    private final TrialAiMapper mapper;
    private final String model;
    private final int outputTokens,dailyRequests;
    public TrialAiPolicy(TrialAiMapper mapper,@Value("${TEST_USER_OPENAI_MODEL:gpt-6-luna}") String model,
        @Value("${TEST_USER_MAX_OUTPUT_TOKENS:2000}") int tokens,@Value("${TEST_USER_DAILY_AI_REQUESTS:50}") int daily) {
        this.mapper=mapper;this.model=model.strip();this.outputTokens=Math.clamp(tokens,256,3000);this.dailyRequests=Math.clamp(daily,1,50);
    }
    public record Policy(Category category,String model,int maxQuestionLength,int maxOutputTokens,int dailyRequests) {}
    public Policy view(){var category=mapper.category();if(category==null)throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"TRIAL_NOT_CONFIGURED","Trial category unavailable");return new Policy(Category.from(category),model,1000,outputTokens,dailyRequests);}
    public String model(){return model;}
    public int outputTokens(){return outputTokens;}
    public void checkAvailable(long accountId){if(mapper.used(accountId)>=dailyRequests)throw exhausted();}
    // The counter commits only with the published post; failed writes roll back both.
    @Transactional(propagation=Propagation.MANDATORY)
    public void consume(long accountId){if(mapper.consume(accountId,dailyRequests)==null)throw exhausted();}
    private ApiException exhausted(){return new ApiException(HttpStatus.TOO_MANY_REQUESTS,"TRIAL_DAILY_LIMIT","Daily trial allowance exhausted");}
}
