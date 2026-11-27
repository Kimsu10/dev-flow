package com.devflow.blog.service;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;

@Tag("postgres")
@EnabledIfEnvironmentVariable(named="DEVFLOW_S3_SMOKE",matches="true")
class S3ImageSmokeTest {
    @Test void writesReadsAndRemovesOnlyItsOwnVerificationObject() {
        String bucket=System.getenv("S3_BUCKET_NAME"),region=System.getenv().getOrDefault("AWS_REGION","ap-northeast-2");
        assertNotNull(bucket,"S3 bucket must be configured");
        var date=ZonedDateTime.now(ZoneId.of("Asia/Seoul"));
        String category="DevFlowUploadVerification";
        String key=date.getYear()+"/"+category+"/"+String.format("%02d",date.getMonthValue())+"/"+category+"_"+date.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSSSSSSSS"))+".png";
        byte[] bytes=Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jVj0AAAAASUVORK5CYII=");
        boolean created=false;
        try(var storage=new S3PostImageStorage(bucket,region,System.getenv().getOrDefault("AWS_PROFILE",""))) {
            try { storage.put(key,bytes,"image/png");created=true;assertArrayEquals(bytes,storage.get(key)); }
            finally { if(created)storage.delete(key); }
        }
    }
}
