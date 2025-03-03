package com.eulerity.hackathon.imagefinder;

import java.net.MalformedURLException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The type Image crawler factory.
 */
public class ImageCrawlerFactory {
    private static final ImageCrawlerFactory INSTANCE = new ImageCrawlerFactory();
    private final ConcurrentHashMap<String, ImageCrawler> crawlerCache = new ConcurrentHashMap<>();

    private ImageCrawlerFactory() {}

    /**
     * Gets instance.
     *
     * @return the instance
     */
    public static ImageCrawlerFactory getInstance() {
        return INSTANCE;
    }

    /**
     * get crawl instance
     * @param url url parameter
     * @return response `ImageCrawler` instance
     */
    public ImageCrawler getCrawler(String url) throws MalformedURLException {
        return crawlerCache.computeIfAbsent(url, key -> {
            try {
                return new ImageCrawler(key,1);
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * remove crawl
     * @param url target URL
     */
    public void removeCrawler(String url) {
        crawlerCache.remove(url);
    }
}
