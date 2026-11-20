package io.github.brantunger.gamex.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Every texture of the game. All but one are read from the {@code .png} files in {@code assets/textures/}, which
 * {@code ./gradlew tools:generateTextures} paints; {@link #white} is made in code. The textures are small and use
 * nearest-neighbour filtering, which gives the chunky look of the genre; {@link #orb} alone is filtered linearly.
 * The class keeps its name from the time it painted every texture itself at load time.
 */
public final class ProceduralTextures implements Disposable {

    /** The folder of the texture files inside {@code assets/}. */
    public static final String TEXTURES_DIR = "textures";
    /** The file of {@link #floor}. */
    public static final String FLOOR_FILE = "floor.png";
    /** The file of {@link #ceiling}. */
    public static final String CEILING_FILE = "ceiling.png";
    /** The file of {@link #muzzleFlash}. */
    public static final String MUZZLE_FLASH_FILE = "muzzle-flash.png";
    /** The file of {@link #scorch}. */
    public static final String SCORCH_FILE = "scorch.png";
    /** The file of {@link #spark}. */
    public static final String SPARK_FILE = "spark.png";
    /** The file of {@link #orb}. */
    public static final String ORB_FILE = "orb.png";
    /** The file of {@link #explosion}. */
    public static final String EXPLOSION_FILE = "explosion.png";
    /** The file of {@link #exitPad}. */
    public static final String EXIT_PAD_FILE = "exit-pad.png";

    /** The slate floor tiles. */
    public final Texture floor;
    /** The dark beamed ceiling. */
    public final Texture ceiling;
    /** The pistol's muzzle flash. */
    public final Texture muzzleFlash;
    /** The scorch mark a pistol shot leaves. */
    public final Texture scorch;
    /** The spark of a pistol shot landing. */
    public final Texture spark;
    /** The glowing ball of a blast in flight. */
    public final Texture orb;
    /** The fireball of an explosion. */
    public final Texture explosion;
    /** The glowing extraction pad on exit cells. */
    public final Texture exitPad;
    /** A single white pixel, for drawing flat rectangles. */
    public final Texture white;

    private final WallTextures wallTextures;
    /** The texture of each file {@link #wallTextures} names. */
    private final Map<String, Texture> wallsByFile;
    private final Array<Texture> all = new Array<>();

    /**
     * Reads every texture file and makes {@link #white}. Needs a GL context.
     * @param wallTextures which texture file covers each wall type
     * @throws RuntimeException if a texture file is missing or cannot be decoded
     */
    public ProceduralTextures(WallTextures wallTextures) {
        this(wallTextures, null);
    }

    /**
     * Uploads every texture file into GPU textures (reusing any pre-decoded CPU pixmaps in {@code predecoded}) and
     * makes {@link #white}. Needs a GL context.
     * @param wallTextures which texture file covers each wall type
     * @param predecoded optional pre-decoded pixmaps from {@link AssetPreparer}, consumed and disposed here
     * @throws RuntimeException if a texture file is missing or cannot be decoded
     */
    public ProceduralTextures(WallTextures wallTextures, AssetPreparer.PreparedTextures predecoded) {
        if (wallTextures == null) {
            if (predecoded != null) predecoded.dispose();
            throw new IllegalArgumentException("wallTextures must not be null");
        }
        this.wallTextures = wallTextures;
        try {
            wallsByFile = loadEach(wallTextures.files(), file -> load(file, predecoded));
            floor = load(FLOOR_FILE, predecoded);
            ceiling = load(CEILING_FILE, predecoded);
            muzzleFlash = load(MUZZLE_FLASH_FILE, predecoded);
            scorch = load(SCORCH_FILE, predecoded);
            spark = load(SPARK_FILE, predecoded);
            orb = upload(read(ORB_FILE, predecoded), Texture.TextureFilter.Linear);
            explosion = load(EXPLOSION_FILE, predecoded);
            exitPad = load(EXIT_PAD_FILE, predecoded);
            white = upload(paintWhite());
        } catch (Throwable t) {
            try {
                dispose();
            } catch (Throwable suppressed) {
                t.addSuppressed(suppressed);
            }
            throw t;
        } finally {
            if (predecoded != null) {
                predecoded.dispose();
            }
        }
    }

    /** @return ordered distinct texture file names under {@code assets/textures/} needed at startup. */
    static Set<String> allFiles(WallTextures wallTextures) {
        Set<String> files = new LinkedHashSet<>(wallTextures.files());
        files.add(FLOOR_FILE);
        files.add(CEILING_FILE);
        files.add(MUZZLE_FLASH_FILE);
        files.add(SCORCH_FILE);
        files.add(SPARK_FILE);
        files.add(ORB_FILE);
        files.add(EXPLOSION_FILE);
        files.add(EXIT_PAD_FILE);
        return files;
    }

    /**
     * @param wallType a wall type from the map layout
     * @return the texture of that wall type
     * @throws IllegalArgumentException if the wall table has no texture for the character; there is no fallback
     */
    public Texture wall(char wallType) {
        return wallsByFile.get(wallTextures.file(wallType));
    }

    /**
     * Loads each file once.
     * @param files file names in {@code assets/textures/}
     * @param loader makes the texture of one file
     * @return the texture of each file, in the order of {@code files}
     */
    static <T> Map<String, T> loadEach(Iterable<String> files, Function<String, T> loader) {
        Map<String, T> loaded = new LinkedHashMap<>();
        for (String file : files) {
            if (!loaded.containsKey(file)) {
                loaded.put(file, loader.apply(file));
            }
        }
        return loaded;
    }

    @Override
    public void dispose() {
        disposeAll(all);
    }

    static void disposeAll(Array<? extends Disposable> resources) {
        if (resources == null) return;
        for (int i = 0; i < resources.size; i++) {
            Disposable resource = resources.get(i);
            if (resource != null) {
                try {
                    resource.dispose();
                } catch (RuntimeException ignored) {
                    // Isolate cleanup so one failing texture disposal never skips the remaining textures.
                }
            }
        }
        resources.clear();
    }

    /** Reads a file of {@code assets/textures/} into a nearest-filtered texture. */
    private Texture load(String file, AssetPreparer.PreparedTextures predecoded) {
        return upload(read(file, predecoded));
    }

    private static Pixmap read(String file, AssetPreparer.PreparedTextures predecoded) {
        if (predecoded != null) {
            Pixmap pixmap = predecoded.take(file);
            if (pixmap != null) return pixmap;
        }
        return read(file);
    }

    /** Decodes a file of {@code assets/textures/}: the loose file under the working directory, else the classpath. */
    static Pixmap read(String file) {
        return new Pixmap(Gdx.files.internal(TEXTURES_DIR + "/" + file));
    }

    /** Turns a pixmap into a nearest-filtered texture and disposes of the pixmap. */
    private Texture upload(Pixmap pixmap) {
        return upload(pixmap, Texture.TextureFilter.Nearest);
    }

    private Texture upload(Pixmap pixmap, Texture.TextureFilter filter) {
        return uploadPixmap(pixmap, all, Texture::new, texture -> texture.setFilter(filter, filter));
    }

    static <P extends Disposable, T extends Disposable> T uploadPixmap(
        P pixmap,
        Array<T> tracked,
        Function<P, T> textureFactory,
        Consumer<T> configure) {
        if (pixmap == null) {
            throw new IllegalArgumentException("pixmap must not be null");
        }
        Throwable primary = null;
        try {
            T texture = textureFactory.apply(pixmap);
            if (texture != null) {
                if (configure != null) {
                    try {
                        configure.accept(texture);
                    } catch (Throwable t) {
                        disposeSuppressing(texture, t);
                        throw t;
                    }
                }
                if (tracked != null) {
                    tracked.add(texture);
                }
            }
            return texture;
        } catch (Throwable t) {
            primary = t;
            throw t;
        } finally {
            if (primary != null) {
                disposeSuppressing(pixmap, primary);
            } else {
                pixmap.dispose();
            }
        }
    }

    static <P extends Disposable, T extends Disposable> Array<T> uploadAll(
        Iterable<? extends Supplier<P>> painters,
        Function<P, T> textureFactory,
        BiConsumer<Integer, T> configure) {
        Array<T> tracked = new Array<>();
        try {
            int index = 0;
            for (Supplier<P> painter : painters) {
                final int current = index++;
                uploadPixmap(painter.get(), tracked, textureFactory,
                    configure != null ? texture -> configure.accept(current, texture) : null);
            }
            return tracked;
        } catch (Throwable t) {
            try {
                disposeAll(tracked);
            } catch (Throwable suppressed) {
                t.addSuppressed(suppressed);
            }
            throw t;
        }
    }

    private static void disposeSuppressing(Disposable resource, Throwable primary) {
        if (resource == null) return;
        try {
            resource.dispose();
        } catch (Throwable suppressed) {
            primary.addSuppressed(suppressed);
        }
    }

    private static Pixmap paintWhite() {
        Pixmap pixel = blank(1, 1);
        try {
            pixel.drawPixel(0, 0, 0xFFFFFFFF);
            return pixel;
        } catch (Throwable t) {
            pixel.dispose();
            throw t;
        }
    }

    private static Pixmap blank(int width, int height) {
        Pixmap pixmap = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        pixmap.setBlending(Pixmap.Blending.None);
        return pixmap;
    }
}
