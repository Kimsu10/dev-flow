package com.devflow.blog.service;
import com.devflow.auth.domain.Actor;
import com.devflow.blog.domain.ManagedPost;
import com.devflow.blog.dto.PostWrite;
import com.devflow.blog.mapper.PostWriteMapper;
import com.devflow.common.exception.ApiException;
import java.util.*;
import java.time.OffsetDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
@Transactional
public class PostWriteService {
 private final PostWriteMapper mapper;
 private final AiDraftService ai;
 public PostWriteService(PostWriteMapper mapper,AiDraftService ai){this.mapper=mapper;this.ai=ai;}
 @Transactional(readOnly=true) public List<ManagedPost> list(boolean drafts,Actor actor){return drafts?mapper.drafts(actor.id()):mapper.list(actor.id());}
 @Transactional(readOnly=true) public List<ManagedPost> mine(Actor actor){return mapper.mine(actor.id());}
 @Transactional(readOnly=true) public ManagedPost find(long id,Actor actor){return readable(mapper.find(id),actor);}
 private ManagedPost required(ManagedPost p){if(p==null) throw new ApiException(HttpStatus.NOT_FOUND,"POST_NOT_FOUND","Post not found");return p;}
 private ManagedPost readable(ManagedPost p,Actor actor){
  required(p);
  if("PRIVATE".equals(p.visibility()) && !Objects.equals(p.authorId(),actor.id()))
   throw new ApiException(HttpStatus.NOT_FOUND,"POST_NOT_FOUND","Post not found");
  return p;
 }
 private void validate(PostWrite p){
  if(p.categoryId()!=null && mapper.category(p.categoryId())==0) throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_CATEGORY","Category not found");
 }
 private String status(String status,String fallback,boolean agent){
  String value=status==null?fallback:status;
  if(!(agent?Set.of("DRAFT","IN_REVIEW"):Set.of("DRAFT","IN_REVIEW","ARCHIVED")).contains(value))
   throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_STATUS","Use the administrator publish endpoint to publish");
  return value;
 }
 public ManagedPost create(PostWrite p,Actor actor){
  boolean agent=actor.role().equals("SYSTEM");
  if(agent && p.visibility()!=null) throw new ApiException(HttpStatus.FORBIDDEN,"FORBIDDEN","Agents cannot set publication visibility");
  validateAi(p,actor);
  validate(p);
  String state=p.visibility()!=null?"PUBLISHED":status(p.status(),"DRAFT",agent);
  var normalized=new PostWrite(p.title(),p.contentMarkdown(),p.summary()==null?"":p.summary(),p.categoryId(),state,null,p.aiDraftId(),
    p.publishedAt(),p.visibility()==null?"PUBLIC":p.visibility());
  long id=mapper.create(UUID.randomUUID().toString(),normalized,state,actor.id());
  return find(id,actor);
 }
 public ManagedPost update(long id,PostWrite p,Actor actor){
  var old=readable(mapper.lock(id),actor);boolean agent=actor.role().equals("SYSTEM");
  if(agent && (!Objects.equals(old.authorId(),actor.id()) || !Set.of("DRAFT","IN_REVIEW").contains(old.status())))
   throw new ApiException(HttpStatus.FORBIDDEN,"FORBIDDEN","Draft is not editable by this agent");
  checkVersion(old,p.version());
  if(agent && p.visibility()!=null) throw new ApiException(HttpStatus.FORBIDDEN,"FORBIDDEN","Agents cannot set publication visibility");
  validateAi(p,actor);
  validate(p);
  String state=p.visibility()!=null?"PUBLISHED":!agent && p.status()==null?old.status():status(p.status(),old.status(),agent);
  String visibility=p.visibility()==null?old.visibility():p.visibility();
  Long owner=old.authorId();
  if("PRIVATE".equals(visibility)) {
   // Legacy ownerless articles are claimed only when their editor explicitly makes them private.
   if(owner==null) owner=actor.id();
   if(!Objects.equals(owner,actor.id())) throw new ApiException(HttpStatus.FORBIDDEN,"FORBIDDEN","Only the author can make this post private");
  }
  OffsetDateTime date=p.publishedAt()==null?old.publishedAt():p.publishedAt();
  var normalized=new PostWrite(p.title(),p.contentMarkdown(),p.summary(),p.categoryId(),state,p.version(),p.aiDraftId(),date,visibility);
  mapper.update(id,normalized,state,owner);return find(id,actor);
 }
 private void checkVersion(ManagedPost old,Long expected){
  if(expected!=null && expected!=old.version()) throw new ApiException(HttpStatus.CONFLICT,"POST_CONFLICT","This post was changed by another editor. Reload before saving.");
 }
 private void validateAi(PostWrite p,Actor actor){
  if(p.aiDraftId()==null) return;
  if(!actor.role().equals("ADMIN")) throw new ApiException(HttpStatus.FORBIDDEN,"FORBIDDEN","Only administrators can apply AI drafts");
  ai.findForActor(p.aiDraftId(),actor.id());
 }
 public ManagedPost publish(long id,boolean publish,Long version,Actor actor){checkVersion(readable(mapper.lock(id),actor),version);if(publish)mapper.publish(id);else mapper.unpublish(id);return find(id,actor);}
 public void delete(long id,Actor actor){readable(mapper.lock(id),actor);mapper.delete(id);}
}
