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
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

class InternalUtils {
    static IMappingFile load(InputStream in) throws IOException {
        INamedMappingFile named = loadNamed(in);
        return named.getMap(named.getNames().get(0), named.getNames().get(1));
    }

    static INamedMappingFile loadNamed(InputStream in) throws IOException {
        List<String> lines = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)).lines()
            //.map(InternalUtils::stripComment)
            .filter(l -> !l.isEmpty()) //Remove Empty lines
            .collect(Collectors.toList());


        String firstLine = lines.get(0);
        String test = firstLine.split(" ")[0];

        if ("PK:".equals(test) || "CL:".equals(test) || "FD:".equals(test) || "MD:".equals(test)) //SRG
            return loadSRG(filter(lines));
        else if(firstLine.contains(" -> ")) // ProGuard
            return loadProguard(filter(lines));
        else if (firstLine.startsWith("v1\t")) // Tiny V1
            return loadTinyV1(lines);
        else if (firstLine.startsWith("tiny\t")) // Tiny V2+
            return loadTinyV2(lines);
        else if (firstLine.startsWith("tsrg2 ")) // TSRG v2, parameters, and multi-names
            return loadTSrg2(lines);
        else // TSRG/CSRG
            return loadSlimSRG(filter(lines));
    }

    private static List<String> filter(List<String> lines) {
        return lines.stream().map(InternalUtils::stripComment)
        .filter(l -> !l.isEmpty()) //Remove Empty lines
        .collect(Collectors.toList());
    }

    private static INamedMappingFile loadSRG(List<String> lines) throws IOException {
        NamedMappingFile ret = new NamedMappingFile();
        for (String line : lines) {
            String[] pts = line.split(" ");
            switch (pts[0]) {
                case "PK:": ret.addPackage(pts[1], pts[2]); break;
                case "CL:": ret.addClass(pts[1], pts[2]); break;
                case "FD:":
                    if (pts.length == 5)
                        ret.getOrCreateClass(rsplit(pts[1], '/', 1)[0]).addField(pts[2], rsplit(pts[1], '/', 1)[1], rsplit(pts[3], '/', 1)[1]);
                    else
                        ret.getOrCreateClass(rsplit(pts[1], '/', 1)[0]).addField(null,   rsplit(pts[1], '/', 1)[1], rsplit(pts[2], '/', 1)[1]);
                    break;
                case "MD:": ret.getOrCreateClass(rsplit(pts[1], '/', 1)[0]).addMethod(0, 0, pts[2], rsplit(pts[1], '/', 1)[1], rsplit(pts[3], '/', 1)[1]); break;
                default:
                    throw new IOException("Invalid SRG file, Unknown type: " + line);
            }
        }
        return ret;
    }

    private static INamedMappingFile loadProguard(List<String> lines) throws IOException {
        NamedMappingFile ret = new NamedMappingFile();

        for (String line : lines) {
            if (!line.startsWith("    ") && line.endsWith(":")) {
                String[] pts = line.replace('.', '/').split(" -> ");
                ret.addClass(pts[0], pts[1].substring(0, pts[1].length() - 1));
            }
        }

        NamedMappingFile.Cls cls = null;
        for (String line : lines) {
            line = line.replace('.', '/');
            if (!line.startsWith("    ") && line.endsWith(":")) {
                //Classes we already did this in the first pass
                cls = ret.getClass(line.split(" -> ")[0]);
            } else if (line.contains("(") && line.contains(")")) {
                if (cls == null)
                    throw new IOException("Invalid PG line, missing class: " + line);

                line = line.trim();
                int start = 0;
                int end = 0;
                if (line.indexOf(':') != -1) {
                    int i = line.indexOf(':');
                    int j = line.indexOf(':', i + 1);
                    start = Integer.parseInt(line.substring(0,     i));
                    end   = Integer.parseInt(line.substring(i + 1, j));
                    line = line.substring(j + 1);
                }

                String obf = line.split(" -> ")[1];
                String _ret = toDesc(line.split(" ")[0]);
                String name = line.substring(line.indexOf(' ') + 1, line.indexOf('('));
                String[] args = line.substring(line.indexOf('(') + 1, line.indexOf(')')).split(",");

                StringBuffer desc = new StringBuffer();
                desc.append('(');
                for (String arg : args) {
                    if (arg.isEmpty()) break;
                    desc.append(toDesc(arg));
                }
                desc.append(')').append(_ret);
                cls.addMethod(start, end, desc.toString(), obf, name);
            } else {
                if (cls == null)
                    throw new IOException("Invalid PG line, missing class: " + line);
                String[] pts = line.trim().split(" ");
                cls.addField(toDesc(pts[0]), pts[1], pts[3]);
            }
        }

        return ret;
    }

    private static NamedMappingFile loadSlimSRG(List<String> lines) throws IOException {
        NamedMappingFile ret = new NamedMappingFile();

        lines.stream().filter(l -> l.charAt(0) != '\t')
        .map(l -> l.split(" "))
        .filter(pts -> pts.length == 2)
        .forEach(pts -> {
            if (pts[0].endsWith("/"))
                ret.addPackage(pts[0].substring(0, pts[0].length() - 1), pts[1].substring(0, pts[1].length() -1));
            else
                ret.addClass(pts[0], pts[1]);
        });

        NamedMappingFile.Cls cls = null;
        for (String line : lines) {
            String[] pts = line.split(" ");
            if (pts[0].charAt(0) == '\t') {
                if (cls == null)
                    throw new IOException("Invalid TSRG line, missing class: " + line);
                pts[0] = pts[0].substring(1);
                if (pts.length == 2)
                    cls.addField(null, pts[0], pts[1]);
                else if (pts.length == 3)
                    cls.addMethod(0, 0, pts[1], pts[0], pts[2]);
                else
                    throw new IOException("Invalid TSRG line, to many parts: " + line);
            } else {
                if (pts.length == 2) {
                    if (!pts[0].endsWith("/"))
                        cls = ret.getClass(pts[0]);
                }
                else if (pts.length == 3)
                    ret.getClass(pts[0]).addField(null, pts[1], pts[2]);
                else if (pts.length == 4)
                    ret.getClass(pts[0]).addMethod(0, 0, pts[2], pts[1], pts[3]);
                else
                    throw new IOException("Invalid CSRG line, to many parts: " + line);
            }
        }

        return ret;
    }

    private static INamedMappingFile loadTSrg2(List<String> lines) throws IOException {
        /*
         *   This is a extended spec of the TSRG format, mainly to allow multiple names
         * for entries, consolidating our files into a single one, parameter names, and
         * optional descriptors for fields {Doesn't really matter in MC modding, but why not..}
         *
         * Multiple names:
         *   The header line defines how many extra names are allowed.
         *   tsrg2 [name ...]
         *
         * Field Descriptors:
         *   If a line would be a method line, but the descriptor does not start with '(' it is a field with a descriptor
         *
         * Parameters:
         *   tabbed in one extra time below method nodes.
         *   \t\tindex [name ...]
         *
         * Things we do not care about:
         *   Local variables:
         *     In theory this would be useful if decompilers were perfect and matched up
         *     things perfectly, but they don't so it's not really worth caring.
         *   Comments:
         *     This format is targeted towards binary files, comments are added else ware.
         *   Line numbers:
         *     I can't see a use for this
         */
        String[] header = lines.get(0).split(" ");
        if (header.length < 3) throw new IOException("Invalid TSrg v2 Header: " + lines.get(0));
        NamedMappingFile ret = new NamedMappingFile(Arrays.copyOfRange(header, 1, header.length));
        int nameCount = header.length - 1;

        NamedMappingFile.Cls cls = null;
        NamedMappingFile.Cls.Method mtd = null;
        for (String line : lines) {
            if (line.length() < 2)
                throw new IOException("Invalid TSRG v2 line, too short: " + line);

            String[] pts = line.split(" ");
            if (line.charAt(0) != '\t') { // Classes or Packages are not tabbed
                if (pts.length != nameCount)
                    throw new IOException("Invalid TSRG v2 line: " + line);
                if (pts[0].charAt(pts[0].length()) == '/') { // Packages
                    for (int x = 0; x < pts.length; x++)
                        pts[x] = pts[x].substring(0, pts[x].length() - 1);
                    ret.addPackage(pts);
                    cls = null;
                } else
                    cls = ret.addClass(pts);
                mtd = null;
            } else if (line.charAt(1) == '\t') { //Parameter
                if (mtd == null)
                    throw new IOException("Invalid TSRG v2 line, missing method: " + line);
                pts[0] = pts[0].substring(2, pts[0].length());
                mtd.addParameter(Integer.parseInt(pts[0]), Arrays.copyOfRange(pts, 1, pts.length));
            } else {
                if (cls == null)
                    throw new IOException("Invalid TSRG v2 line, missing class: " + line);
                pts[0] = pts[0].substring(1, pts[0].length());
                if (pts.length == 1 + nameCount) // Field without descriptor
                    cls.addField(null, Arrays.copyOfRange(pts, 1, pts.length));
                else if (pts.length == 2 + nameCount) {
                    swapFirst(pts);
                    if (pts[0].charAt(0) == '(') { // Methods
                        mtd = cls.addMethod(0, 0, pts[0], Arrays.copyOfRange(pts, 1, pts.length));
                    } else { // Field with Descriptor
                        mtd = null;
                        cls.addField(pts[0], Arrays.copyOfRange(pts, 1, pts.length));
                    }
                } else
                    throw new IOException("Invalid TSRG v2 line, to many parts: " + line);
            }
        }

        return ret;
    }

    private static INamedMappingFile loadTinyV1(List<String> lines) throws IOException {
        /*
         *  The entire file is just a list tab-separated-value lines.
         *  It can have a unlimited number of name steps, The first part of the header is always 'v1'
         *  anything extra tells us the names of mapping stages. So we build a bunch of maps from the first value to the Nth value
         */
        String[] header = lines.get(0).split("\t");
        if (header.length < 3) throw new IOException("Invalid Tiny v1 Header: " + lines.get(0));
        NamedMappingFile ret = new NamedMappingFile(Arrays.copyOfRange(header, 1, header.length));

        for (int x = 1; x < lines.size(); x++) {
            String[] line = lines.get(x).split("\t");
            switch (line[0]) {
                case "CLASS": // CLASS Name1 Name2 Name3...
                    if (line.length != header.length)
                        throw new IOException("Invalid Tiny v1 line: #" + x + ": " + line);
                    ret.addClass(Arrays.copyOfRange(line, 1, line.length));
                    break;
                case "FIELD": // FIELD Owner Desc Name1 Name2 Name3
                    if (line.length != header.length  + 2)
                        throw new IOException("Invalid Tiny v1 line: #" + x + ": " + line);
                    ret.getOrCreateClass(line[1]).addField(line[2], Arrays.copyOfRange(line, 3, line.length));
                    break;
                case "METHOD": // METHOD Owner Desc Name1 Name2 Name3
                    if (line.length != header.length  + 2)
                        throw new IOException("Invalid Tiny v1 line: #" + x + ": " + line);
                    ret.getOrCreateClass(line[1]).addMethod(0, 0, line[2], Arrays.copyOfRange(line, 3, line.length));
                    break;
                default:
                    throw new IOException("Invalid Tiny v1 line: #" + x + ": " + line);
            }
        }

        return ret;
    }

    private static INamedMappingFile loadTinyV2(List<String> lines) throws IOException {
        /*
         * This is the only spec I could find on it, so i'm assuming its official:
         * https://github.com/FabricMC/tiny-remapper/issues/9
         */
        String[] header = lines.get(0).split("\t");
        if (header.length < 5) throw new IOException("Invalid Tiny v2 Header: " + lines.get(0));

        try {
            int major = Integer.parseInt(header[1]);
            int minor = Integer.parseInt(header[2]);
            if (major != 2 || minor != 0)
                throw new IOException("Unsupported Tiny v2 version: " + lines.get(0));
        } catch (NumberFormatException e) {
            throw new IOException("Invalid Tiny v2 Header: " + lines.get(0));
        }
        NamedMappingFile ret = new NamedMappingFile(Arrays.copyOfRange(header, 3, header.length));

        int nameCount = ret.getNames().size();
        boolean escaped = false;
        Map<String, String> properties = new HashMap<>();
        int start = 1;
        for(start = 1; start < lines.size(); start++) {
            String[] line = lines.get(start).split("\t");
            if (!line[0].isEmpty())
                break;

            properties.put(line[1], line.length < 3 ? null : escaped ? unescapeTinyString(line[2]) : line[2]);
            if ("escaped-names".equals(line[1]))
                escaped = true;
        }

        Deque<TinyV2State> stack = new ArrayDeque<>();
        NamedMappingFile.Cls cls = null;
        //NamedMappingFile.Cls.Field field = null;
        NamedMappingFile.Cls.Method method = null;

        for (int x = start; x < lines.size(); x++) {
            String line = lines.get(x);

            int newdepth = 0;
            while (line.charAt(newdepth) == '\t')
                newdepth++;
            if (newdepth != 0)
                line = line.substring(newdepth);

            if (newdepth != stack.size()) {
                while (stack.size() != newdepth)
                    stack.pop();
            }

            String[] parts = line.split("\t");
            if (escaped) {
                for (int y = 1; y < parts.length; y++)
                    parts[y] = unescapeTinyString(parts[y]);
            }

            switch (parts[0]) {
                case "c":
                    if (stack.size() == 0) { // Class: c Name1 Name2 Name3
                        if (parts.length != nameCount + 1)
                            throw new IOException("Invalid Tiny v2 line: #" + x + ": " + line);

                        cls = ret.addClass(Arrays.copyOfRange(parts, 1, parts.length));
                        stack.push(TinyV2State.CLASS);
                    } else { // Comment
                        //String comment = unescapeTinyString(parts[1]);
                        //TODO: Do we care?
                    }
                    break;
                case "f": // Field: f desc Name1 Name2 Name3
                    if (parts.length != nameCount + 2 || stack.peek() != TinyV2State.CLASS)
                        throw new IOException("Invalid Tiny v2 line: #" + x + ": " + line);

                    /*field =*/ cls.addField(parts[1], Arrays.copyOfRange(parts, 2, parts.length));
                    stack.push(TinyV2State.FIELD);

                    break;

                case "m": // Method: m desc Name1 Name2 Name3
                    if (parts.length != nameCount + 2 || stack.peek() != TinyV2State.CLASS)
                        throw new IOException("Invalid Tiny v2 line: #" + x + ": " + line);

                    method = cls.addMethod(0, 0, parts[1], Arrays.copyOfRange(parts, 2, parts.length));
                    stack.push(TinyV2State.METHOD);

                    break;

                case "p": // Parameters: p index Name1 Name2 Name3
                    if (parts.length != nameCount + 2 || stack.peek() != TinyV2State.METHOD)
                        throw new IOException("Invalid Tiny v2 line: #" + x + ": " + line);

                    method.addParameter(Integer.parseInt(parts[1]), Arrays.copyOfRange(parts, 2, parts.length));
                    stack.push(TinyV2State.PARAMETER);

                    break;
                case "v": // Local Variable: v index start Name1 Name2 Name3?
                    break; //TODO: Unsupported, is this used? Should we expose it?
                default:
                    throw new IOException("Invalid Tiny v2 line: #" + x + ": " + line);
            }
        }

        return ret;
    }
    enum TinyV2State { ROOT, CLASS, FIELD, METHOD, PARAMETER }

    /* <escaped-string> is a string that must not contain <eol> and escapes
     *     \ to \\
     *     "\n" to \n
     *     "\r" to \r
     *     "\t" to \t
     *     "\0" to \0
     * */
    private static String unescapeTinyString(String value) {
        return value.replace("\\\\", "\\")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\0", "\0");
    }

    static String escapeTinyString(String value) {
        return value.replace("\\", "\\\\")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
                .replace("\0", "\\0");
    }

    static String toDesc(String type) {
        if (type.endsWith("[]"))    return "[" + toDesc(type.substring(0, type.length() - 2));
        if (type.equals("int"))     return "I";
        if (type.equals("void"))    return "V";
        if (type.equals("boolean")) return "Z";
        if (type.equals("byte"))    return "B";
        if (type.equals("char"))    return "C";
        if (type.equals("short"))   return "S";
        if (type.equals("double"))  return "D";
        if (type.equals("float"))   return "F";
        if (type.equals("long"))    return "J";
        if (type.contains("/"))     return "L" + type + ";";
        throw new RuntimeException("Invalid toDesc input: " + type);
    }

    static String toSource(String desc) {
        char first = desc.charAt(0);
        switch (first) {
            case 'I': return "int";
            case 'V': return "void";
            case 'Z': return "boolean";
            case 'B': return "byte";
            case 'C': return "char";
            case 'S': return "short";
            case 'D': return "double";
            case 'F': return "float";
            case 'J': return "long";
            case '[': return toSource(desc.substring(1)) + "[]";
            case 'L': return desc.substring(1, desc.length() - 1).replace('/', '.');
            default: throw new IllegalArgumentException("Unknown descriptor: " + desc);
        }
    }

    static String toSource(String name, String desc) {
        StringBuilder buf = new StringBuilder();
        int endParams = desc.lastIndexOf(')');
        String ret = desc.substring(endParams + 1);
        buf.append(toSource(ret)).append(' ').append(name).append('(');

        int idx = 1;
        while (idx < endParams) {
            int array = 0;
            char c = desc.charAt(idx);
            if (c == '[') {
                while (desc.charAt(idx) == '[') {
                    array++;
                    idx++;
                }
                c = desc.charAt(idx);
            }
            if (c == 'L') {
                int end = desc.indexOf(';', idx);
                buf.append(toSource(desc.substring(idx, end + 1)));
                idx = end;
            } else {
                buf.append(toSource(c + ""));
            }

            while (array-- > 0)
                buf.append("[]");

            idx++;
            if (idx < endParams)
                buf.append(',');
        }
        buf.append(')');
        return buf.toString();
    }

    private static String[] rsplit(String str, char chr, int count) {
        List<String> pts = new ArrayList<>();
        int idx;
        while ((idx = str.lastIndexOf(chr)) != -1 && count > 0) {
            pts.add(str.substring(idx + 1));
            str = str.substring(0, idx);
            count--;
        }
        pts.add(str);
        Collections.reverse(pts);
        return pts.toArray(new String[pts.size()]);
    }

    private static final List<String> ORDER = Arrays.asList("PK:", "CL:", "FD:", "MD:");
    public static int compareLines(String o1, String o2) {
        String[] pt1 = o1.split(" ");
        String[] pt2 = o2.split(" ");
        if (!pt1[0].equals(pt2[0]))
            return ORDER.indexOf(pt1[0]) - ORDER.lastIndexOf(pt2[0]);

        if ("PK:".equals(pt1[0]))
            return o1.compareTo(o2);
        if ("CL:".equals(pt1[0]))
            return compareCls(pt1[1], pt2[1]);
        if ("FD:".equals(pt1[0]) || "MD:".equals(pt1[0]))
        {
            String[][] y = {
                {pt1[1].substring(0, pt1[1].lastIndexOf('/')), pt1[1].substring(pt1[1].lastIndexOf('/') + 1)},
                {pt2[1].substring(0, pt2[1].lastIndexOf('/')), pt2[1].substring(pt2[1].lastIndexOf('/') + 1)}
            };
            int ret = compareCls(y[0][0], y[1][0]);
            if (ret != 0)
                return ret;
            return y[0][1].compareTo(y[1][1]);
        }
        return o1.compareTo(o2);
    }

    public static int compareCls(String cls1, String cls2) {
        if (cls1.indexOf('/') > 0 && cls2.indexOf('/') > 0)
            return cls1.compareTo(cls2);
        String[][] t = { cls1.split("\\$"), cls2.split("\\$") };
        int max = Math.min(t[0].length, t[1].length);
        for (int i = 0; i < max; i++)
        {
            if (!t[0][i].equals(t[1][i]))
            {
                if (t[0][i].length() != t[1][i].length())
                    return t[0][i].length() - t[1][i].length();
                return t[0][i].compareTo(t[1][i]);
            }
        }
        return Integer.compare(t[0].length, t[1].length);
    }

    public static String stripComment(String str) {
        int idx = str.indexOf('#');
        if (idx == 0)
            return "";
        if (idx != -1)
            str = str.substring(0, idx - 1);
        int end = str.length();
        while (end > 1 && str.charAt(end - 1) == ' ')
            end--;
        return end == 0 ? "" : str.substring(0, end);
    }

    private static void swapFirst(String[] values) {
        String tmp = values[0];
        values[0] = values[1];
        values[1] = tmp;
    }
}
