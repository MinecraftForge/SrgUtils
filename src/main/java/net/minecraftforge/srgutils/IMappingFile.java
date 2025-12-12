/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.srgutils;

import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;

public interface IMappingFile {
    public static IMappingFile load(File path) throws IOException {
        try (InputStream in = new FileInputStream(path)) {
            if (path.getName().endsWith(".gz"))
                return load(new GZIPInputStream(in));
            return load(in);
        }
    }

    public static IMappingFile load(InputStream in) throws IOException {
        return InternalUtils.load(in);
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

    void write(Path path, Format format, boolean reversed) throws IOException;

    IMappingFile reverse();
    IMappingFile rename(IRenamer renamer);

    /**
     * Chains this mapping file with another.
     * Any extra mappings in the other file that is not used are discarded.
     * For example:
     * A mapping file with A -> B chained with a mapping file B -> C
     * will result in a chained file of A -> C.
     *
     * @param other the other mapping file to chain with
     * @return the resulting chained mapping file
     */
    IMappingFile chain(IMappingFile other);

    /**
     * Merges this mapping file with another.
     * Any mappings in the other file that already exist in this file will be discarded.
     * All entries in this mapping file are preserved.
     * This operation is purely additive based on the contents of the other file.
     *
     * @param other the other mapping file to merge into this one
     * @return the resulting merged mapping file
     */
    IMappingFile merge(IMappingFile other);

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
        @Nullable
        IParameter getParameter(int index);
    }

    public interface IParameter extends IOwnedNode<IMethod> {
        int getIndex();
    }
}
