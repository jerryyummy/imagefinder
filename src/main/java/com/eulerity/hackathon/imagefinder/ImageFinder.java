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

    // 用于存储爬取结果
    private static final ConcurrentHashMap<String, CompletableFuture<CrawlResult>> crawlResults = new ConcurrentHashMap<>();

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
    protected final void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        String url = req.getParameter("url");

        if (url == null || url.isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().print(GSON.toJson(new CrawlResult("error", "Missing 'url' parameter.", null, null)));
            return;
        }

        System.out.println("[Request] Received crawl request for: " + url);

        CompletableFuture<CrawlResult> future = crawlResults.computeIfAbsent(url, key -> startCrawling(url));

        try {
            // **等待最多 10 秒**，如果超时，则返回当前已爬取的数据
            CrawlResult result = future.get(10, TimeUnit.SECONDS);
            resp.setStatus(HttpServletResponse.SC_OK);
            resp.getWriter().print(GSON.toJson(result));
        } catch (TimeoutException e) {
            // **超时了，但仍然返回当前爬取到的结果**
            System.out.println("[Timeout] Crawling for " + url + " exceeded 10 seconds, returning partial results.");

            // **获取当前已爬取的部分数据**
            CrawlResult partialResult = getCurrentCrawlResult(url);

            // **返回部分爬取数据**
            resp.setStatus(HttpServletResponse.SC_OK);
            resp.getWriter().print(GSON.toJson(partialResult));

            // **取消未完成的任务，避免资源浪费**
            future.cancel(true);
        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().print(GSON.toJson(new CrawlResult("error", "Crawling failed.", null, null)));
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
            if (futureResult.isDone()) {  // 任务完成，返回 200 OK
                CrawlResult result = futureResult.get();
                response.setStatus(HttpServletResponse.SC_OK);
                response.setContentType("application/json");
                response.getWriter().print(GSON.toJson(result));

                // ✅ 任务完成后，异步清理
                CompletableFuture.runAsync(() -> {
                    try {
                        Thread.sleep(60000);
                        crawlResults.remove(url);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });

            } else {  // 任务仍在进行中，返回 202
                response.setStatus(HttpServletResponse.SC_ACCEPTED);
                response.getWriter().print(GSON.toJson(new CrawlResult("in_progress", "Crawling is still in progress. Try again later.", null, null)));
            }
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().print(GSON.toJson(new CrawlResult("error", "Failed to retrieve crawl result.", null, null)));
        }
    }

    /**
     * 启动爬取任务并返回 `CompletableFuture`
     */
    private CompletableFuture<CrawlResult> startCrawling(String url) {
        CompletableFuture<CrawlResult> future = new CompletableFuture<>();

        CompletableFuture.runAsync(() -> {
            try {
                ImageCrawler crawler = ImageCrawlerFactory.getInstance().getCrawler(url);
                crawler.startCrawling(url, 0);

                long startTime = System.currentTimeMillis();

                while (crawler.isCrawling()) {
                    if (System.currentTimeMillis() - startTime > 10000) { // **超过 10 秒**
                        System.out.println("[Timeout] Crawling exceeded 10 seconds, returning partial results.");
                        future.complete(getCurrentCrawlResult(url));
                        return;
                    }
                    Thread.sleep(1000);
                }

                // ✅ 任务完成，确保 future.complete()
                CrawlResult result = new CrawlResult("completed", "Crawling completed successfully.",
                        crawler.getImageUrlsAsJson(), crawler.getLogoUrlsAsJson());

                future.complete(result);  // 重要：标记任务完成
                System.out.println("[Crawler] " + url + " crawling completed.");

                // ✅ 任务完成后，延迟 60 秒清理
                CompletableFuture.runAsync(() -> {
                    try {
                        Thread.sleep(60000);
                        crawlResults.remove(url);
                        System.out.println("[Cleanup] Removed crawl result for: " + url);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                future.complete(new CrawlResult("error", "Crawling was interrupted.", null, null));
            } catch (MalformedURLException e) {
                future.complete(new CrawlResult("error", "Invalid URL format.", null, null));
            }
        }, CrawlingThreadPool.getInstance().getExecutor());

        return future;
    }

    /**
     * 获取当前爬取到的部分数据（如果超时）
     */
    private CrawlResult getCurrentCrawlResult(String url) throws MalformedURLException {
        ImageCrawler crawler = ImageCrawlerFactory.getInstance().getCrawler(url);
        return new CrawlResult("partial",
                "Crawling exceeded time limit, returning available results.",
                crawler.getImageUrlsAsJson(),  // **返回已爬取到的图片**
                crawler.getLogoUrlsAsJson());  // **返回已爬取到的 logo**
    }

    /**
     * 销毁 Servlet 时关闭线程池
     */
    @Override
    public void destroy() {
        CrawlingThreadPool.getInstance().shutdown();
        System.out.println("[Shutdown] ImageFinder servlet shutting down.");
    }
}