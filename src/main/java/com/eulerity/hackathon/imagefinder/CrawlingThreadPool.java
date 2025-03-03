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
    private static final int THREAD_COUNT = 10; // 线程池大小
    private static final CrawlingThreadPool INSTANCE = new CrawlingThreadPool();

    private final ThreadPoolExecutor executorService;
    private RateLimiter rateLimiter; // 速率限制器

    private CrawlingThreadPool() {
        this.executorService = new ThreadPoolExecutor(
                THREAD_COUNT, THREAD_COUNT,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(100), // 任务队列最大 100
                Executors.defaultThreadFactory(),
                new ThreadPoolExecutor.CallerRunsPolicy() // 超过限制时，当前线程执行任务
        );
        this.rateLimiter = new RateLimiter(10, 200); // 默认限流器（10 请求，2 秒漏 1 个）
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
     * 提交爬取任务（受 RateLimiter 限制）
     *
     * @param task 爬取任务
     */
    public void submitTask(Runnable task) {
        if (rateLimiter.allowRequestTokenBucket()) {
            executorService.submit(task);
            System.out.println("[Crawling] Task submitted. Active Threads: " + executorService.getActiveCount());
        } else {
            System.out.println("[Rate Limited] Task delayed. Queue Size: " + executorService.getQueue().size());
            try {
                TimeUnit.MILLISECONDS.sleep(500); // 等待一段时间再尝试提交
                executorService.submit(task); // 重新提交任务
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
