package com.devflow.blog.service;

public interface PostImageStorage {
    void put(String key, byte[] bytes, String contentType);
    byte[] get(String key);
    void delete(String key);
}
