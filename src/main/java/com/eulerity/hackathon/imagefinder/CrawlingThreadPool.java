package com.eulerity.hackathon.imagefinder;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * The type Crawling thread pool.
 */
public class CrawlingThreadPool {
    private static final int THREAD_COUNT = 10;
    private static final CrawlingThreadPool INSTANCE = new CrawlingThreadPool();

    private final ThreadPoolExecutor executorService;
    private RateLimiter rateLimiter;

    private CrawlingThreadPool() {
        this.executorService = new ThreadPoolExecutor(
                THREAD_COUNT, THREAD_COUNT,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(100),
                Executors.defaultThreadFactory(),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        this.rateLimiter = new RateLimiter(10, 200);
    }

    /**
     * Gets instance.
     *
     * @return the instance
     */
    public static CrawlingThreadPool getInstance() {
        return INSTANCE;
    }

    /**
     * submit
     *
     * @param task crawl tasks
     */
    public void submitTask(Runnable task) {
        if (rateLimiter.allowRequestTokenBucket()) {
            executorService.submit(task);
            System.out.println("[Crawling] Task submitted. Active Threads: " + executorService.getActiveCount());
        } else {
            System.out.println("[Rate Limited] Task delayed. Queue Size: " + executorService.getQueue().size());
            try {
                TimeUnit.MILLISECONDS.sleep(500); // wait and re-submit
                executorService.submit(task);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * 获取线程池（用于 `CompletableFuture`）
     *
     * @return the executor
     */
    public ExecutorService getExecutor() {
        return executorService;
    }

    /**
     * 允许动态调整限流策略
     *
     * @param rateLimiter 新的 RateLimiter
     */
    public void setRateLimiter(RateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    /**
     * 关闭线程池
     */
    public void shutdown() {
        System.out.println("[Shutdown] Closing thread pool...");
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                System.out.println("[Shutdown] Forcing shutdown.");
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 强制终止线程池中的所有任务
     */
    public void shutdownNow() {
        System.out.println("[ShutdownNow] Immediate shutdown requested.");
        executorService.shutdownNow();
    }
}
