package com.devflow.blog.dto;

import jakarta.validation.constraints.*;
import java.util.List;

public final class AiDraftDtos {
    private AiDraftDtos() {}
    public record Request(@NotBlank @Size(max=2000) String question, @AssertTrue boolean publicQuestionAccepted) {}
    public record Source(String title, String url) {}
    public record Attribution(String draftId, String question, List<Source> sources) {}
    public record Draft(String id, String question, String contentMarkdown, List<Source> sources) {}
    public record Research(String contentMarkdown, List<Source> sources) {}
}
