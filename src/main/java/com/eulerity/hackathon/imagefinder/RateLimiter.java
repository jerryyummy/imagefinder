package com.eulerity.hackathon.imagefinder;

import java.util.Deque;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The type Rate limiter.
 */
public class RateLimiter {
    private final int bucketCapacity;
    private final long leakRateMs;
    private long lastLeakTime;
    private AtomicInteger bucketSize = new AtomicInteger(0);

    /**
     * RateLimiter constructor
     *
     * @param bucketCapacity
     * @param leakRateMs
     */
    public RateLimiter( int bucketCapacity, long leakRateMs) {
        this.bucketCapacity = bucketCapacity;
        this.leakRateMs = leakRateMs;
        this.lastLeakTime = System.currentTimeMillis();
    }

    /**
     * Allow request token bucket boolean.
     *
     * @return the boolean
     */
    public synchronized boolean allowRequestTokenBucket() {
        long now = System.currentTimeMillis();

        int newTokens = (int) ((now - lastLeakTime) / leakRateMs);
        lastLeakTime = now;

        bucketSize.getAndUpdate(size -> Math.min(bucketCapacity, size + newTokens));

        if (bucketSize.get() > 0) {
            bucketSize.decrementAndGet();
            return true;
        }

        return false;
    }
}
