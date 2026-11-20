package io.github.brantunger.gamex.render;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.TimeUtils;
import io.github.brantunger.gamex.world.WorldModels;
import net.mgsx.gltf.data.data.GLTFBufferView;
import net.mgsx.gltf.data.texture.GLTFImage;
import net.mgsx.gltf.loaders.glb.BinaryDataFileResolver;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Decodes the Warden's 48 MB binary glTF container and its embedded 4096x4096 PNG textures in parallel on background
 * worker threads without blocking OpenGL rendering.
 */
final class WardenPreloader implements Disposable {

    /** Testable handle to an asynchronous parallel slice-decode pipeline with cancellation and disposal guarantees. */
    static final class Task<R extends Disposable> implements Disposable {
        private final CompletableFuture<R> future;
        private final AtomicBoolean cancelled;
        private final Object claimLock = new Object();
        private boolean claimed;

        Task(CompletableFuture<R> future, AtomicBoolean cancelled) {
            this.future = future;
            this.cancelled = cancelled;
        }

        boolean isDone() {
            return future.isDone();
        }

        R await() {
            try {
                R result = future.join();
                synchronized (claimLock) {
                    if (claimed) {
                        return null;
                    }
                    claimed = true;
                    if (cancelled.get()) {
                        disposeQuietly(result);
                        return null;
                    }
                    return result;
                }
            } catch (CompletionException e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                if (cause instanceof RuntimeException) throw (RuntimeException) cause;
                if (cause instanceof Error) throw (Error) cause;
                throw new IllegalStateException("Background decode failed", cause);
            }
        }

        @Override
        public void dispose() {
            cancelled.set(true);
            future.whenComplete((result, err) -> {
                synchronized (claimLock) {
                    if (!claimed) {
                        claimed = true;
                        disposeQuietly(result);
                    }
                }
            });
            try {
                future.get(2L, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException | TimeoutException ignored) {
                // Any completed resources are freed via the whenComplete handler above.
            }
        }
    }

    private static final class ImageSlice {
        final BinaryDataFileResolver backing;
        final GLTFImage image;
        final byte[] encodedBytes;
        final long startedMillis;

        ImageSlice(BinaryDataFileResolver backing, GLTFImage image, byte[] encodedBytes, long startedMillis) {
            this.backing = backing;
            this.image = image;
            this.encodedBytes = encodedBytes;
            this.startedMillis = startedMillis;
        }
    }

    private final Task<WardenModel.DecodedAsset> task;

    private WardenPreloader(Task<WardenModel.DecodedAsset> task) {
        this.task = task;
    }

    /** Starts reading {@code file} and decoding its embedded textures in parallel on {@code executor}. */
    static WardenPreloader start(FileHandle file, Executor executor) {
        Executor target = executor != null ? executor : Runnable::run;
        Task<WardenModel.DecodedAsset> task = startParallelDecode(
            () -> readSlices(file),
            WardenPreloader::decodeSlice,
            (slices, pixmaps) -> assembleDecodedAsset(slices, pixmaps),
            target);
        return new WardenPreloader(task);
    }

    /** Synchronous convenience path preserving full 3-stage decode when called directly. */
    static WardenModel.DecodedAsset decodeSynchronously(FileHandle file) {
        return start(file, Runnable::run).await();
    }

