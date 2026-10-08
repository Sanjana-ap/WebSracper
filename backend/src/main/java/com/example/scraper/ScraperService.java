package com.example.scraper;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ScraperService {
    private static final int MAX_CHARS = 12_000; // keeps the AI prompt small and fast
    private static final int MAX_REDIRECTS = 3;
    private static final int TIMEOUT_MS = 10_000;
    private static final String USER_AGENT = "Mozilla/5.0 (compatible; AIWebScraper/1.0)";

    public record Page(String title, String text) {}

    public Page scrape(String rawUrl) {
        Page page = extract(fetch(rawUrl));
        if (page.text().length() < 50) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Couldn't find enough text on that page. It may need JavaScript to load.");
        }
        return page;
    }

    private Document fetch(String rawUrl) {
        URI uri = assertPublicUrl(rawUrl);
        try {
            for (int i = 0; i <= MAX_REDIRECTS; i++) {
                Connection.Response res = Jsoup.connect(uri.toString())
                        .userAgent(USER_AGENT)
                        .timeout(TIMEOUT_MS)
                        .followRedirects(false) // we follow manually so every hop is re-checked
                        .ignoreHttpErrors(true)
                        .ignoreContentType(true)
                        .execute();

                int status = res.statusCode();
                if (status >= 300 && status < 400 && res.hasHeader("Location")) {
                    uri = assertPublicUrl(uri.resolve(res.header("Location")).toString());
                    continue;
                }
                if (status >= 400) {
                    throw new ApiException(HttpStatus.BAD_GATEWAY,
                            "The website returned an error (HTTP " + status + ").");
                }
                String type = res.contentType() == null ? "" : res.contentType();
                if (!type.contains("text/html") && !type.contains("application/xhtml")) {
                    throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "That URL isn't an HTML web page.");
                }
                return res.parse();
            }
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "The website took too long to respond or couldn't be reached.");
        }
        throw new ApiException(HttpStatus.BAD_GATEWAY, "Too many redirects.");
    }

    /** Validates the URL and refuses anything that resolves to a private/internal address (SSRF guard). */
    static URI assertPublicUrl(String raw) {
        URI uri;
        try {
            uri = new URI(raw.trim());
        } catch (URISyntaxException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "That doesn't look like a valid URL. Include https://");
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Only http and https URLs are supported. Include https://");
        }
        if (uri.getHost() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "That doesn't look like a valid URL. Include https://");
        }
        try {
            for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
                if (isPrivate(address)) {
                    throw new ApiException(HttpStatus.BAD_REQUEST,
                            "That address points to a private network and can't be scraped.");
                }
            }
        } catch (UnknownHostException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Couldn't find that website. Check the URL and try again.");
        }
        return uri;
    }

    private static boolean isPrivate(InetAddress a) {
        if (a.isAnyLocalAddress() || a.isLoopbackAddress() || a.isLinkLocalAddress()
                || a.isSiteLocalAddress() || a.isMulticastAddress()) {
            return true;
        }
        byte[] b = a.getAddress();
        if (b.length == 4) { // 100.64.0.0/10 (carrier-grade NAT)
            return (b[0] & 0xff) == 100 && (b[1] & 0xc0) == 64;
        }
        return (b[0] & 0xfe) == 0xfc; // fc00::/7 (IPv6 unique local)
    }

    /** Pulls the title and readable text out of a parsed page. */
    static Page extract(Document doc) {
        String title = doc.title().trim();
        if (title.isEmpty()) {
            Element h1 = doc.selectFirst("h1");
            title = h1 != null ? h1.text().trim() : "";
        }
        if (title.isEmpty()) title = "Untitled page";

        doc.select("script, style, noscript, nav, footer, header, aside, form, svg, iframe").remove();

        Element root = doc.selectFirst("article");
        if (root == null) root = doc.selectFirst("main");
        if (root == null) root = doc.selectFirst("[role=main]");
        if (root == null) root = doc.body();

        String text = String.join("\n", root.select("h1, h2, h3, p, li").eachText().stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList());
        if (text.length() < 200) text = root.text().trim(); // fallback for odd markup

        return new Page(title, text.length() > MAX_CHARS ? text.substring(0, MAX_CHARS) : text);
    }
}
