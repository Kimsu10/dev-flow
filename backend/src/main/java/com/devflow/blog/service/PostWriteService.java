package com.devflow.blog.service;
import com.devflow.auth.domain.Actor;
import com.devflow.blog.domain.ManagedPost;
import com.devflow.blog.dto.PostWrite;
import com.devflow.blog.mapper.PostWriteMapper;
import com.devflow.common.exception.ApiException;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
@Transactional
public class PostWriteService {
 private final PostWriteMapper mapper;
 public PostWriteService(PostWriteMapper mapper){this.mapper=mapper;}
 @Transactional(readOnly=true) public List<ManagedPost> list(boolean drafts){return drafts?mapper.drafts():mapper.list();}
 @Transactional(readOnly=true) public ManagedPost find(long id){return required(mapper.find(id));}
 private ManagedPost required(ManagedPost p){if(p==null) throw new ApiException(HttpStatus.NOT_FOUND,"POST_NOT_FOUND","Post not found");return p;}
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
  validate(p);String status=status(p.status(),"DRAFT",actor.role().equals("SYSTEM"));
  long id=mapper.create(UUID.randomUUID().toString(),new PostWrite(p.title(),p.contentMarkdown(),p.summary()==null?"":p.summary(),p.categoryId(),status),status,actor.id());
  return find(id);
 }
 public ManagedPost update(long id,PostWrite p,Actor actor){
  var old=required(mapper.lock(id));boolean agent=actor.role().equals("SYSTEM");
  if(agent && (!Objects.equals(old.authorId(),actor.id()) || !Set.of("DRAFT","IN_REVIEW").contains(old.status())))
   throw new ApiException(HttpStatus.FORBIDDEN,"FORBIDDEN","Draft is not editable by this agent");
  validate(p);
  String status=!agent && p.status()==null?old.status():status(p.status(),old.status(),agent);
  mapper.update(id,p,status);return find(id);
 }
 public ManagedPost publish(long id,boolean publish){required(mapper.lock(id));if(publish)mapper.publish(id);else mapper.unpublish(id);return find(id);}
 public void delete(long id){required(mapper.lock(id));mapper.delete(id);}
}
