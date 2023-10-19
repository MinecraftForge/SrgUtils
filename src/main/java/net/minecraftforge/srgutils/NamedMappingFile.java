/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.srgutils;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import net.minecraftforge.srgutils.IMappingFile.Format;

import static net.minecraftforge.srgutils.IMappingFile.Format.*;
import static net.minecraftforge.srgutils.InternalUtils.Element.*;
import static net.minecraftforge.srgutils.InternalUtils.*;

class NamedMappingFile implements INamedMappingFile, IMappingBuilder {
    private final List<String> names;
    private final Map<String, Package> packages = new HashMap<>();
    private final Map<String, Cls> classes = new HashMap<>();
    private final Map<String, String[]> classCache = new ConcurrentHashMap<>();
    private final Map<String, IMappingFile> mapCache = new ConcurrentHashMap<>(); //TODO: Weak?

    NamedMappingFile(String... names) {
        if (names == null || names.length < 2)
            throw new IllegalArgumentException("Can not create Mapping file with less then two names");
        this.names = Collections.unmodifiableList(Arrays.asList(names));
    }

    private void ensureCount(String... names) {
        if (names == null) throw new IllegalArgumentException("Names can not be null");
        if (names.length != this.names.size()) throw new IllegalArgumentException("Invalid number of names, expected " + this.names.size() + " got " + names.length);
    }

    @Override
    public List<String> getNames() {
        return this.names;
    }

    @Override
    public IMappingFile getMap(final String from, final String to) {
        String key = from + "_to_" + to;
        return mapCache.computeIfAbsent(key, k -> {
            int fromI = this.names.indexOf(from);
            int toI = this.names.indexOf(to);
            if (fromI == -1 || toI == -1)
                throw new IllegalArgumentException("Could not find mapping names: " + from + " / " + to);
            return new MappingFile(this, fromI, toI);
        });
    }

    @Override
    public void write(Path path, Format format, String... order) throws IOException {
        if (order == null || order.length == 1)
            throw new IllegalArgumentException("Invalid order, you must specify atleast 2 names");

        if (!format.hasNames() && order.length > 2)
            throw new IllegalArgumentException("Can not write " + order + " in " + format.name() + " format, it does not support headers");

        int[] indexes = new int[order.length];
        for (int x = 0; x < order.length; x++) {
            indexes[x] = this.getNames().indexOf(order[x]);
            if (indexes[x] == -1)
                throw new IllegalArgumentException("Invalid order: Missing \"" + order[x] + "\" name");
        }


        List<String> lines = new ArrayList<>();
        Comparator<Named> sort = (a,b) -> a.getName(indexes[0]).compareTo(b.getName(indexes[0]));

        getPackages().sorted(sort).forEachOrdered(pkg -> {
            lines.add(pkg.write(format, indexes));
            writeMeta(format, lines, PACKAGE, pkg.meta);
        });
        getClasses().sorted(sort).forEachOrdered(cls -> {
            lines.add(cls.write(format, indexes));
            writeMeta(format, lines, CLASS, cls.meta);

            cls.getFields().sorted(sort).forEachOrdered(fld -> {
                lines.add(fld.write(format, indexes));
                writeMeta(format, lines, FIELD, fld.meta);
            });

            cls.getMethods().sorted(sort).forEachOrdered(mtd -> {
                lines.add(mtd.write(format, indexes));
                writeMeta(format, lines, METHOD, mtd.meta);

                mtd.getParameters().sorted((a,b) -> a.getIndex() - b.getIndex()).forEachOrdered(par -> {
                    lines.add(par.write(format, indexes));
                    writeMeta(format, lines, PARAMETER, par.meta);
                });
            });
        });

        lines.removeIf(Objects::isNull);

        if (!format.isOrdered()) {
            Comparator<String> linesort = (format == SRG || format == XSRG) ? InternalUtils::compareLines : (o1, o2) -> o1.compareTo(o2);
            lines.sort(linesort);
        }

        if (format == TINY1 || format == TINY) {
            StringBuilder buf = new StringBuilder();
            buf.append(format == TINY ? "tiny\t2\t0" : "v1");
            for (String name : order)
                buf.append('\t').append(name);
            lines.add(0, buf.toString());
        } else if (format == TSRG2) {
            StringBuilder buf = new StringBuilder();
            buf.append("tsrg2");
            for (String name : order)
                buf.append(' ').append(name);
            lines.add(0, buf.toString());
        }

        Files.createDirectories(path.getParent());
        try (BufferedWriter writer = Files.newBufferedWriter(path)) {
            for (String line : lines) {
                writer.write(line);
                writer.write('\n');
            }
        }
    }

