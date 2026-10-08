package com.example.scraper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

class ScraperServiceTest {

    @Test
    void extractKeepsArticleTextAndDropsNavAndScripts() {
        String html = "<html><head><title>Hello</title></head><body>"
                + "<nav>Menu links</nav><script>var x=1</script>"
                + "<article><h1>Heading</h1><p>" + "Real content here. ".repeat(20) + "</p></article>"
                + "</body></html>";
        ScraperService.Page page = ScraperService.extract(Jsoup.parse(html));
        assertEquals("Hello", page.title());
        assertTrue(page.text().contains("Real content here"));
        assertFalse(page.text().contains("Menu links"));
        assertFalse(page.text().contains("var x"));
    }

    @Test
    void blocksPrivateAddressesAndBadUrls() {
        assertThrows(ApiException.class, () -> ScraperService.assertPublicUrl("http://localhost:8080"));
        assertThrows(ApiException.class, () -> ScraperService.assertPublicUrl("http://127.0.0.1"));
        assertThrows(ApiException.class, () -> ScraperService.assertPublicUrl("ftp://example.com"));
        assertThrows(ApiException.class, () -> ScraperService.assertPublicUrl("not a url"));
    }
}
