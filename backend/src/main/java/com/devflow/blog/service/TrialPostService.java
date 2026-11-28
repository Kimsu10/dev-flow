package com.devflow.blog.service;

import com.devflow.auth.domain.Actor;
import com.devflow.blog.domain.ManagedPost;
import com.devflow.blog.dto.AiDraftDtos.*;
import com.devflow.blog.dto.PostWrite;
import com.devflow.blog.mapper.PostWriteMapper;
import com.devflow.common.exception.ApiException;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@Service
public class TrialPostService {
    public record Publish(@NotBlank @Size(max=1000) String question,@AssertTrue boolean publicQuestionAccepted) {}
    public record Edit(@NotBlank @Size(max=200) String title,@NotNull @Size(max=1000000) String contentMarkdown,@Size(max=500) String summary,@NotNull @PositiveOrZero Long version) {}
    private final AiDraftService ai;private final TrialAiPolicy policy;private final PostWriteMapper posts;private final TransactionTemplate tx;
    public TrialPostService(AiDraftService ai,TrialAiPolicy policy,PostWriteMapper posts,PlatformTransactionManager transactions){this.ai=ai;this.policy=policy;this.posts=posts;this.tx=new TransactionTemplate(transactions);}
    private void user(Actor actor){if(actor==null || !"USER".equals(actor.role()))throw new ApiException(HttpStatus.FORBIDDEN,"FORBIDDEN","Trial user required");}
    public TrialAiPolicy.Policy policy(Actor actor){user(actor);return policy.view();}
    public ManagedPost publish(Publish input,Actor actor) {
        user(actor);long category=policy.view().category().id();
        policy.checkAvailable(actor.id());
        // AI generation runs without a post transaction. Only a committed publication consumes quota.
        var draft=ai.generateTrial(input.question(),actor.id());
        return tx.execute(status->{
            policy.consume(actor.id());
            String heading=draft.contentMarkdown().lines().filter(line->line.startsWith("# ")).map(line->line.substring(2).strip()).findFirst().orElse(input.question().strip().lines().findFirst().orElse("체험용 AI 글"));
            String title=heading.isBlank()?"체험용 AI 글":heading.substring(0,Math.min(200,heading.length()));
            var post=new PostWrite(title,draft.contentMarkdown(),"",category,"PUBLISHED",null,draft.id(),null,"PUBLIC");
            long id=posts.create(UUID.randomUUID().toString(),post,"PUBLISHED",actor.id());return posts.find(id);
        });
    }
    public List<ManagedPost> mine(Actor actor){user(actor);long category=policy.view().category().id();return posts.mine(actor.id()).stream().filter(p->Objects.equals(p.categoryId(),category)&&p.aiDraftId()!=null).toList();}
    private ManagedPost own(ManagedPost post,Actor actor){
        user(actor);
        if(post==null || !Objects.equals(post.authorId(),actor.id()) || !Objects.equals(post.categoryId(),policy.view().category().id()) || post.aiDraftId()==null)
            throw new ApiException(HttpStatus.NOT_FOUND,"POST_NOT_FOUND","Post not found");
        return post;
    }
    public ManagedPost find(long id,Actor actor){return own(posts.find(id),actor);}
    public Draft attribution(long id,Actor actor){return ai.findForActor(find(id,actor).aiDraftId(),actor.id());}
    public ManagedPost edit(long id,Edit input,Actor actor){
        return tx.execute(status->{
            var old=own(posts.lock(id),actor);
            if(input.version()!=old.version())throw new ApiException(HttpStatus.CONFLICT,"POST_CONFLICT","Post changed");
            if(input.contentMarkdown().contains(PostImageService.PENDING))throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_IMAGES","Trial image upload is unavailable");
            var post=new PostWrite(input.title(),input.contentMarkdown(),input.summary()==null?"":input.summary(),old.categoryId(),old.status(),old.version(),old.aiDraftId(),old.publishedAt(),old.visibility());
            posts.update(id,post,old.status(),old.authorId());return posts.find(id);
        });
    }
    public void delete(long id,Actor actor){tx.executeWithoutResult(status->{own(posts.lock(id),actor);posts.delete(id);});}
}
