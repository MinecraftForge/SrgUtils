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
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

public interface IMappingFile {
    public static IMappingFile load(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path)) {
            return load(reader);
        }
    }

    public static IMappingFile load(File path) throws IOException {
        try (InputStream in = new FileInputStream(path)) {
            return load(in);
        }
    }

    public static IMappingFile load(InputStream in) throws IOException {
        return load(new InputStreamReader(in));
    }

    public static IMappingFile load(Reader in) throws IOException {
        BufferedReader reader = in instanceof BufferedReader ? (BufferedReader) in : new BufferedReader(in);
        List<String> lines = reader.lines()
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toList());
        return InternalUtils.load(lines);
    }

    public static IMappingFile load(List<String> lines) throws IOException {
        List<String> input = lines.stream()
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toList());
        return InternalUtils.load(input);
    }

    public enum Format {
        SRG  (false, false, false),
        XSRG (false, true,  false),
        CSRG (false, false, false),
        TSRG (true,  false, false),
        TSRG2(true,  true,  true ),
        PG   (true,  true,  false),
        TINY1(false, true,  true ),
        TINY (true,  true,  false)
        ;

        private final boolean ordered;
        private final boolean hasFieldTypes;
        private final boolean hasNames;

        private Format(boolean ordered, boolean hasFieldTypes, boolean hasNames) {
            this.ordered = ordered;
            this.hasFieldTypes = hasFieldTypes;
            this.hasNames = hasNames;
        }

        public boolean isOrdered() {
            return this.ordered;
        }

        public boolean hasFieldTypes() {
            return this.hasFieldTypes;
        }

        public boolean hasNames() {
            return this.hasNames;
        }

        public static Format get(String name) {
            name = name.toUpperCase(Locale.ENGLISH);
            for (Format value : values())
                if (value.name().equals(name))
                    return value;
            return null;
        }
    }

    Collection<? extends IPackage> getPackages();
    IPackage getPackage(String original);

    Collection<? extends IClass> getClasses();
    IClass getClass(String original);

    String remapPackage(String pkg);
    String remapClass(String desc);
    String remapDescriptor(String desc);

    default void write(Path path, Format format, boolean reversed) throws IOException {
        List<String> lines = write(format, reversed);
        Files.createDirectories(path.getParent());
        try (BufferedWriter writer = Files.newBufferedWriter(path)) {
            for (String line : lines) {
                writer.write(line);
                writer.write('\n');
            }
        }
    }
    List<String> write(Format format, boolean reversed);

    IMappingFile reverse();
    IMappingFile rename(IRenamer renamer);
    IMappingFile chain(IMappingFile other);

    public interface INode {
        String getOriginal();
        String getMapped();
        @Nullable // Returns null if the specified format doesn't support this node type
        String write(Format format, boolean reversed);

        /*
         * A unmodifiable map of various metadata that is attached to this node.
         * This is very dependent on the format. Some examples:
         * Tiny v1/v2:
         *   "comment": Javadoc comment to insert into source
         * TSRG:
         *   On Methods:
         *     "is_static": Value means nothing, just a marker if it exists.
         * Proguard:
         *   On Methods:
         *     "start_line": The source line that this method starts on
         *     "end_line": The source line for the end of this method
         */
        Map<String, String> getMetadata();
    }

    public interface IPackage extends INode {}

    public interface IClass extends INode {
        Collection<? extends IField> getFields();
        Collection<? extends IMethod> getMethods();

        String remapField(String field);
        String remapMethod(String name, String desc);

        @Nullable
        IField getField(String name);
        @Nullable
        IMethod getMethod(String name, String desc);
    }

    public interface IOwnedNode<T> extends INode {
        T getParent();
    }

    public interface IField extends IOwnedNode<IClass> {
        @Nullable
        String getDescriptor();
        @Nullable
        String getMappedDescriptor();
    }

    public interface IMethod extends IOwnedNode<IClass> {
        String getDescriptor();
        String getMappedDescriptor();
        Collection<? extends IParameter> getParameters();
        String remapParameter(int index, String name);
    }

    public interface IParameter extends IOwnedNode<IMethod> {
        int getIndex();
    }
}
