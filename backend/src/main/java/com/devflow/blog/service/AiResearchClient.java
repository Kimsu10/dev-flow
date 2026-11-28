package com.devflow.blog.service;

import com.devflow.blog.dto.AiDraftDtos.Research;

public interface AiResearchClient {
    Research research(String question);
    default Research researchTrial(String question) { throw new UnsupportedOperationException("Trial model must be explicitly configured"); }
}
