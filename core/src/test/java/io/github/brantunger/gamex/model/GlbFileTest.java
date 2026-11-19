package io.github.brantunger.gamex.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/** Checks how the start of a binary glTF file is read, and what is said of a file that is not one. */
class GlbFileTest {

    /** What Git LFS leaves in place of a file it has not fetched. */
    private static final String POINTER = "version https://git-lfs.github.com/spec/v1\n"
        + "oid sha256:97a70a60e799e3caa84612b602587695a1e687d6ce68be114b716646191967f4\n"
        + "size 50079128\n";

    private static byte[] glb(int magic, int version, int chunkType, String json) {
        byte[] text = json.getBytes(StandardCharsets.UTF_8);
        ByteBuffer file = ByteBuffer.allocate(GlbFile.HEADER_SIZE + text.length + 8).order(ByteOrder.LITTLE_ENDIAN);
        file.putInt(magic).putInt(version).putInt(file.capacity());
        file.putInt(text.length).putInt(chunkType).put(text);
        // The start of the binary chunk that follows the JSON in a real file.
        file.putInt(0).putInt(0x004E4942);
        return file.array();
    }

    private static byte[] glb(String json) {
        return glb(0x46546C67, 2, 0x4E4F534A, json);
    }

    private static String readJson(byte[] file) throws IOException {
        return GlbFile.readJson(new ByteArrayInputStream(file), "models/thing.glb");
    }

    @Test
    void theJsonOfABinaryGltfFileIsReadAndNothingAfterIt() throws IOException {
        byte[] file = glb("{\"asset\":{\"version\":\"2.0\"}}");
        assertEquals("{\"asset\":{\"version\":\"2.0\"}}", readJson(file));

        ByteArrayInputStream in = new ByteArrayInputStream(file);
        GlbFile.readJson(in, "models/thing.glb");
        assertEquals(8, in.available(), "the binary chunk is left unread");
    }

    @Test
    void aGitLfsPointerIsNamedAsOneWithWhatToDoAboutIt() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> readJson(POINTER.getBytes(StandardCharsets.US_ASCII)));
        assertEquals("models/thing.glb is a Git LFS pointer file, not the model itself. Install Git LFS, then run"
            + " 'git lfs install' and 'git lfs pull' in the repository; in CI, check out with 'lfs: true'.",
            error.getMessage());
    }

    @Test
    void aPointerIsKnownByTheBytesOfItThatFitInAHeader() {
        byte[] start = POINTER.substring(0, GlbFile.HEADER_SIZE).getBytes(StandardCharsets.US_ASCII);
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> readJson(start));
        assertTrue(error.getMessage().contains("Git LFS pointer"), error.getMessage());
    }

    @Test
    void anEmptyOrCutOffFileIsNotAModel() {
        IllegalArgumentException empty = assertThrows(IllegalArgumentException.class, () -> readJson(new byte[0]));
        assertEquals("models/thing.glb is not a binary glTF 2.0 file: it is only 0 bytes long", empty.getMessage());

        byte[] cut = new byte[12];
        System.arraycopy(glb("{}"), 0, cut, 0, 12);
        IllegalArgumentException shortFile = assertThrows(IllegalArgumentException.class, () -> readJson(cut));
        assertTrue(shortFile.getMessage().contains("only 12 bytes long"), shortFile.getMessage());
    }

    @Test
    void aFileOfAnotherKindOrAnotherVersionIsNotAModel() {
        byte[] text = "# Wavefront OBJ file\nv 0 0 0\nv 1 0 0\nv 0 1 0\nf 1 2 3\n".getBytes(StandardCharsets.US_ASCII);
        assertEquals("models/thing.glb is not a binary glTF 2.0 file",
            assertThrows(IllegalArgumentException.class, () -> readJson(text)).getMessage());
        assertEquals("models/thing.glb is not a binary glTF 2.0 file",
            assertThrows(IllegalArgumentException.class, () -> readJson(glb(0x46546C67, 1, 0x4E4F534A, "{}")))
                .getMessage());
    }

    @Test
    void aFileThatDoesNotStartWithItsJsonIsRefused() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> readJson(glb(0x46546C67, 2, 0x004E4942, "{}")));
        assertTrue(error.getMessage().contains("does not start with its JSON chunk"), error.getMessage());
    }

    @Test
    void aFileThatEndsInsideItsJsonIsAnError() throws IOException {
        byte[] whole = glb("{\"asset\":{\"version\":\"2.0\"}}");
        ByteArrayOutputStream cut = new ByteArrayOutputStream();
        cut.write(whole, 0, GlbFile.HEADER_SIZE + 5);
        assertThrows(EOFException.class, () -> readJson(cut.toByteArray()));
    }

    @Test
    void theHeaderIsReadEvenWhenItArrivesInPieces() throws IOException {
        byte[] file = glb("{}");
        // A stream that hands over one byte at a time, as one from a compressed JAR may.
        ByteArrayInputStream trickle = new ByteArrayInputStream(file) {
            @Override
            public synchronized int read(byte[] target, int offset, int length) {
                return super.read(target, offset, Math.min(1, length));
            }
        };
        assertEquals(2, GlbFile.checkHeader(GlbFile.readHeader(trickle), "models/thing.glb"));
    }
}
