package io.github.brantunger.gamex.model;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Reads the start of a binary glTF ({@code .glb}) file without graphics types: enough to tell a real model from a
 * Git LFS pointer file left in its place, and to read the JSON that describes the model.
 */
public final class GlbFile {

    /** How many bytes {@link #checkHeader} needs: the file header and the header of the first chunk. */
    public static final int HEADER_SIZE = 20;

    private static final int MAGIC = 0x46546C67;
    private static final int JSON_CHUNK = 0x4E4F534A;
    /** How a Git LFS pointer file starts, as far as the {@link #HEADER_SIZE} bytes that are looked at go. */
    private static final String LFS_POINTER_START = "version https://git-";

    private GlbFile() {}

    /**
     * Checks that a file starts as a binary glTF 2.0 file does.
     * @param header the first {@link #HEADER_SIZE} bytes of the file, or all of it if it is shorter
     * @param name what to call the file in an error, such as its path
     * @return the length of the JSON chunk that follows the header, in bytes
     * @throws IllegalArgumentException if the file is a Git LFS pointer, which the message says, or is otherwise
     *     not a binary glTF 2.0 file that starts with its JSON chunk
     */
    public static int checkHeader(byte[] header, String name) {
        if (new String(header, StandardCharsets.US_ASCII).startsWith(LFS_POINTER_START)) {
            throw new IllegalArgumentException(name + " is a Git LFS pointer file, not the model itself. Install Git"
                + " LFS, then run 'git lfs install' and 'git lfs pull' in the repository; in CI, check out with"
                + " 'lfs: true'.");
        }
        if (header.length < HEADER_SIZE) {
            throw new IllegalArgumentException(name + " is not a binary glTF 2.0 file: it is only " + header.length
                + " bytes long");
        }
        ByteBuffer words = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
        if (words.getInt(0) != MAGIC || words.getInt(4) != 2) {
            throw new IllegalArgumentException(name + " is not a binary glTF 2.0 file");
        }
        if (words.getInt(16) != JSON_CHUNK || words.getInt(12) < 0) {
            throw new IllegalArgumentException(name + " is a binary glTF file that does not start with its JSON chunk");
        }
        return words.getInt(12);
    }

    /**
     * Reads up to {@link #HEADER_SIZE} bytes from the start of a file.
     * @param in the file, at its start; it is not closed
     * @return the bytes read: fewer than {@link #HEADER_SIZE} only if the file is shorter
     * @throws IOException if the file cannot be read
     */
    public static byte[] readHeader(InputStream in) throws IOException {
        byte[] header = new byte[HEADER_SIZE];
        int length = 0;
        while (length < HEADER_SIZE) {
            int read = in.read(header, length, HEADER_SIZE - length);
            if (read < 0) break;
            length += read;
        }
        if (length == HEADER_SIZE) return header;
        byte[] shorter = new byte[length];
        System.arraycopy(header, 0, shorter, 0, length);
        return shorter;
    }

    /**
     * Reads the JSON of a binary glTF file, and none of the binary data that follows it.
     * @param in the file, at its start; it is not closed
     * @param name what to call the file in an error, such as its path
     * @return the JSON text
     * @throws IllegalArgumentException as {@link #checkHeader} does
     * @throws IOException if the file cannot be read or ends inside its JSON
     */
    public static String readJson(InputStream in, String name) throws IOException {
        byte[] json = new byte[checkHeader(readHeader(in), name)];
        new DataInputStream(in).readFully(json);
        return new String(json, StandardCharsets.UTF_8);
    }
}
