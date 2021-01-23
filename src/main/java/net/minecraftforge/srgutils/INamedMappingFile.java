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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;

import net.minecraftforge.srgutils.IMappingFile.Format;

public interface INamedMappingFile {
    public static INamedMappingFile load(File path) throws IOException {
        try (InputStream in = new FileInputStream(path)) {
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