    /**
     * Composes a two-stage deadlock-free async decode pipeline: {@code readSlices} produces work items once, each
     * slice is decoded concurrently via {@code decodeSlice}, and {@code assembler} combines the results. Any failure or
     * cancellation disposes every already-decoded and in-flight {@link Disposable}.
     */
    static <S, P extends Disposable, R extends Disposable> Task<R> startParallelDecode(
        Supplier<List<S>> readSlices,
        Function<S, P> decodeSlice,
        BiFunction<List<S>, List<P>, R> assembler,
        Executor executor) {
        AtomicBoolean cancelled = new AtomicBoolean();
        CompletableFuture<R> future = CompletableFuture.supplyAsync(() -> {
            if (cancelled.get()) return null;
            return readSlices.get();
        }, executor).thenCompose(slices -> {
            if (slices == null || cancelled.get()) {
                return CompletableFuture.completedFuture(null);
            }
            int count = slices.size();
            if (count == 0) {
                return CompletableFuture.completedFuture(assembler.apply(slices, Collections.emptyList()));
            }
            @SuppressWarnings("unchecked")
            CompletableFuture<P>[] sliceFutures = new CompletableFuture[count];
            for (int i = 0; i < count; i++) {
                final S slice = slices.get(i);
                sliceFutures[i] = CompletableFuture.supplyAsync(() -> {
                    if (cancelled.get()) return null;
                    P decoded = decodeSlice.apply(slice);
                    if (decoded != null && cancelled.get()) {
                        disposeQuietly(decoded);
                        return null;
                    }
                    return decoded;
                }, executor);
            }
            return CompletableFuture.allOf(sliceFutures).handle((ignored, err) -> {
                List<P> results = new ArrayList<>(count);
                Throwable failure = err;
                for (int i = 0; i < count; i++) {
                    CompletableFuture<P> f = sliceFutures[i];
                    if (f.isCompletedExceptionally()) {
                        try {
                            f.join();
                        } catch (CompletionException ce) {
                            if (failure == null) failure = ce.getCause() != null ? ce.getCause() : ce;
                        }
                        results.add(null);
                    } else {
                        results.add(f.getNow(null));
                    }
                }
                if (cancelled.get() || failure != null) {
                    for (int i = 0; i < results.size(); i++) {
                        disposeQuietly(results.get(i));
                    }
                    if (cancelled.get()) {
                        return null;
                    }
                    if (failure instanceof RuntimeException) throw (RuntimeException) failure;
                    if (failure instanceof Error) throw (Error) failure;
                    throw new CompletionException(failure);
                }
                try {
                    return assembler.apply(slices, results);
                } catch (Throwable t) {
                    for (int i = 0; i < results.size(); i++) {
                        disposeQuietly(results.get(i));
                    }
                    throw t;
                }
            });
        });
        return new Task<>(future, cancelled);
    }

    boolean isDone() {
        return task.isDone();
    }

    WardenModel.DecodedAsset await() {
        try {
            return task.await();
        } catch (RuntimeException e) {
            if (e instanceof GdxRuntimeException && e.getMessage() != null
                && e.getMessage().startsWith("The warden model ")) {
                throw e;
            }
            throw new GdxRuntimeException("The warden model " + WorldModels.WARDEN_PATH + " could not be read: "
                + e.getMessage(), e);
        }
    }

    @Override
    public void dispose() {
        task.dispose();
    }

    private static List<ImageSlice> readSlices(FileHandle file) {
        long started = TimeUtils.millis();
        BinaryDataFileResolver backing = new BinaryDataFileResolver();
        backing.load(file);
        Array<GLTFImage> images = backing.getRoot() != null ? backing.getRoot().images : null;
        if (images == null || images.size == 0) {
            return List.of(new ImageSlice(backing, null, null, started));
        }
        Array<GLTFBufferView> bufferViews = backing.getRoot().bufferViews;
        List<ImageSlice> slices = new ArrayList<>(images.size);
        for (int i = 0; i < images.size; i++) {
            GLTFImage img = images.get(i);
            byte[] bytes = null;
            if (img.bufferView != null && bufferViews != null) {
                GLTFBufferView view = bufferViews.get(img.bufferView);
                ByteBuffer source = backing.getBuffer(view.buffer).duplicate();
                source.position(view.byteOffset);
                bytes = new byte[view.byteLength];
                source.get(bytes);
            }
            slices.add(new ImageSlice(backing, img, bytes, started));
        }
        return slices;
    }

    private static Pixmap decodeSlice(ImageSlice slice) {
        if (slice.image == null) return null;
        if (slice.encodedBytes != null) {
            return new Pixmap(slice.encodedBytes, 0, slice.encodedBytes.length);
        }
        return slice.backing.load(slice.image);
    }

    private static WardenModel.DecodedAsset assembleDecodedAsset(List<ImageSlice> slices, List<Pixmap> pixmaps) {
        BinaryDataFileResolver backing = slices.get(0).backing;
        long startedMillis = slices.get(0).startedMillis;
        Map<GLTFImage, Pixmap> map = new IdentityHashMap<>(slices.size());
        for (int i = 0; i < slices.size(); i++) {
            ImageSlice slice = slices.get(i);
            Pixmap pixmap = pixmaps.get(i);
            if (slice.image != null && pixmap != null) {
                map.put(slice.image, pixmap);
            }
        }
        long elapsed = TimeUtils.millis() - startedMillis;
        return new WardenModel.DecodedAsset(backing, map, elapsed);
    }

    private static void disposeQuietly(Disposable resource) {
        if (resource != null) {
            try {
                resource.dispose();
            } catch (RuntimeException ignored) {
                // Keep cleanup isolated so sibling native pixmaps are never leaked.
            }
        }
    }
}
