package io.github.brantunger.gamex.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.GdxRuntimeException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

class WardenPreloaderTest {

    private static final class RecordingDisposable implements Disposable {
        final String label;
        final AtomicInteger disposeCount = new AtomicInteger();

        RecordingDisposable(String label) {
            this.label = label;
        }

        @Override
        public void dispose() {
            disposeCount.incrementAndGet();
        }
    }

    private static final class RecordingBundle implements Disposable {
        final List<RecordingDisposable> parts;
        final AtomicInteger disposeCount = new AtomicInteger();

        RecordingBundle(List<RecordingDisposable> parts) {
            this.parts = parts;
        }

        @Override
        public void dispose() {
            if (disposeCount.incrementAndGet() == 1) {
                for (RecordingDisposable part : parts) {
                    if (part != null) part.dispose();
                }
            }
        }
    }

    @Test
    void decodesAllThreeSlicesConcurrentlyAcrossWorkerThreads() {
        ExecutorService pool = Executors.newFixedThreadPool(3);
        Set<String> workerThreads = ConcurrentHashMap.newKeySet();
        CountDownLatch allSlicesStarted = new CountDownLatch(3);

        try {
            WardenPreloader.Task<RecordingBundle> task = WardenPreloader.startParallelDecode(
                () -> List.of("slice-0", "slice-1", "slice-2"),
                slice -> {
                    workerThreads.add(Thread.currentThread().getName());
                    allSlicesStarted.countDown();
                    try {
                        assertTrue(allSlicesStarted.await(5, TimeUnit.SECONDS),
                            "all 3 slice decodes must execute concurrently");
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError("Interrupted waiting for concurrent slice barrier", e);
                    }
                    return new RecordingDisposable("decoded-" + slice);
                },
                (slices, decoded) -> new RecordingBundle(decoded),
                pool);

            RecordingBundle bundle = task.await();
            assertEquals(3, bundle.parts.size());
            assertEquals("decoded-slice-0", bundle.parts.get(0).label);
            assertEquals("decoded-slice-1", bundle.parts.get(1).label);
            assertEquals("decoded-slice-2", bundle.parts.get(2).label);
            assertEquals(3, workerThreads.size(), "expected 3 distinct worker threads: " + workerThreads);
            assertEquals(0, bundle.disposeCount.get());
            assertNull(task.await(), "second await() must not return ownership twice");
            assertEquals(0, bundle.disposeCount.get(), "second await() must not dispose caller-owned result");
            task.dispose();
            assertEquals(0, bundle.disposeCount.get(), "claiming await() grants ownership to caller");
            bundle.dispose();
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void singleThreadExecutorCompletesWithoutDeadlock() {
        ExecutorService single = Executors.newSingleThreadExecutor();
        try {
            WardenPreloader.Task<RecordingBundle> task = WardenPreloader.startParallelDecode(
                () -> List.of("a", "b", "c"),
                slice -> new RecordingDisposable("px-" + slice),
                (slices, decoded) -> new RecordingBundle(decoded),
                single);

            RecordingBundle result = task.await();
            assertEquals(3, result.parts.size());
            result.dispose();
        } finally {
            single.shutdownNow();
        }
    }

    @Test
    void cancellationMidDecodeDisposesAlreadyDecodedAndInFlightPixmaps() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(3);
        RecordingDisposable fastSlice = new RecordingDisposable("fast");
        RecordingDisposable inFlightSlice = new RecordingDisposable("in-flight");
        CountDownLatch fastDecoded = new CountDownLatch(1);
        CountDownLatch inFlightStarted = new CountDownLatch(1);
        CountDownLatch allowInFlightReturn = new CountDownLatch(1);
        AtomicBoolean assemblerCalled = new AtomicBoolean();

        try {
            WardenPreloader.Task<RecordingBundle> task = WardenPreloader.startParallelDecode(
                () -> List.of(0, 1, 2),
                idx -> {
                    if (idx == 0) {
                        fastDecoded.countDown();
                        return fastSlice;
                    }
                    if (idx == 1) {
                        inFlightStarted.countDown();
                        try {
                            assertTrue(allowInFlightReturn.await(5, TimeUnit.SECONDS));
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        return inFlightSlice;
                    }
                    return new RecordingDisposable("slice-2");
                },
                (slices, decoded) -> {
                    assemblerCalled.set(true);
                    return new RecordingBundle(decoded);
                },
                pool);

            assertTrue(fastDecoded.await(5, TimeUnit.SECONDS));
            assertTrue(inFlightStarted.await(5, TimeUnit.SECONDS));

            // Allow the in-flight decode to finish shortly after dispose() flips the cancellation flag.
            Thread releaser = new Thread(() -> {
                try {
                    Thread.sleep(40L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                allowInFlightReturn.countDown();
            }, "test-cancel-releaser");
            releaser.start();

            task.dispose();
            releaser.join(TimeUnit.SECONDS.toMillis(5));

            assertFalse(assemblerCalled.get(), "assembler must not run after cancellation");
            assertEquals(1, fastSlice.disposeCount.get(), "already-decoded sibling pixmap must be disposed");
            assertEquals(1, inFlightSlice.disposeCount.get(), "in-flight pixmap finishing after cancel must dispose");
            assertNull(task.await(), "cancelled task must not return unclaimed resources");
        } finally {
            allowInFlightReturn.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void failureInOneSliceDisposesSiblingPixmapsAndPropagatesError() {
        ExecutorService pool = Executors.newFixedThreadPool(3);
        RecordingDisposable siblingA = new RecordingDisposable("sibling-a");
        RecordingDisposable siblingC = new RecordingDisposable("sibling-c");
        AtomicBoolean assemblerCalled = new AtomicBoolean();
        GdxRuntimeException boom = new GdxRuntimeException("Corrupt PNG slice");

        try {
            WardenPreloader.Task<RecordingBundle> task = WardenPreloader.startParallelDecode(
                () -> List.of(0, 1, 2),
                idx -> {
                    if (idx == 0) return siblingA;
                    if (idx == 1) throw boom;
                    return siblingC;
                },
                (slices, decoded) -> {
                    assemblerCalled.set(true);
                    return new RecordingBundle(decoded);
                },
                pool);

            GdxRuntimeException thrown = assertThrows(GdxRuntimeException.class, task::await);
            assertSame(boom, thrown);
            assertFalse(assemblerCalled.get());
            assertEquals(1, siblingA.disposeCount.get());
            assertEquals(1, siblingC.disposeCount.get());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void disposingAfterCompletionFreesUnclaimedResources() throws InterruptedException {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        RecordingDisposable part = new RecordingDisposable("slice-0");
        AtomicReference<RecordingBundle> created = new AtomicReference<>();
        CountDownLatch assembled = new CountDownLatch(1);

        try {
            WardenPreloader.Task<RecordingBundle> task = WardenPreloader.startParallelDecode(
                () -> List.of(0),
                idx -> part,
                (slices, decoded) -> {
                    RecordingBundle bundle = new RecordingBundle(decoded);
                    created.set(bundle);
                    assembled.countDown();
                    return bundle;
                },
                pool);

            assertTrue(assembled.await(5, TimeUnit.SECONDS), "assembler did not complete");
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (!task.isDone() && System.nanoTime() < deadline) {
                Thread.yield();
            }
            assertTrue(task.isDone());
            assertEquals(0, created.get().disposeCount.get());

            task.dispose();
            assertEquals(1, created.get().disposeCount.get(), "unclaimed bundle must be disposed on dispose()");
            assertEquals(1, part.disposeCount.get(), "contained pixmap must be disposed with unclaimed bundle");
            assertNull(task.await(), "await() after dispose() must return null");
            assertEquals(1, created.get().disposeCount.get(), "subsequent await() must not double-dispose");
        } finally {
            pool.shutdownNow();
        }
    }
}
