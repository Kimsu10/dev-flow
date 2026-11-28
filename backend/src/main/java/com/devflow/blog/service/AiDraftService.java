package com.devflow.blog.service;

import com.devflow.blog.domain.AiDraftRow;
import com.devflow.blog.dto.AiDraftDtos.*;
import com.devflow.blog.mapper.AiDraftMapper;
import com.devflow.common.exception.ApiException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.concurrent.Semaphore;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class AiDraftService {
    private final AiResearchClient research;
    private final AiDraftMapper mapper;
    private final ObjectMapper json;
    private final Semaphore slots = new Semaphore(2);
    public AiDraftService(AiResearchClient research, AiDraftMapper mapper, ObjectMapper json) {
        this.research = research; this.mapper = mapper; this.json = json;
    }
    public Draft generate(String question, long actorId) {
        return generate(question,actorId,false);
    }
    public Draft generateTrial(String question,long actorId) { return generate(question,actorId,true); }
    private Draft generate(String question,long actorId,boolean trial) {
        String original = question.strip();
        if (!slots.tryAcquire()) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "AI_BUSY", "진행 중인 AI 조사가 있습니다. 잠시 후 다시 시도하세요.");
        try {
            // Do not hold a database transaction/connection during external research.
            var result = trial?research.researchTrial(original):research.research(original);
            String id = UUID.randomUUID().toString();
            String sources;
            try { sources = json.writeValueAsString(result.sources()); }
            catch (Exception e) { throw new IllegalStateException("Unable to encode source metadata"); }
            mapper.create(new AiDraftRow(id, actorId, original, result.contentMarkdown(), sources));
            return new Draft(id, original, result.contentMarkdown(), result.sources());
        } finally { slots.release(); }
    }
    public Draft find(String id) {
        requireId(id);
        var row = mapper.find(id);
        if (row == null) throw new ApiException(HttpStatus.NOT_FOUND, "AI_DRAFT_NOT_FOUND", "AI 초안을 찾을 수 없습니다.");
        return new Draft(row.id(), row.question(), row.contentMarkdown(), sources(row));
    }
    public Draft findForActor(String id,long actorId) {
        requireId(id);
        var row=mapper.find(id);
        if(row==null || (row.authorId()!=actorId && !mapper.readableReference(id,actorId)))
            throw new ApiException(HttpStatus.NOT_FOUND,"AI_DRAFT_NOT_FOUND","AI 초안을 찾을 수 없습니다.");
        return new Draft(row.id(),row.question(),row.contentMarkdown(),sources(row));
    }
    public Attribution attributionForPost(long id) {
        var row = mapper.forPost(id);
        return row == null ? null : new Attribution(row.id(), row.question(), sources(row));
    }
    private List<Source> sources(AiDraftRow row) {
        try { return json.readValue(row.sourcesJson(), new TypeReference<List<Source>>() {}); }
        catch (Exception e) { throw new IllegalStateException("Invalid stored source metadata"); }
    }
    private static void requireId(String id) {
        try { if (id == null || !UUID.fromString(id).toString().equalsIgnoreCase(id)) throw new IllegalArgumentException(); }
        catch (IllegalArgumentException e) { throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_AI_DRAFT", "AI 초안 ID를 확인하세요."); }
    }
}