    // Internal Utilities
    private static <K, V> V retPut(Map<K, V> map, K key, V value) {
        map.put(key, value);
        return value;
    }

    private String remapClass(int index, String cls) {
        String[] ret = remapClass(cls);
        return ret[ret.length == 1 ? 0 : index];
    }

    private String[] remapClass(String cls) {
        String[] ret = classCache.get(cls);
        if (ret == null) {
            Cls _cls = classes.get(cls);
            if (_cls == null) {
                int idx = cls.lastIndexOf('$');
                if (idx != -1) {
                    String[] parent = remapClass(cls.substring(0, idx));
                    ret = new String[parent.length];
                    for (int x = 0; x < ret.length; x++)
                        ret[x] = parent[x] + '$' + cls.substring(idx + 1);
                } else
                    ret = new String[]{ cls };
            } else
                ret = _cls.getNames();
            classCache.put(cls, ret);
        }
        return ret;
    }

    private String remapDescriptor(int index, String desc) {
        Matcher matcher = MappingFile.DESC.matcher(desc);
        StringBuffer buf = new StringBuffer();
        while (matcher.find())
            matcher.appendReplacement(buf, Matcher.quoteReplacement("L" + remapClass(index, matcher.group("cls")) + ";"));
        matcher.appendTail(buf);
        return buf.toString();
    }

    // Accesses for converting to MappingFile
    Stream<Package> getPackages() {
        return this.packages.values().stream();
    }

    Stream<Cls> getClasses() {
        return this.classes.values().stream();
    }

    // Builder functions, only called from InternalUtils/reading
    @Override
    public Package addPackage(String... names) {
        ensureCount(names);
        return retPut(this.packages, names[0], new Package(names));
    }

    @Override
    public Cls addClass(String... names) {
        ensureCount(names);
        return retPut(this.classes, names[0], new Cls(names));
    }

    @Override
    public INamedMappingFile build() {
        return this;
    }

    @Nullable
    Cls getClass(String name) {
        return this.classes.get(name);
    }

    abstract class Named {
        private final String[] names;

        Named(String... names) {
            this.names = names;
        }

        public String getName(int index) {
            return this.names[index];
        }

        String[] getNames() {
            return this.names;
        }

        protected String getNames(int... order) {
            StringBuilder ret = new StringBuilder();
            for (int index : order)
                ret.append('\t').append(getName(index));
            return ret.toString();
        }

        abstract String write(Format format, int... order);
    }

    class Package extends Named implements IMappingBuilder.IPackage {
        final Map<String, String> meta = new LinkedHashMap<>();

        Package(String... names) {
            super(names);
        }

        @Override
        String write(Format format, int... order) {
            switch (format) {
                case SRG:
                case XSRG: return "PK: " + getName(order[0]) + ' ' + getName(order[1]);
                case CSRG:
                case TSRG: return getName(order[0]) + "/ " + getName(order[1]) + '/';
                case TSRG2: return getTsrg2(order);
                case PG:
                case TINY1:
                case TINY: return null;
                default: throw new UnsupportedOperationException("Unknown format: " + format);
            }
        }

        private String getTsrg2(int... order) {
            StringBuilder ret = new StringBuilder();
            for (int x = 0; x < order.length; x++) {
                ret.append(getName(x)).append('/');
                if (x != order.length - 1)
                    ret.append(' ');
            }
            return ret.toString();
        }

        @Override
        public IPackage meta(String key, String value) {
            meta.put(key, value);
            return this;
        }

