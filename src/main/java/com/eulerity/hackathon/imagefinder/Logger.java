package com.eulerity.hackathon.imagefinder;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.*;

/**
 * The type Logger.
 */
public class Logger {
    private static final String LOG_FILE = "crawler.log"; // log file path
    private static final Logger instance = new Logger();
    private final java.util.logging.Logger logger;

    private Logger() {
        logger = java.util.logging.Logger.getLogger("ImageCrawlerLogger");
        logger.setUseParentHandlers(false); // close default control log

        try {
            FileHandler fileHandler = new FileHandler(LOG_FILE, true);
            fileHandler.setFormatter(new SimpleFormatter());

            ConsoleHandler consoleHandler = new ConsoleHandler();
            consoleHandler.setFormatter(new SimpleFormatter());

            logger.setLevel(Level.ALL);
            fileHandler.setLevel(Level.ALL);
            consoleHandler.setLevel(Level.ALL);

            // add handler
            logger.addHandler(fileHandler);
            logger.addHandler(consoleHandler);
        } catch (IOException e) {
            System.err.println("Failed to initialize logger: " + e.getMessage());
        }
    }

    /**
     * Gets instance.
     *
     * @return the instance
     */
    public static Logger getInstance() {
        return instance;
    }

    /**
     * Log.
     *
     * @param level   the level
     * @param message the message
     */
    public void log(Level level, String message) {
        logger.log(level, "[" + getCurrentTimestamp() + "] " + message);
    }

    /**
     * Info.
     *
     * @param message the message
     */
    public void info(String message) {
        log(Level.INFO, message);
    }

    /**
     * Warn.
     *
     * @param message the message
     */
    public void warn(String message) {
        log(Level.WARNING, message);
    }

    /**
     * Error.
     *
     * @param message the message
     */
    public void error(String message) {
        log(Level.SEVERE, message);
    }

    private String getCurrentTimestamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
    }
}
