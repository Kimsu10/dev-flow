package com.devflow.blog.service;

import com.devflow.auth.domain.Actor;
import com.devflow.blog.domain.ManagedPost;
import com.devflow.blog.dto.PostWrite;
import com.devflow.blog.mapper.*;
import com.devflow.common.exception.ApiException;
import java.io.IOException;
import java.text.Normalizer;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import org.slf4j.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.web.multipart.MultipartFile;

@Service
public class PostImageService {
    public static final String PENDING="https://devflow.invalid/pending-images/";
    private static final Pattern IDS=Pattern.compile(Pattern.quote(PENDING)+"([0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12})");
    private static final AtomicLong LAST_TIME=new AtomicLong();
    private static final Logger log=LoggerFactory.getLogger(PostImageService.class);
    private final PostWriteService posts; private final PostImageMapper images; private final BlogMapper blog; private final PostImageStorage storage;
    public PostImageService(PostWriteService posts,PostImageMapper images,BlogMapper blog,PostImageStorage storage) { this.posts=posts;this.images=images;this.blog=blog;this.storage=storage; }
    record FileData(String id,byte[] bytes,String type,String extension) {}
    @Transactional
    public ManagedPost save(Long id,PostWrite post,List<String> ids,List<MultipartFile> files,Actor actor) throws IOException {
        if(!"ADMIN".equals(actor.role())) throw new ApiException(HttpStatus.FORBIDDEN,"FORBIDDEN","Admin required");
        if(ids.size()!=files.size() || ids.isEmpty() || ids.size()>10 || new HashSet<>(ids).size()!=ids.size()) throw invalid();
        var expected=new HashSet<String>();var matcher=IDS.matcher(post.contentMarkdown());while(matcher.find())expected.add(matcher.group(1));
        if(!expected.equals(new HashSet<>(ids))) throw invalid();
        var data=new ArrayList<FileData>();long total=0;
        for(int i=0;i<ids.size();i++) {
            try { if(!UUID.fromString(ids.get(i)).toString().equals(ids.get(i))) throw invalid(); } catch(IllegalArgumentException e) { throw invalid(); }
            MultipartFile f=files.get(i);total+=f.getSize();if(f.isEmpty() || f.getSize()>5*1024*1024 || total>25*1024*1024) throw invalid();
            byte[] bytes=f.getBytes();String ext=extension(bytes);String type=switch(ext){case "jpg"->"image/jpeg";default->"image/"+ext;};
            if(!type.equals(f.getContentType()))throw invalid();
            data.add(new FileData(ids.get(i),bytes,type,ext));
        }
        // Database checks, ownership and optimistic locking happen before any S3 writes.
        var saved=id==null?posts.create(post,actor):posts.update(id,post,actor);
        String category=saved.categoryId()==null?"미분류":images.category(saved.categoryId());
        var date=saved.createdAt().atZoneSameInstant(ZoneId.of("Asia/Seoul"));
        String prefix=date.getYear()+"/"+safeCategory(category)+"/"+String.format(Locale.ROOT,"%02d",date.getMonthValue())+"/";
        var uploaded=new ArrayList<String>();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
            public void afterCompletion(int status){if(status!=STATUS_COMMITTED)for(String key:uploaded)try{storage.delete(key);}catch(Exception e){log.warn("S3 image rollback cleanup failed; manual reconciliation may be required");}}
        });
        String markdown=post.contentMarkdown();
        for(var file:data) {
            String key=prefix+safeCategory(category)+"_"+DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS").format(Instant.ofEpochMilli(LAST_TIME.updateAndGet(last->Math.max(System.currentTimeMillis(),last+1))).atZone(ZoneId.of("Asia/Seoul")))+"."+file.extension();
            try { storage.put(key,file.bytes(),file.type());uploaded.add(key); }
            catch(ApiException e){throw e;}
            catch(Exception e){log.warn("S3 image upload failed; provider details suppressed");throw new ApiException(HttpStatus.BAD_GATEWAY,"IMAGE_UPLOAD_FAILED","Image upload failed");}
            images.insert(new PostImageMapper.Image(file.id(),saved.id(),key,file.type(),file.bytes().length));
            markdown=markdown.replace(PENDING+file.id(),"/api/posts/"+saved.id()+"/images/"+file.id());
        }
        images.content(saved.id(),markdown);
        return posts.find(saved.id(),actor);
    }
    public record Download(byte[] bytes,String type) {}
    @Transactional(readOnly=true)
    public Download read(long postId,String id,Actor actor) {
        if(blog.findPost(postId,actor==null?null:actor.id(),actor!=null && "ADMIN".equals(actor.role()))==null)throw new ApiException(HttpStatus.NOT_FOUND,"POST_NOT_FOUND","Post not found");
        var image=images.find(postId,id);if(image==null)throw new ApiException(HttpStatus.NOT_FOUND,"IMAGE_NOT_FOUND","Image not found");
        try{return new Download(storage.get(image.objectKey()),image.contentType());}
        catch(Exception e){throw new ApiException(HttpStatus.BAD_GATEWAY,"IMAGE_DOWNLOAD_FAILED","Image unavailable");}
    }
    static String safeCategory(String name) {
        String value=Normalizer.normalize(name==null?"미분류":name,Normalizer.Form.NFC).replaceAll("[^\\p{L}\\p{N} _-]","_").strip();
        return value.isBlank()?"미분류":value.substring(0,Math.min(80,value.length()));
    }
    static String extension(byte[] b) {
        if(b.length<12)throw invalid();
        if(b[0]==(byte)137 && b[1]==80 && b[2]==78 && b[3]==71 && b[4]==13 && b[5]==10 && b[6]==26 && b[7]==10)return "png";
        if(b[0]==(byte)255 && b[1]==(byte)216 && b[2]==(byte)255)return "jpg";
        String first=new String(b,0,4,java.nio.charset.StandardCharsets.US_ASCII);
        if((new String(b,0,6,java.nio.charset.StandardCharsets.US_ASCII)).matches("GIF8[79]a"))return "gif";
        if(first.equals("RIFF") && new String(b,8,4,java.nio.charset.StandardCharsets.US_ASCII).equals("WEBP"))return "webp";
        throw invalid();
    }
    private static ApiException invalid(){return new ApiException(HttpStatus.BAD_REQUEST,"INVALID_IMAGES","Invalid pending images");}
}
