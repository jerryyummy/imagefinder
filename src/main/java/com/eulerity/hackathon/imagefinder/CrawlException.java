package com.eulerity.hackathon.imagefinder;

/**
 * The type Crawl exception.
 */
public class CrawlException extends Exception {
    /**
     * Instantiates a new Crawl exception.
     *
     * @param message the message
     */
    public CrawlException(String message) {
        super(message);
        Logger.getInstance().error("CrawlException: " + message);
    }

    /**
     * Instantiates a new Crawl exception.
     *
     * @param message the message
     * @param cause   the cause
     */
    public CrawlException(String message, Throwable cause) {
        super(message, cause);
        Logger.getInstance().error("CrawlException: " + message + " | Cause: " + cause.getMessage());
    }
}
