package com.devflow.blog.service;

import com.devflow.common.exception.ApiException;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PostImageValidationTest {
    @Test void restrictsCategoryNamesToSinglePathSegment() {
        assertEquals("Spring_Boot",PostImageService.safeCategory("Spring/Boot"));
        assertEquals("미분류",PostImageService.safeCategory("  "));
        assertFalse(PostImageService.safeCategory("../a\\b").contains("/"));
    }
    @Test void detectsActualBytesAndRejectsSvgAndText() {
        assertEquals("png",PostImageService.extension(Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jVj0AAAAASUVORK5CYII=")));
        assertThrows(ApiException.class,()->PostImageService.extension("<svg onload='x'/>".getBytes()));
        assertThrows(ApiException.class,()->PostImageService.extension(new byte[3]));
    }
}
