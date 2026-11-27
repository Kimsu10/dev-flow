package com.devflow.blog.service;

import com.devflow.common.exception.ApiException;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

@Component
public class S3PostImageStorage implements PostImageStorage, AutoCloseable {
    private static final Logger log=LoggerFactory.getLogger(S3PostImageStorage.class);
    private final String bucket, region, profile;
    private S3Client client;
    private DefaultCredentialsProvider credentials;
    @Autowired
    public S3PostImageStorage(@Value("${S3_BUCKET_NAME:}") String bucket,@Value("${AWS_REGION:ap-northeast-2}") String region,@Value("${AWS_PROFILE:}") String profile) { this.bucket=bucket.strip();this.region=region.strip();this.profile=profile.strip(); }
    public S3PostImageStorage(String bucket,String region) { this(bucket,region,System.getenv().getOrDefault("AWS_PROFILE","")); }
    private synchronized S3Client client() {
        if(bucket.isBlank()) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"IMAGE_STORAGE_NOT_CONFIGURED","Configure S3_BUCKET_NAME");
        if(client==null) { credentials=credentials(profile); client=S3Client.builder().region(Region.of(region)).credentialsProvider(credentials)
            .httpClientBuilder(UrlConnectionHttpClient.builder().connectionTimeout(Duration.ofSeconds(5)).socketTimeout(Duration.ofSeconds(20)))
            .overrideConfiguration(c->c.apiCallTimeout(Duration.ofSeconds(45))).build(); }
        try { credentials.resolveCredentials(); }
        catch(SdkClientException e){
            String message=e.getMessage()==null?"":e.getMessage().toLowerCase(java.util.Locale.ROOT);
            String reason=message.contains("expired")?"LOGIN_EXPIRED":message.contains("no credentials") || message.contains("not found")?"PROFILE_OR_SESSION_UNAVAILABLE":"CREDENTIALS_UNAVAILABLE";
            log.warn("S3 credentials unavailable: profileConfigured={}, reason={}",!profile.isBlank(),reason);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"IMAGE_STORAGE_AUTH_REQUIRED","AWS credentials are missing or expired");
        }
        return client;
    }
    static DefaultCredentialsProvider credentials(String profile) {
        var builder=DefaultCredentialsProvider.builder();
        if(!profile.isBlank())builder.profileName(profile);
        return builder.build();
    }
    public void put(String key,byte[] bytes,String type) {
        client().putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType(type).ifNoneMatch("*").build(),RequestBody.fromBytes(bytes));
    }
    public byte[] get(String key) { return client().getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key).build()).asByteArray(); }
    public void delete(String key) { client().deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build()); }
    public synchronized void close() { if(client!=null) client.close();if(credentials!=null)credentials.close(); }
}
