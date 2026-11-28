package com.devflow.blog.service;

import com.devflow.common.exception.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@ExtendWith(OutputCaptureExtension.class)
class OpenAiResearchClientTest {
    private final ObjectMapper json = new ObjectMapper();
    @Test void distinguishesTokenExhaustionFromIncompleteResponsesAndDoesNotLogDraftText(CapturedOutput output) throws Exception {
        var client=new OpenAiResearchClient("", "unused", json,RestClient.builder());
        String incomplete=json.writeValueAsString(Map.of("status","incomplete","incomplete_details",Map.of("reason","max_output_tokens"),"usage",Map.of("output_tokens",2000,"output_tokens_details",Map.of("reasoning_tokens",1900)),"output",List.of(Map.of("text","private draft"))));
        assertEquals("AI_OUTPUT_LIMIT",assertThrows(ApiException.class,()->client.parse(incomplete)).code());
        assertTrue(output.getAll().contains("reason=OUTPUT_TOKEN_LIMIT, outputTokens=2000, reasoningTokens=1900"));
        assertFalse(output.getAll().contains("private draft"));
        assertEquals("AI_UNVERIFIED",assertThrows(ApiException.class,()->client.parse(response(false,"in_progress","https://example.com"))).code());
        assertTrue(output.getAll().contains("reason=INCOMPLETE_RESPONSE"));
    }
    @Test void trialResearchUsesOnlyServerSelectedLowCostModelAndBoundedTokensAndSearch() throws Exception {
        var policy=org.mockito.Mockito.mock(TrialAiPolicy.class);
        org.mockito.Mockito.when(policy.model()).thenReturn("gpt-6-luna");org.mockito.Mockito.when(policy.outputTokens()).thenReturn(2000);
        var builder=RestClient.builder();var server=MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.openai.com/v1/responses")).andExpect(content().json("{\"model\":\"gpt-6-luna\",\"max_output_tokens\":2000,\"max_tool_calls\":1,\"tools\":[{\"type\":\"web_search\",\"search_context_size\":\"low\"}],\"reasoning\":{\"effort\":\"low\"},\"include\":[\"web_search_call.action.sources\"]}"))
            .andRespond(withSuccess(response(true,"completed","https://example.com/docs"),MediaType.APPLICATION_JSON));
        var client=new OpenAiResearchClient("test-key","gpt-6.2-sol",json,builder,policy);
        assertFalse(client.researchTrial("Trial question").sources().isEmpty());server.verify();
    }
    private Map<String,Object> citation(String url) { return Map.of("type","url_citation","url",url,"title","Primary docs"); }
    private String response(boolean search, String status, String url) throws Exception {
        var output = new ArrayList<Object>();
        if (search) output.add(Map.of("type","web_search_call","status","completed","action",Map.of("type","search")));
        output.add(Map.of("type","message","content",List.of(Map.of("type","output_text","text","# Draft\n\nEvidence citeturn0search0", "annotations",List.of(citation(url),citation(url))))));
        return json.writeValueAsString(Map.of("status",status,"output",output));
    }
    private String markdownResponse(String text, String searchStatus, List<?> sources) throws Exception {
        // Messages may appear before the search item; collection must not depend on output order.
        return json.writeValueAsString(Map.of("status","completed","output",List.of(
            Map.of("type","message","content",List.of(Map.of("type","output_text","text",text))),
            Map.of("type","web_search_call","status",searchStatus,"action",Map.of("type","search","sources",sources)))));
    }
    @Test void publishesOriginalWritingWithoutForcingSearchOrInventingSources() throws Exception {
        var builder=RestClient.builder();var server=MockRestServiceServer.bindTo(builder).build();
        String story="# Rabbit and Goose\n"+String.join("\n",java.util.stream.IntStream.rangeClosed(1,10).mapToObj(i->"Story line "+i).toList());
        String response=json.writeValueAsString(Map.of("status","completed","output",List.of(
            Map.of("type","message","content",List.of(Map.of("type","output_text","text",story))))));
        server.expect(requestTo("https://api.openai.com/v1/responses"))
            .andExpect(content().json("{\"tool_choice\":\"auto\"}"))
            .andRespond(withSuccess(response,MediaType.APPLICATION_JSON));
        var result=new OpenAiResearchClient("test-key","test-model",json,builder).research("Write a 10-line story about a rabbit and a goose");
        assertEquals(story,result.contentMarkdown());assertTrue(result.sources().isEmpty());server.verify();
    }
    @Test void acceptsMarkdownCitationsOnlyWhenTheyMatchCompletedSearchSources() throws Exception {
        var builder=RestClient.builder();var server=MockRestServiceServer.bindTo(builder).build();
        String draft="# Draft\n\nSee [Primary docs](<https://example.com/docs>) and [again](https://example.com/docs).";
        server.expect(requestTo("https://api.openai.com/v1/responses"))
            .andExpect(content().json("{\"include\":[\"web_search_call.action.sources\"]}"))
            .andRespond(withSuccess(markdownResponse(draft,"completed",List.of(
                Map.of("type","url","url","https://example.com/uncited"),
                Map.of("type","url","url","https://example.com/docs"))),MediaType.APPLICATION_JSON));
        var result=new OpenAiResearchClient("test-key","test-model",json,builder).research("question");
        assertEquals(draft,result.contentMarkdown());
        assertEquals(1,result.sources().size());
        assertEquals("https://example.com/docs",result.sources().getFirst().url());
        assertEquals("Primary docs",result.sources().getFirst().title());server.verify();
    }
    @Test void doesNotInventAttributionForUnmatchedUncitedAndNonLinkSearchSources(CapturedOutput output) throws Exception {
        var client=new OpenAiResearchClient("","unused",json,RestClient.builder());
        var sources=List.of(Map.of("type","url","url","https://example.com/docs"));
        for(String draft:List.of("# Private draft with no citation",
            "[Forged](https://example.com/invented)","[Prefix](https://example.com/docs/forged)",
            "https://example.com/docs","![Image](https://example.com/docs)")) {
            assertTrue(client.parse(markdownResponse(draft,"completed",sources)).sources().isEmpty());
        }
        assertFalse(output.getAll().contains("reason=CITATIONS_NOT_VERIFIED"));
        assertFalse(output.getAll().contains("Private draft"));
    }
    @Test void excludesAttributionFromFailedSearchesOrUnsafeSources() throws Exception {
        var client=new OpenAiResearchClient("","unused",json,RestClient.builder());
        assertTrue(client.parse(markdownResponse("[Docs](https://example.com/docs)","failed",
            List.of(Map.of("type","url","url","https://example.com/docs")))).sources().isEmpty());
        assertTrue(client.parse(markdownResponse("[Docs](https://example.com/docs)","completed",
            List.of(Map.of("type","not_url","url","https://example.com/docs")))).sources().isEmpty());
        assertTrue(client.parse(markdownResponse("[Docs](<https://user:pass@example.com/docs>)","completed",
            List.of(Map.of("type","url","url","https://user:pass@example.com/docs")))).sources().isEmpty());
    }
    @Test void retainsProviderCitationsAndMakesLinksClickable() throws Exception {
        var builder = RestClient.builder(); var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.openai.com/v1/responses")).andExpect(method(HttpMethod.POST))
            .andExpect(content().json("{\"store\":false,\"tool_choice\":\"auto\",\"tools\":[{\"type\":\"web_search\"}]}"))
            .andRespond(withSuccess(response(true,"completed","https://example.com/docs"),MediaType.APPLICATION_JSON));
        var client = new OpenAiResearchClient("test-only-placeholder","test-model",json,builder);
        var result = client.research("question");
        assertEquals(1,result.sources().size());
        assertTrue(result.contentMarkdown().contains("[출처](<https://example.com/docs>)"));
        assertFalse(result.contentMarkdown().contains("cite")); server.verify();
    }
    @Test void rejectsIncompleteOrEmptyDraftsAndFiltersUnsafeCitations() throws Exception {
        var client = new OpenAiResearchClient("", "unused", json, RestClient.builder());
        assertThrows(ApiException.class,()->client.parse(response(true,"incomplete","https://example.com")));
        assertThrows(ApiException.class,()->client.parse(markdownResponse("  ","completed",List.of())));
        for (String url : List.of("javascript:alert(1)","https://user:pass@example.com")) {
            var result=client.parse(response(true,"completed",url));
            assertTrue(result.sources().isEmpty());assertFalse(result.contentMarkdown().contains("cite"));
        }
        var missingKey = assertThrows(ApiException.class,()->client.research("question"));
        assertEquals("AI_NOT_CONFIGURED", missingKey.code());
    }
    @Test void retainsBothProviderAnnotationsAndAdditionalCitedSearchSources() throws Exception {
        var client=new OpenAiResearchClient("","unused",json,RestClient.builder());
        var root=json.readTree(markdownResponse("[Extra docs](https://example.com/extra)","completed",
            List.of(Map.of("type","url","url","https://example.com/extra"))));
        ((com.fasterxml.jackson.databind.node.ObjectNode)root.path("output").get(0).path("content").get(0))
            .set("annotations",json.valueToTree(List.of(citation("https://example.com/docs"))));
        assertEquals(List.of("https://example.com/docs","https://example.com/extra"),
            client.parse(json.writeValueAsString(root)).sources().stream().map(s->s.url()).toList());
    }
    @Test void providerFailuresNeverReturnProviderResponseBodies(CapturedOutput output) {
        var builder = RestClient.builder(); var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.openai.com/v1/responses")).andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("provider-internal-detail"));
        var client = new OpenAiResearchClient("test-only-placeholder","test-model",json,builder);
        var error = assertThrows(ApiException.class,()->client.research("question"));
        assertEquals("AI_UNAVAILABLE",error.code()); assertFalse(error.getMessage().contains("provider-internal-detail"));
        assertTrue(output.getAll().contains("HTTP 401, code=unclassified"));
        assertFalse(output.getAll().contains("provider-internal-detail"));
        assertFalse(output.getAll().contains("test-only-placeholder"));
        server.verify();
    }
    @Test void logsOnlyRecognizedCodesWithoutProviderMessagesOrQuestion(CapturedOutput output) {
        var builder = RestClient.builder(); var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.openai.com/v1/responses")).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
            .contentType(MediaType.APPLICATION_JSON).body("{\"error\":{\"code\":\"insufficient_quota\",\"message\":\"sensitive-provider-detail\"}}"));
        var client = new OpenAiResearchClient("test-only-placeholder", "test-model", json, builder);
        assertThrows(ApiException.class, () -> client.research("private-question"));
        assertTrue(output.getAll().contains("HTTP 429, code=insufficient_quota"));
        assertFalse(output.getAll().contains("sensitive-provider-detail"));
        assertFalse(output.getAll().contains("private-question"));
        server.verify();
    }
}
