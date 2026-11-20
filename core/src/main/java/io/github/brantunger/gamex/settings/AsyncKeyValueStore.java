package io.github.brantunger.gamex.settings;

import com.badlogic.gdx.utils.Disposable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe {@link KeyValueStore} wrapper that keeps an in-memory write-through cache for instant main-thread reads
 * and offloads {@link #flush()} disk I/O to a sequential background worker thread.
 */
public final class AsyncKeyValueStore implements KeyValueStore, Disposable {

    private static final Logger log = LoggerFactory.getLogger(AsyncKeyValueStore.class);
    private static final long DRAIN_TIMEOUT_SECONDS = 5L;

    @FunctionalInterface
    private interface PendingWrite {
        void apply(KeyValueStore delegate, String key);
    }

    private final KeyValueStore delegate;
    private final ExecutorService ioExecutor;
    private final boolean ownsExecutor;
    private final Object stateLock = new Object();
    private final Object delegateLock = new Object();
    private final Map<String, Object> readCache = new HashMap<>();
    private final AtomicReference<Future<?>> lastFlush = new AtomicReference<>();
    private Map<String, PendingWrite> pendingWrites = new LinkedHashMap<>();
    private boolean closed;

    /**
     * Creates an asynchronous store backed by {@code delegate} using a dedicated daemon I/O thread.
     * @param delegate underlying persistence store
     */
    public AsyncKeyValueStore(KeyValueStore delegate) {
        this(delegate, newIoExecutor(), true);
    }

    /**
     * Creates an asynchronous store backed by {@code delegate} on a caller-provided sequential executor.
     * @param delegate underlying persistence store
     * @param ioExecutor single-threaded (or sequential) executor executing disk flushes
     * @param ownsExecutor whether {@link #dispose()} should shut down {@code ioExecutor}
     */
    public AsyncKeyValueStore(KeyValueStore delegate, ExecutorService ioExecutor, boolean ownsExecutor) {
        if (delegate == null) throw new IllegalArgumentException("Delegate KeyValueStore must not be null");
        if (ioExecutor == null) throw new IllegalArgumentException("IO executor must not be null");
        this.delegate = delegate;
        this.ioExecutor = ioExecutor;
        this.ownsExecutor = ownsExecutor;
    }

    /** @return a single-threaded daemon executor named {@code gamex-io} for sequential disk persistence. */
    public static ExecutorService newIoExecutor() {
        return Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "gamex-io");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override
    public float getFloat(String key, float defaultValue) {
        synchronized (stateLock) {
            Object cached = readCache.get(key);
            if (cached instanceof Float) return (Float) cached;
        }
        synchronized (delegateLock) {
            return delegate.getFloat(key, defaultValue);
        }
    }

    @Override
    public int getInteger(String key, int defaultValue) {
        synchronized (stateLock) {
            Object cached = readCache.get(key);
            if (cached instanceof Integer) return (Integer) cached;
        }
        synchronized (delegateLock) {
            return delegate.getInteger(key, defaultValue);
        }
    }

    @Override
    public boolean getBoolean(String key, boolean defaultValue) {
        synchronized (stateLock) {
            Object cached = readCache.get(key);
            if (cached instanceof Boolean) return (Boolean) cached;
        }
        synchronized (delegateLock) {
            return delegate.getBoolean(key, defaultValue);
        }
    }

    @Override
    public String getString(String key, String defaultValue) {
        synchronized (stateLock) {
            if (readCache.containsKey(key)) {
                Object cached = readCache.get(key);
                if (cached == null || cached instanceof String) {
                    return (String) cached;
                }
            }
        }
        synchronized (delegateLock) {
            return delegate.getString(key, defaultValue);
        }
    }

    @Override
    public void putFloat(String key, float value) {
        synchronized (stateLock) {
            readCache.put(key, value);
            pendingWrites.put(key, (store, k) -> store.putFloat(k, value));
        }
    }

    @Override
    public void putInteger(String key, int value) {
        synchronized (stateLock) {
            readCache.put(key, value);
            pendingWrites.put(key, (store, k) -> store.putInteger(k, value));
        }
    }

    @Override
    public void putBoolean(String key, boolean value) {
        synchronized (stateLock) {
            readCache.put(key, value);
            pendingWrites.put(key, (store, k) -> store.putBoolean(k, value));
        }
    }

    @Override
    public void putString(String key, String value) {
        synchronized (stateLock) {
            readCache.put(key, value);
            pendingWrites.put(key, (store, k) -> store.putString(k, value));
        }
    }

    @Override
    public void flush() {
        Map<String, PendingWrite> batch;
        boolean runSynchronously;
        synchronized (stateLock) {
            batch = drainPendingLocked();
            runSynchronously = closed;
        }
        Runnable task = () -> flushBatch(batch);
        if (runSynchronously) {
            task.run();
            return;
        }
        try {
            lastFlush.set(ioExecutor.submit(task));
        } catch (RejectedExecutionException e) {
            task.run();
        }
    }

    /** Flushes any staged writes and waits until all pending background flushes complete. */
    public void awaitPending() {
        boolean hasUnflushed;
        synchronized (stateLock) {
            hasUnflushed = !pendingWrites.isEmpty();
        }
        if (hasUnflushed) {
            flush();
        }
        waitForFuture(lastFlush.get());
    }

    @Override
    public void dispose() {
        Map<String, PendingWrite> remaining;
        synchronized (stateLock) {
            closed = true;
            remaining = drainPendingLocked();
        }
        waitForFuture(lastFlush.get());
        if (!remaining.isEmpty()) {
            flushBatch(remaining);
        }
        if (ownsExecutor) {
            ioExecutor.shutdown();
            try {
                if (!ioExecutor.awaitTermination(DRAIN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    log.warn("Timed out waiting for asynchronous preferences writer shutdown");
                    ioExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private Map<String, PendingWrite> drainPendingLocked() {
        if (pendingWrites.isEmpty()) return Collections.emptyMap();
        Map<String, PendingWrite> drained = pendingWrites;
        pendingWrites = new LinkedHashMap<>();
        return drained;
    }

    private void flushBatch(Map<String, PendingWrite> batch) {
        try {
            synchronized (delegateLock) {
                for (Map.Entry<String, PendingWrite> entry : batch.entrySet()) {
                    entry.getValue().apply(delegate, entry.getKey());
                }
                delegate.flush();
            }
        } catch (RuntimeException e) {
            log.error("Failed to flush preferences asynchronously: {}", e.getMessage(), e);
        }
    }

    private static void waitForFuture(Future<?> future) {
        if (future == null) return;
        try {
            future.get(DRAIN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            log.error("Asynchronous preferences flush failed: {}", e.getMessage(), e.getCause());
        } catch (TimeoutException e) {
            log.warn("Timed out waiting for asynchronous preferences flush");
        }
    }
}