        @Override
        public IMappingBuilder build() {
            return NamedMappingFile.this;
        }
    }

    class Cls extends Named implements IMappingBuilder.IClass {
        private final Map<String, Field> fields = new HashMap<>();
        private final Map<String, Method> methods = new HashMap<>();
        final Map<String, String> meta = new LinkedHashMap<>();

        Cls(String... name) {
            super(name);
        }

        Stream<Field> getFields() {
            return this.fields.values().stream();
        }

        Stream<Method> getMethods() {
            return this.methods.values().stream();
        }

        @Override
        public Field field(String... names) {
            ensureCount(names);
            return retPut(this.fields, names[0], new Field(names));
        }

        @Override
        public Method method(String desc, String... names) {
            ensureCount(names);
            return retPut(this.methods, names[0] + desc, new Method(desc, names));
        }

        @Override
        public IClass meta(String key, String value) {
            this.meta.put(key, value);
            return this;
        }

        @Override
        public IMappingBuilder build() {
            return NamedMappingFile.this;
        }

        @Override
        String write(Format format, int... order) {
            switch (format) {
                case SRG:
                case XSRG:  return "CL: " + getName(order[0]) + ' ' + getName(order[1]);
                case CSRG:
                case TSRG:  return getName(order[0]) + ' ' + getName(order[1]);
                case TSRG2: return getTsrg2(order);
                case PG:    return getName(order[0]).replace('/', '.') + " -> " + getName(order[1]).replace('/', '.') + ':';
                case TINY1: return "CLASS" + getNames(order);
                case TINY:  return "c" + getNames(order);
                default: throw new UnsupportedOperationException("Unknown format: " + format);
            }
        }

        private String getTsrg2(int... order) {
            StringBuilder ret = new StringBuilder();
            for (int x = 0; x < order.length; x++) {
                ret.append(getName(x));
                if (x != order.length - 1)
                    ret.append(' ');
            }
            return ret.toString();
        }

        class Field extends Named implements IMappingBuilder.IField {
            @Nullable
            private String desc;
            final Map<String, String> meta = new LinkedHashMap<>();

            Field(String... names) {
                super(names);
            }

            public String getDescriptor(int index) {
                return this.desc == null ? null : index == 0 ? this.desc : NamedMappingFile.this.remapDescriptor(index, this.desc);
            }

            @Override
            public IField descriptor(String value) {
                this.desc = value;
                return this;
            }

            @Override
            public IField meta(String key, String value) {
                this.meta.put(key, value);
                return this;
            }

            @Override
            public IClass build() {
                return Cls.this;
            }

            @Override
            String write(Format format, int... order) {
                switch (format) {
                    case SRG:   return "FD: " + Cls.this.getName(order[0]) + '/' + getName(order[0]) + ' ' + Cls.this.getName(order[1]) + '/' + getName(order[1]) + (this.desc == null ? "" : getDescriptor(order[0]) + ' ' + getDescriptor(order[1]));
                    case XSRG:  return "FD: " + Cls.this.getName(order[0]) + '/' + getName(order[0]) + (this.desc == null ? "" : getDescriptor(order[0])) + ' ' + Cls.this.getName(order[1]) + '/' + getName(order[1]) + (this.desc == null ? "" : getDescriptor(order[1]));
                    case CSRG:  return Cls.this.getName(order[0]) + ' ' + getName(order[0]) + ' ' + getName(order[1]);
                    case TSRG:  return '\t' + getName(order[0]) + ' ' + getName(order[1]);
                    case TSRG2: return getTsrg2(order);
                    case PG:    return "    " + InternalUtils.toSource(getDescriptor(order[0])) + ' ' + getName(order[0]) + " -> " + getName(order[1]);
                    case TINY1: return "FIELD\t" + Cls.this.getName(order[0]) + '\t' + getDescriptor(order[0]) + getNames(order);
                    case TINY:  return "\tf\t" + getDescriptor(order[0]) + getNames(order);
                    default: throw new UnsupportedOperationException("Unknown format: " + format);
                }
            }

