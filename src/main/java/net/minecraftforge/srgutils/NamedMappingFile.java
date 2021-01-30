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

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import net.minecraftforge.srgutils.IMappingFile.Format;

class NamedMappingFile implements INamedMappingFile {
    private final List<String> names;
    private Map<String, Package> packages = new HashMap<>();
    private Map<String, Cls> classes = new HashMap<>();
    private Map<String, String[]> classCache = new HashMap<>();
    private Map<String, IMappingFile> mapCache = new HashMap<>(); //TODO: Weak?

    NamedMappingFile() {
        this("left", "right");
    }

    NamedMappingFile(String... names) {
        this.names = Collections.unmodifiableList(Arrays.asList(names));
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

        getPackages().sorted(sort).map(e -> e.write(format, indexes)).filter(s -> s != null).forEachOrdered(lines::add);
        getClasses().sorted(sort).forEachOrdered(cls -> {
            lines.add(cls.write(format, indexes));
            cls.getFields().sorted(sort).map(e -> e.write(format, indexes)).forEachOrdered(lines::add);
            cls.getMethods().sorted(sort).forEachOrdered(mtd -> {
                lines.add(mtd.write(format, indexes));
                mtd.getParameters().sorted((a,b) -> a.getIndex() - b.getIndex()).map(e -> e.write(format, indexes)).filter(s -> s != null).forEachOrdered(lines::add);
            });
        });
        if (!format.isOrdered()) {
            Comparator<String> linesort = (format == Format.SRG || format == Format.XSRG) ? InternalUtils::compareLines : (o1, o2) -> o1.compareTo(o2);
            Collections.sort(lines, linesort);
        }

        if (format == Format.TINY1 || format == Format.TINY) {
            StringBuilder buf = new StringBuilder();
            buf.append(format == Format.TINY ? "tiny\t2\t0" : "v1");
            for (String name : order)
                buf.append('\t').append(name);
            lines.add(0, buf.toString());
        } else if (format == Format.TSRG2) {
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

    private String[] duplicate(String value) {
        String[] ret = new String[this.names.size()];
        Arrays.fill(ret, value);
        return ret;
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
                    ret = remapClass(cls.substring(0, idx));
                    for (int x = 0; x < ret.length; x++)
                        ret[x] += '$' + cls.substring(idx + 1);
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
    Package addPackage(String... names) {
        return retPut(this.packages, names[0], new Package(names));
    }

    Cls addClass(String... names) {
        return retPut(this.classes, names[0], new Cls(names));
    }

    @Nullable
    Cls getClass(String name) {
        return this.classes.get(name);
    }

    Cls getOrCreateClass(String name) {
        return this.classes.computeIfAbsent(name, k -> new Cls(duplicate(name)));
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

    class Package extends Named {
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
    }

    class Cls extends Named {
        private final Map<String, Field> fields = new HashMap<>();
        private final Map<String, Method> methods = new HashMap<>();

        Cls(String... name) {
            super(name);
        }

        Stream<Field> getFields() {
            return this.fields.values().stream();
        }

        Stream<Method> getMethods() {
            return this.methods.values().stream();
        }

        Field addField(@Nullable String desc, String... names) {
            return retPut(this.fields, names[0], new Field(desc, names));
        }

        Method addMethod(int start, int end, String desc, String... names) {
            return retPut(this.methods, names[0] + desc, new Method(start, end, desc, names));
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

        class Field extends Named {
            @Nullable
            private final String desc;

            Field(@Nullable String desc, String... names) {
                super(names);
                this.desc = desc;
            }

            public String getDescriptor(int index) {
                return this.desc == null ? null : index == 0 ? this.desc : NamedMappingFile.this.remapDescriptor(index, this.desc);
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
                    case TINY1: return "FIELD" + getNames(order);
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

        class Method extends Named {
            private final String desc;
            private final int start, end;
            private final Map<Integer, Parameter> params = new HashMap<>();

            Method(int start, int end, String desc, String... names) {
                super(names);
                this.desc = desc;
                this.start = start;
                this.end = end;
            }

            public String getDescriptor(int index) {
                return index == 0 ? this.desc : NamedMappingFile.this.remapDescriptor(index, this.desc);
            }

            public int getStart() {
                return this.start;
            }

            public int getEnd() {
                return this.end;
            }

            Stream<Parameter> getParameters() {
                return this.params.values().stream();
            }

            Parameter addParameter(int index, String... names) {
                return retPut(this.params, index, new Parameter(index, names));
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
                    case PG:   return "    " + (start == 0 && end == 0 ? "" : start + ":" + end + ":") + InternalUtils.toSource(oName, oDesc) + " -> " + mName;
                    case TINY1: return "METHOD\t" + oOwner + '\t' + oDesc + getNames(order);
                    case TINY: return "\tm\t" + oDesc + getNames(order);
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

            class Parameter extends Named {
                private final int index;
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
            }
        }
    }
}
