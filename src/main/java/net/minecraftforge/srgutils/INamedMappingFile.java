/*
 * SRG Utils
 * Copyright (c) 2021
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation version 2.1
 * of the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

package net.minecraftforge.srgutils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import net.minecraftforge.srgutils.IMappingFile.Format;

public interface INamedMappingFile {
    public static INamedMappingFile load(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path)) {
            return load(reader);
        }
    }

    public static INamedMappingFile load(File path) throws IOException {
        try (InputStream in = new FileInputStream(path)) {
            return load(in);
        }
    }

    public static INamedMappingFile load(InputStream in) throws IOException {
        return load(new InputStreamReader(in));
    }

    public static INamedMappingFile load(Reader in) throws IOException {
        BufferedReader reader = in instanceof BufferedReader ? (BufferedReader) in : new BufferedReader(in);
        List<String> lines = reader.lines()
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toList());
        return InternalUtils.loadNamed(lines);
    }

    public static INamedMappingFile load(List<String> lines) throws IOException {
        List<String> input = lines.stream()
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toList());
        return InternalUtils.loadNamed(input);
    }

    List<String> getNames();
    IMappingFile getMap(String from, String to);

    default void write(Path path, Format format) throws IOException {
        write(path, format, getNames().toArray(new String[getNames().size()]));
    }

    default void write(Path path, Format format, String... order) throws IOException {
        List<String> lines = write(format, order);
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        try (BufferedWriter writer = Files.newBufferedWriter(path)) {
            for (String line : lines) {
                writer.write(line);
                writer.write('\n');
            }
        }
    }

    default List<String> write(Format format) {
        return write(format, getNames().toArray(new String[getNames().size()]));
    }

    List<String> write(Format format, String... order);
}
