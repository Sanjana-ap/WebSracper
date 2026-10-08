package com.example.scraper;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Sends scraped text to Groq's free, OpenAI-compatible chat API and returns a short summary. */
@Service
public class SummarizerService {
    private static final String SYSTEM_PROMPT =
            "You summarize web pages. Reply with a 2-sentence overview, then 3-5 key points as lines "
                    + "starting with '- '. Plain text only, no markdown headings, no preamble.";

    private final RestClient client;
    private final String apiKey;
    private final String model;

    public SummarizerService(@Value("${groq.api-key:}") String apiKey, @Value("${groq.model}") String model) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(25_000);
        this.client = RestClient.builder()
                .baseUrl("https://api.groq.com/openai/v1")
                .requestFactory(factory)
                .build();
        this.apiKey = apiKey;
        this.model = model;
    }

    public String summarize(String title, String text) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Server is missing GROQ_API_KEY. See the README.");
        }

        Map<String, Object> body = Map.of(
                "model", model,
                "temperature", 0.3,
                "max_tokens", 1500,
                "reasoning_effort", "low",
                "messages", List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content", "Page title: " + title + "\n\nPage content:\n" + text)));

        JsonNode response;
        try {
            response = client.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (HttpClientErrorException.TooManyRequests e) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "The AI service is rate-limited. Wait a few seconds and try again.");
        } catch (RestClientException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "The AI service returned an error. Please try again.");
        }

        String summary = response == null ? "" : response.path("choices").path(0).path("message").path("content").asText("").trim();
        if (summary.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "The AI service returned an empty summary.");
        }
        return summary;
    }
}
