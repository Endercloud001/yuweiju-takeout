package com.codeying.risk;

import java.io.*;
import java.nio.file.*;

/** Model file lifecycle only; training, activation and compatibility remain Service rules. */
public final class RiskModelFiles {
    private RiskModelFiles() {}
    public static Path resolve(String directory, String artifact) {
        Path path = Path.of(artifact).normalize();
        return path.isAbsolute() ? path : Path.of(directory).toAbsolutePath().resolve(path).normalize();
    }
    public static String save(String directory, String version, Serializable bundle) throws IOException {
        Path dir = Path.of(directory).toAbsolutePath().normalize();
        Files.createDirectories(dir);
        Path file = dir.resolve(version + ".ser");
        // Unique versions + CREATE_NEW protect existing artifacts; failed files are retained for diagnosis.
        try (ObjectOutputStream out = new ObjectOutputStream(Files.newOutputStream(file, StandardOpenOption.CREATE_NEW))) {
            out.writeObject(bundle);
        }
        return file.toString();
    }
    public static <T> T load(String directory, String artifact, Class<T> type) throws IOException, ClassNotFoundException {
        try (ObjectInputStream in = new ObjectInputStream(Files.newInputStream(resolve(directory, artifact)))) {
            Object value = in.readObject();
            if (!type.isInstance(value)) throw new IOException("Unsupported order risk model artifact type");
            return type.cast(value);
        }
    }
}
