package com.devflow.blog.domain;

public record AiDraftRow(String id, long authorId, String question, String contentMarkdown, String sourcesJson) {}