            private String getTsrg2(int... order) {
                StringBuilder ret = new StringBuilder().append('\t');
                for (int x = 0; x < order.length; x++) {
                    ret.append(getName(x));
                    if (x == 0 && getDescriptor(order[x]) != null)
                        ret.append(' ').append(getDescriptor(order[x]));
                    if (x != order.length - 1)
                        ret.append(' ');
                }
                return ret.toString();
            }
        }

        class Method extends Named implements IMappingBuilder.IMethod {
            private final String desc;
            private final Map<Integer, Parameter> params = new HashMap<>();
            final Map<String, String> meta = new LinkedHashMap<>();

            Method(String desc, String... names) {
                super(names);
                this.desc = desc;
            }

            @Override
            public IParameter parameter(int index, String... names) {
                ensureCount(names);
                return retPut(this.params, index, new Parameter(index, names));
            }

            @Override
            public IMethod meta(String key, String value) {
                this.meta.put(key, value);
                return this;
            }

            @Override
            public IClass build() {
                return Cls.this;
            }

            public String getDescriptor(int index) {
                return index == 0 ? this.desc : NamedMappingFile.this.remapDescriptor(index, this.desc);
            }

            Stream<Parameter> getParameters() {
                return this.params.values().stream();
            }

            @Override
            String write(Format format, int... order) {
                String oOwner = Cls.this.getName(order[0]);
                String oName = getName(order[0]);
                String mName = getName(order[1]);
                String oDesc = getDescriptor(order[0]);

                switch (format) {
                    case SRG:
                    case XSRG: return "MD: " + oOwner + '/' + oName + ' ' + oDesc + ' ' + Cls.this.getName(order[1]) + '/' + mName + ' ' + getDescriptor(order[1]);
                    case CSRG: return oOwner + ' ' + oName + ' ' + oDesc + ' ' + mName;
                    case TSRG: return '\t' + oName + ' ' + oDesc + ' ' + mName;
                    case TSRG2: return getTsrg2(order);
                    case TINY1: return "METHOD\t" + oOwner + '\t' + oDesc + getNames(order);
                    case TINY: return "\tm\t" + oDesc + getNames(order);
                    case PG:
                        int start = Integer.parseInt(meta.getOrDefault("start_line", "0"));
                        int end = Integer.parseInt(meta.getOrDefault("end_line", "0"));
                        return "    " + (start == 0 && end == 0 ? "" : start + ":" + end + ":") + InternalUtils.toSource(oName, oDesc) + " -> " + mName;
                    default: throw new UnsupportedOperationException("Unknown format: " + format);
                }
            }

            private String getTsrg2(int... order) {
                StringBuilder ret = new StringBuilder().append('\t');
                for (int x = 0; x < order.length; x++) {
                    ret.append(getName(x));
                    if (x == 0 && getDescriptor(order[x]) != null)
                        ret.append(' ').append(getDescriptor(order[x]));
                    if (x != order.length - 1)
                        ret.append(' ');
                }
                return ret.toString();
            }

            class Parameter extends Named implements IMappingBuilder.IParameter {
                private final int index;
                final Map<String, String> meta = new LinkedHashMap<>();

                Parameter(int index, String... names) {
                    super(names);
                    this.index = index;
                }

                public int getIndex() {
                    return this.index;
                }

                @Override
                String write(Format format, int... order) {
                    switch (format) {
                        case SRG:
                        case XSRG:
                        case CSRG:
                        case TSRG:
                        case PG:
                        case TINY1: return null;
                        case TINY: return "\t\tp\t" + getIndex() + getNames(order);
                        case TSRG2: return getTsrg2(order);
                        default: throw new UnsupportedOperationException("Unknown format: " + format);
                    }
                }

                private String getTsrg2(int... order) {
                    StringBuilder ret = new StringBuilder()
                        .append("\t\t").append(getIndex());
                    for (int x = 0; x < order.length; x++)
                        ret.append(' ').append(getName(x));
                    return ret.toString();
                }

                @Override
                public IParameter meta(String key, String value) {
                    this.meta.put(key, value);
                    return this;
                }

                @Override
                public IMethod build() {
                    return Method.this;
                }
            }
        }
    }
}
