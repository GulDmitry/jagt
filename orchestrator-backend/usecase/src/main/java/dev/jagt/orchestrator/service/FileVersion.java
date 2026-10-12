package dev.jagt.orchestrator.service;

import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;

/** A file's content is taken as unchanged while this stays equal; a rename-over-write changes the key. */
public record FileVersion(FileTime modified, long size, @Nullable Object key) {

    /** Null when the attributes cannot be read, which no cached version equals. */
    public static @Nullable FileVersion of(Path file) {
        try {
            BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);
            return new FileVersion(attributes.lastModifiedTime(), attributes.size(), attributes.fileKey());
        } catch (IOException e) {
            return null;
        }
    }
}
