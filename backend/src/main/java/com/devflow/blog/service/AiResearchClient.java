package com.devflow.blog.service;

import com.devflow.blog.dto.AiDraftDtos.Research;

public interface AiResearchClient {
    Research research(String question);
}
