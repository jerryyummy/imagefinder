package com.eulerity.hackathon.imagefinder;


/**
 * Class to represent the crawl result in JSON format.
 */
public class CrawlResult {
    private final String status;
    private final String message;
    private final String images;
    private final String logos;

    /**
     * Instantiates a new Crawl result.
     *
     * @param status  the status
     * @param message the message
     * @param images  the images
     * @param logos   the logos
     */
    public CrawlResult(String status, String message, String images, String logos) {
        this.status = status;
        this.message = message;
        this.images = images;
        this.logos = logos;
    }

    /**
     * Gets status.
     *
     * @return the status
     */
    public String getStatus() { return status; }

    /**
     * Gets message.
     *
     * @return the message
     */
    public String getMessage() { return message; }

    /**
     * Gets images.
     *
     * @return the images
     */
    public String getImages() { return images; }

    /**
     * Gets logos.
     *
     * @return the logos
     */
    public String getLogos() { return logos; }
}
