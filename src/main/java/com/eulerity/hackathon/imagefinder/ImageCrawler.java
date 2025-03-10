package com.eulerity.hackathon.imagefinder;

import com.google.gson.Gson;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.Semaphore;

/**
 * The type Image crawler.
 */
public class ImageCrawler {
    private static final Semaphore semaphore = new Semaphore(20); // limit max concurrency 20
    private final Set<String> visitedPages = Collections.synchronizedSet(new HashSet<>());
    private final Set<String> imageUrls = Collections.synchronizedSet(new HashSet<>());
    private final Set<String> logoImages = Collections.synchronizedSet(new HashSet<>());
    private final String baseDomain;
    private final int maxDepth;
    private volatile boolean isCrawling = false;
    private static final Map<String, String> crawlStatus = Collections.synchronizedMap(new HashMap<>());

    /**
     * Instantiates a new Image crawler.
     *
     * @param startUrl the start url
     * @param maxDepth the max depth
     * @throws MalformedURLException the malformed url exception
     */
    public ImageCrawler(String startUrl, int maxDepth) throws MalformedURLException {
        this.baseDomain = new URL(startUrl).getHost();
        this.maxDepth = maxDepth;
    }

    /**
     * Start crawling.
     *
     * @param url   the url
     * @param depth the depth
     */
    public void startCrawling(String url, int depth) {
        if (depth > maxDepth || visitedPages.contains(url) || !isAllowedByRobots(url)) return;

        visitedPages.add(url);
        crawlStatus.put(url, "in_progress");
        isCrawling = true;

        semaphore.acquireUninterruptibly();
        CrawlingThreadPool.getInstance().submitTask(() -> {
            try {
                crawl(url, depth);
                crawlStatus.put(url, "completed");
            } catch (CrawlException e) {
                Logger.getInstance().error("[CrawlException] " + e.getMessage());
                crawlStatus.put(url, "error");
            } finally {
                isCrawling = false;
                crawlStatus.remove(url);
                semaphore.release();
            }
        });
    }

    /**
     * Gets crawl status.
     *
     * @param url the url
     * @return the crawl status
     */
    public String getCrawlStatus(String url) {
        return crawlStatus.getOrDefault(url, "not_started");
    }


    /**
     * Is crawling boolean.
     *
     * @return the boolean
     */
    public boolean isCrawling() {
        return isCrawling;
    }

    private void crawl(String url, int depth) throws CrawlException {
        try {
            if (Thread.currentThread().isInterrupted()) {
                System.out.println("[Cancelled] Crawl interrupted before starting: " + url);
                return;
            }

            Logger.getInstance().info("[Crawling] " + url);
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0")
                    .timeout(5000)
                    .get();

            Elements images = doc.select("img");
            for (Element img : images) {
                if (Thread.currentThread().isInterrupted()) {
                    System.out.println("[Cancelled] Crawl interrupted while processing images: " + url);
                    return;
                }

                String src = img.absUrl("src");
                if (!src.isEmpty() && isValidImage(src)) {
                    imageUrls.add(src);
                    if (isLogoImage(src)) {
                        logoImages.add(src);
                    }
                }
            }

            if (depth < maxDepth) {
                Elements links = doc.select("a[href]");
                for (Element link : links) {
                    if (Thread.currentThread().isInterrupted()) {
                        System.out.println("[Cancelled] Crawl interrupted while processing links: " + url);
                        return;
                    }

                    String nextUrl = link.absUrl("href");
                    if (isSameDomain(nextUrl) && !visitedPages.contains(nextUrl)) {
                        startCrawling(nextUrl, depth + 1);
                    }
                }
            }

        } catch (IOException e) {
            throw new CrawlException("Failed to crawl URL: " + url, e);
        } finally {
            crawlStatus.put(url, "completed");
            isCrawling = false;
        }
    }


    /**
     * parse robots.txt
     */
    private boolean isAllowedByRobots(String url) {
        try {
            String robotsTxtUrl = new URL(url).getProtocol() + "://" + new URL(url).getHost() + "/robots.txt";
            Document doc = Jsoup.connect(robotsTxtUrl).ignoreContentType(true).get();
            String content = doc.body().text().toLowerCase();

            // parse all disallow rules
            Scanner scanner = new Scanner(content);
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine().trim();
                if (line.startsWith("disallow:")) {
                    String disallowedPath = line.replace("disallow:", "").trim();
                    if (url.contains(disallowedPath)) {
                        return false;
                    }
                }
            }
            return true;
        } catch (IOException e) {
            return true;
        }
    }

    /**
     * check if image is valid
     */
    private boolean isValidImage(String imageUrl) {
        try {
            URL url = new URL(imageUrl);
            URLConnection connection = url.openConnection();
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);

            String contentType = connection.getContentType();
            int contentLength = connection.getContentLength();

            List<String> allowedTypes = Arrays.asList("image/jpeg", "image/png", "image/webp");

            if (!allowedTypes.contains(contentType)) {
                return false;
            }

            if (contentLength < 10240 || contentLength > 5 * 1024 * 1024) {
                return false;
            }

            BufferedImage img = ImageIO.read(url);
            return img != null;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * use url to figure out Logo
     */
    private boolean isLogoImage(String imageUrl) {
        String lowerCaseUrl = imageUrl.toLowerCase();
        String[] logoKeywords = {"logo", "brand", "favicon", "icon", "corporate", "symbol"};

        for (String keyword : logoKeywords) {
            if (lowerCaseUrl.contains(keyword)) {
                return true;
            }
        }

        return false;
    }

    /**
     * download image
     */
    private String downloadImage(String imageUrl) throws IOException {
        URL url = new URL(imageUrl);
        String filePath = "temp_images/" + imageUrl.substring(imageUrl.lastIndexOf("/") + 1);
        Files.copy(url.openStream(), Paths.get(filePath));
        return filePath;
    }

    /**
     * check URL is the same domain
     */
    private boolean isSameDomain(String url) {
        try {
            return new URL(url).getHost().equals(baseDomain);
        } catch (MalformedURLException e) {
            return false;
        }
    }

    /**
     * Gets image urls as json.
     *
     * @return the image urls as json
     */
    public String getImageUrlsAsJson() {
        synchronized (imageUrls) {
            return new Gson().toJson(new ArrayList<>(imageUrls));
        }
    }

    /**
     * Gets logo urls as json.
     *
     * @return the logo urls as json
     */
    public String getLogoUrlsAsJson() {
        synchronized (logoImages) {
            return new Gson().toJson(new ArrayList<>(logoImages));
        }
    }
}
