package com.eulerity.hackathon.imagefinder;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.concurrent.*;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * The type Image finder.
 */
@WebServlet(
        name = "ImageFinder",
        urlPatterns = {"/main"}
)
public class ImageFinder extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final ConcurrentHashMap<String, CompletableFuture<CrawlResult>> crawlResults = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Future<?>> runningTasks = new ConcurrentHashMap<>();

    /**
     * The constant testImages.
     */
    public static final String[] testImages = {
            "https://images.pexels.com/photos/545063/pexels-photo-545063.jpeg?auto=compress&format=tiny",
            "https://images.pexels.com/photos/464664/pexels-photo-464664.jpeg?auto=compress&format=tiny",
            "https://images.pexels.com/photos/406014/pexels-photo-406014.jpeg?auto=compress&format=tiny",
            "https://images.pexels.com/photos/1108099/pexels-photo-1108099.jpeg?auto=compress&format=tiny"
    };

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        String url = req.getParameter("url");

        if (url == null || url.isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().print(GSON.toJson(new CrawlResult("error", "Missing 'url' parameter.", null, null)));
            return;
        }

        System.out.println("[Request] Received crawl request for: " + url);

        CompletableFuture<CrawlResult> future = crawlResults.get(url);

        if (future != null && future.isCancelled()) {
            // Restart the crawl if the task was cancelled
            System.out.println("[Restart] Previous crawl for " + url + " was cancelled. Restarting...");
            crawlResults.remove(url);
            future = startCrawling(url);
            crawlResults.put(url, future);
        } else if (future == null) {
            future = startCrawling(url);
            crawlResults.put(url, future);
        }

        try {
            CrawlResult result = future.get(10, TimeUnit.SECONDS);
            resp.setStatus(HttpServletResponse.SC_OK);
            resp.getWriter().print(GSON.toJson(result));
        } catch (TimeoutException e) {
            System.out.println("[Timeout] Crawling for " + url + " exceeded 10 seconds, returning partial results.");
            CrawlResult partialResult = getCurrentCrawlResult(url);
            resp.setStatus(HttpServletResponse.SC_OK);
            resp.getWriter().print(GSON.toJson(partialResult));

            // cancel teak can remove cache
            future.cancel(true);
            crawlResults.remove(url);

            // cancel `Future<?>` task
            Future<?> task = runningTasks.remove(url);
            if (task != null) {
                task.cancel(true);
            }
        } catch (CancellationException e) {
            System.out.println("[Cancelled] Crawl task for " + url + " was cancelled.");
            resp.setStatus(HttpServletResponse.SC_GONE);
            resp.getWriter().print(GSON.toJson(new CrawlResult("error", "Crawling was cancelled.", null, null)));

            // remove tasks has been canceled
            crawlResults.remove(url);
        } catch (ExecutionException e) {
            System.out.println("[ExecutionException] Crawling failed for " + url + ": " + e.getMessage());
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().print(GSON.toJson(new CrawlResult("error", "Crawling failed.", null, null)));

            // remove failed tasks
            crawlResults.remove(url);
        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().print(GSON.toJson(new CrawlResult("error", "Unexpected error occurred.", null, null)));
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String url = request.getParameter("url");

        if (url == null || url.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().print(GSON.toJson(new CrawlResult("error", "Missing 'url' parameter.", null, null)));
            return;
        }

        CompletableFuture<CrawlResult> futureResult = crawlResults.get(url);
        if (futureResult == null) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.getWriter().print(GSON.toJson(new CrawlResult("not_started", "No crawling task found for this URL.", null, null)));
            return;
        }

        try {
            if (futureResult.isDone()) {
                CrawlResult result = futureResult.get();
                response.setStatus(HttpServletResponse.SC_OK);
                response.setContentType("application/json");
                response.getWriter().print(GSON.toJson(result));

                CompletableFuture.runAsync(() -> {
                    try {
                        Thread.sleep(60000);
                        crawlResults.remove(url);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });

            } else {
                response.setStatus(HttpServletResponse.SC_ACCEPTED);
                response.getWriter().print(GSON.toJson(new CrawlResult("in_progress", "Crawling is still in progress. Try again later.", null, null)));
            }
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().print(GSON.toJson(new CrawlResult("error", "Failed to retrieve crawl result.", null, null)));
        }
    }

    /**
     * start crawl and return `CompletableFuture`
     */
    private CompletableFuture<CrawlResult> startCrawling(String url) {
        CompletableFuture<CrawlResult> future = new CompletableFuture<>();

        Future<?> task = CrawlingThreadPool.getInstance().getExecutor().submit(() -> {
            try {
                ImageCrawler crawler = ImageCrawlerFactory.getInstance().getCrawler(url);
                crawler.startCrawling(url, 0);

                long startTime = System.currentTimeMillis();

                while (crawler.isCrawling()) {
                    if (System.currentTimeMillis() - startTime > 10000) {
                        System.out.println("[Timeout] Crawling exceeded 10 seconds, returning partial results.");
                        future.complete(getCurrentCrawlResult(url));
                        return;
                    }
                    Thread.sleep(1000);

                    // crontask to check if task is canceled
                    if (Thread.currentThread().isInterrupted()) {
                        System.out.println("[Cancelled] Crawling task interrupted for " + url);
                        return;
                    }
                }

                CrawlResult result = new CrawlResult("completed", "Crawling completed successfully.",
                        crawler.getImageUrlsAsJson(), crawler.getLogoUrlsAsJson());
                future.complete(result);
                System.out.println("[Crawler] " + url + " crawling completed.");

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();  // Reset interrupt status
                future.complete(new CrawlResult("error", "Crawling was interrupted.", null, null));
            } catch (MalformedURLException e) {
                future.complete(new CrawlResult("error", "Invalid URL format.", null, null));
            } finally {
                runningTasks.remove(url);  // Clean up after task completion
            }
        });

        runningTasks.put(url, task);
        return future;
    }

    /**
     * Get part of the data currently crawled (if timeout)
     */
    private CrawlResult getCurrentCrawlResult(String url) throws MalformedURLException {
        ImageCrawler crawler = ImageCrawlerFactory.getInstance().getCrawler(url);
        return new CrawlResult("partial",
                "Crawling exceeded time limit, returning available results.",
                crawler.getImageUrlsAsJson(),
                crawler.getLogoUrlsAsJson());
    }

    /**
     * Shut down the thread pool when destroying the Servlet
     */
    @Override
    public void destroy() {
        CrawlingThreadPool.getInstance().shutdown();
        System.out.println("[Shutdown] ImageFinder servlet shutting down.");
    }
}