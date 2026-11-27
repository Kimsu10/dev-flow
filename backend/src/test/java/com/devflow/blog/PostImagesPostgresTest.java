package com.devflow.blog;

import com.devflow.auth.config.SecurityConfig;
import com.devflow.auth.domain.Actor;
import com.devflow.auth.mapper.AccountMapper;
import com.devflow.blog.service.PostImageStorage;
import com.fasterxml.jackson.databind.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Tag("postgres") @SpringBootTest @AutoConfigureMockMvc(print=MockMvcPrint.NONE)
class PostImagesPostgresTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcTemplate jdbc; @Autowired AccountMapper accounts;
    @MockitoBean PostImageStorage storage;
    Actor author;long category;
    static final byte[] PNG=Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jVj0AAAAASUVORK5CYII=");
    @BeforeEach void fixture(){String name="images-"+UUID.randomUUID();author=new Actor(accounts.create(name,name+"@example.test","unused","ADMIN"),name,"ADMIN");category=jdbc.queryForObject("INSERT INTO blog.categories(name,slug) VALUES(?,?) RETURNING id",Long.class,"Image/Category "+name,name);}
    @AfterEach void cleanup(){jdbc.update("DELETE FROM blog.post_images WHERE post_id IN (SELECT id FROM blog.posts WHERE author_id=?)",author.id());jdbc.update("DELETE FROM blog.posts WHERE author_id=?",author.id());jdbc.update("DELETE FROM blog.categories WHERE id=?",category);jdbc.update("DELETE FROM blog.accounts WHERE id=?",author.id());}
    org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder request(List<String> ids,String visibility) throws Exception {
        String content=ids.stream().map(id->"![image](<https://devflow.invalid/pending-images/"+id+">)").reduce("",(a,b)->a+"\n"+b);
        var body=Map.of("title","Image post","contentMarkdown",content,"categoryId",category,"visibility",visibility);
        var request=multipart("/api/admin/posts/with-images")
            .file(new MockMultipartFile("post","post.json","application/json",json.writeValueAsBytes(body)))
            .file(new MockMultipartFile("imageIds","ids.json","application/json",json.writeValueAsBytes(ids)));
        for(String id:ids)request.file(new MockMultipartFile("images",id,"image/png",PNG));
        return request;
    }
    @Test void saveCommitsImageAndReplacesPendingUrlWhilePrivateAccessRemainsProtected() throws Exception {
        String id=UUID.randomUUID().toString();
        var result=mvc.perform(request(List.of(id),"PRIVATE").with(authentication(SecurityConfig.authentication(author))).with(csrf())).andExpect(status().isOk());
        var post=json.readTree(result.andReturn().getResponse().getContentAsString()).path("data");long postId=post.path("id").asLong();
        String url="/api/posts/"+postId+"/images/"+id;
        assertTrue(post.path("contentMarkdown").asText().contains(url));assertFalse(post.path("contentMarkdown").asText().contains("devflow.invalid"));
        String key=jdbc.queryForObject("SELECT object_key FROM blog.post_images WHERE id=CAST(? AS uuid)",String.class,id);
        assertTrue(key.matches("\\d{4}/Image_Category images-[^/]+/\\d{2}/Image_Category images-[^/]+_\\d{17}\\.png"));
        verify(storage).put(eq(key),aryEq(PNG),eq("image/png"));
        when(storage.get(key)).thenReturn(PNG);
        mvc.perform(get(url)).andExpect(status().isNotFound());
        mvc.perform(get(url).with(authentication(SecurityConfig.authentication(author)))).andExpect(status().isOk()).andExpect(content().bytes(PNG));
        jdbc.update("UPDATE blog.posts SET visibility='PUBLIC' WHERE id=?",postId);
        mvc.perform(get(url)).andExpect(status().isOk());
        jdbc.update("UPDATE blog.posts SET deleted_at=CURRENT_TIMESTAMP WHERE id=?",postId);
        mvc.perform(get(url)).andExpect(status().isNotFound());
    }
    @Test void uploadFailureRollsBackPostAndCleansAlreadyUploadedObjects() throws Exception {
        doNothing().doThrow(new IllegalStateException("test failure")).when(storage).put(anyString(),any(),anyString());
        mvc.perform(request(List.of(UUID.randomUUID().toString(),UUID.randomUUID().toString()),"PUBLIC")
            .with(authentication(SecurityConfig.authentication(author))).with(csrf())).andExpect(status().isBadGateway());
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM blog.posts WHERE author_id=?",Integer.class,author.id()));
        verify(storage,times(1)).delete(anyString());
    }
    @Test void rejectsAnonymousCsrfAndSpoofedFilesBeforeS3() throws Exception {
        var ids=List.of(UUID.randomUUID().toString());
        mvc.perform(request(ids,"PUBLIC").with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(request(ids,"PUBLIC").with(authentication(SecurityConfig.authentication(author)))).andExpect(status().isForbidden());
        var spoof=multipart("/api/admin/posts/with-images")
            .file(new MockMultipartFile("post","post.json","application/json",json.writeValueAsBytes(Map.of("title","bad","contentMarkdown","https://devflow.invalid/pending-images/"+ids.get(0)))))
            .file(new MockMultipartFile("imageIds","ids.json","application/json",json.writeValueAsBytes(ids)))
            .file(new MockMultipartFile("images","fake.png","image/png","<svg onload='bad'/>".getBytes()));
        mvc.perform(spoof.with(authentication(SecurityConfig.authentication(author))).with(csrf())).andExpect(status().isBadRequest());
        // A JSON-only save must never publish a browser-only image reference.
        mvc.perform(post("/api/admin/posts").with(authentication(SecurityConfig.authentication(author))).with(csrf()).contentType("application/json")
            .content(json.writeValueAsString(Map.of("title","bad","contentMarkdown","https://devflow.invalid/pending-images/"+ids.get(0)))))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(storage);
    }
}
