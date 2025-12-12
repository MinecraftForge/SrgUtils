/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.srgutils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.GZIPInputStream;

import net.minecraftforge.srgutils.IMappingFile.Format;

public interface INamedMappingFile {
    public static INamedMappingFile load(File path) throws IOException {
        try (InputStream in = new FileInputStream(path)) {
            if (path.getName().endsWith(".gz"))
                return load(new GZIPInputStream(in));
            return load(in);
        }
    }

    public static INamedMappingFile load(InputStream in) throws IOException {
        return InternalUtils.loadNamed(in);
    }

    List<String> getNames();
    IMappingFile getMap(String from, String to);

    default void write(Path path, Format format) throws IOException {
        write(path, format, getNames().toArray(new String[getNames().size()]));
    }

    void write(Path path, Format format, String... order) throws IOException;
}
