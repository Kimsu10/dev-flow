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
    private Map<String,Object> citation(String url) { return Map.of("type","url_citation","url",url,"title","Primary docs"); }
    private String response(boolean search, String status, String url) throws Exception {
        var output = new ArrayList<Object>();
        if (search) output.add(Map.of("type","web_search_call","status","completed","action",Map.of("type","search")));
        output.add(Map.of("type","message","content",List.of(Map.of("type","output_text","text","# Draft\n\nEvidence citeturn0search0", "annotations",List.of(citation(url),citation(url))))));
        return json.writeValueAsString(Map.of("status",status,"output",output));
    }
    @Test void requiresActualSearchAndProviderCitationsAndMakesLinksClickable() throws Exception {
        var builder = RestClient.builder(); var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.openai.com/v1/responses")).andExpect(method(HttpMethod.POST))
            .andExpect(content().json("{\"store\":false,\"tool_choice\":\"required\",\"tools\":[{\"type\":\"web_search\"}]}"))
            .andRespond(withSuccess(response(true,"completed","https://example.com/docs"),MediaType.APPLICATION_JSON));
        var client = new OpenAiResearchClient("test-only-placeholder","test-model",json,builder);
        var result = client.research("question");
        assertEquals(1,result.sources().size());
        assertTrue(result.contentMarkdown().contains("[출처](<https://example.com/docs>)"));
        assertFalse(result.contentMarkdown().contains("cite")); server.verify();
    }
    @Test void rejectsUnsearchedIncompleteOrUnsafeResults() throws Exception {
        var client = new OpenAiResearchClient("", "unused", json, RestClient.builder());
        for (String result : List.of(response(false,"completed","https://example.com"),response(true,"incomplete","https://example.com"),response(true,"completed","javascript:alert(1)"),response(true,"completed","https://user:pass@example.com"))) {
            assertThrows(ApiException.class,()->client.parse(result));
        }
        var missingKey = assertThrows(ApiException.class,()->client.research("question"));
        assertEquals("AI_NOT_CONFIGURED", missingKey.code());
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
