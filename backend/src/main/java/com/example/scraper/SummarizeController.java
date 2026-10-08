package com.example.scraper;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class SummarizeController {
    public record SummarizeRequest(String url) {}

    public record SummarizeResponse(String url, String title, String summary) {}

    private final ScraperService scraper;
    private final SummarizerService summarizer;

    public SummarizeController(ScraperService scraper, SummarizerService summarizer) {
        this.scraper = scraper;
        this.summarizer = summarizer;
    }

    @PostMapping("/summarize")
    public SummarizeResponse summarize(@RequestBody SummarizeRequest request) {
        String url = request == null || request.url() == null ? "" : request.url().trim();
        if (url.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Please enter a URL.");
        }
        ScraperService.Page page = scraper.scrape(url);
        String summary = summarizer.summarize(page.title(), page.text());
        return new SummarizeResponse(url, page.title(), summary);
    }
}
