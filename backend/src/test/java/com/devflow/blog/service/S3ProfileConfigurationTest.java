package com.devflow.blog.service;

import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class S3ProfileConfigurationTest {
    @TempDir Path temp;
    @Test void usesSpringConfiguredProfileInsteadOfRequiringAnOsEnvironmentVariable() throws Exception {
        Path file=temp.resolve("credentials");
        Files.writeString(file,"[editor-profile]\naws_access_key_id=test-editor-placeholder\naws_secret_access_key=test-secret-placeholder\n");
        String oldCredentials=System.getProperty("aws.sharedCredentialsFile"),oldConfig=System.getProperty("aws.configFile");
        try {
            System.setProperty("aws.sharedCredentialsFile",file.toString());System.setProperty("aws.configFile",temp.resolve("empty-config").toString());
            try(var provider=S3PostImageStorage.credentials("editor-profile")) {
                assertEquals("test-editor-placeholder",provider.resolveCredentials().accessKeyId());
            }
        } finally {
            if(oldCredentials==null)System.clearProperty("aws.sharedCredentialsFile");else System.setProperty("aws.sharedCredentialsFile",oldCredentials);
            if(oldConfig==null)System.clearProperty("aws.configFile");else System.setProperty("aws.configFile",oldConfig);
        }
    }
}
