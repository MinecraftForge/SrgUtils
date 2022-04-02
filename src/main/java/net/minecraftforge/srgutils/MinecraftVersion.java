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

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MinecraftVersion implements Comparable<MinecraftVersion> {
    private static final Pattern SNAPSHOT_PATTERN = Pattern.compile("(\\d{2})w(\\d{2})([a-z]+)");
    public static MinecraftVersion NEGATIVE = from("-1");

    public static MinecraftVersion from(String version) {
        try {
            return new MinecraftVersion(version);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) { //This is dirty, TODO, make less exceptioney
            throw new IllegalArgumentException("Unknown version: " + version, e);
        }
    }

    private static String fromSnapshot(int year, int week) {
        int value = (year * 100) + week;
        if (value >= 1147 && value <= 1201) return "1.1";
        if (value >= 1203 && value <= 1208) return "1.2";
        if (value >= 1215 && value <= 1230) return "1.3";
        if (value >= 1232 && value <= 1242) return "1.4";
        if (value >= 1249 && value <= 1250) return "1.4.6";
        if (value >= 1301 && value <= 1310) return "1.5";
        if (value >= 1311 && value <= 1312) return "1.5.1";
        if (value >= 1316 && value <= 1326) return "1.6";
        if (value >= 1336 && value <= 1343) return "1.7";
        if (value >= 1347 && value <= 1349) return "1.7.4";
        if (value >= 1402 && value <= 1434) return "1.8";
        if (value >= 1531 && value <= 1607) return "1.9";
        if (value >= 1614 && value <= 1615) return "1.9.3";
        if (value >= 1620 && value <= 1621) return "1.10";
        if (value >= 1632 && value <= 1644) return "1.11";
        if (value >= 1650 && value <= 1650) return "1.11.1";
        if (value >= 1706 && value <= 1718) return "1.12";
        if (value >= 1731 && value <= 1731) return "1.12.1";
        if (value >= 1743 && value <= 1822) return "1.13";
        if (value >= 1830 && value <= 1833) return "1.13.1";
        if (value >= 1843 && value <= 1914) return "1.14";
        if (value >= 1934 && value <= 1946) return "1.15";
        if (value >= 2006 && value <= 2022) return "1.16";
        if (value >= 2027 && value <= 2030) return "1.16.2";
        if (value >= 2045 && value <= 9999) return "1.17";
        throw new IllegalArgumentException("Invalid snapshot date: " + value);
    }

    private static int[] splitDots(String version) {
        String[] pts = version.split("\\.");
        int[] values = new int[pts.length];
        for (int x = 0; x < pts.length; x++)
            values[x] = Integer.parseInt(pts[x]);
        return values;
    }

    private final Type type;
    private final String full;
    private final int[] nearest;
    private final int week;
    private final int year;
    private final int pre;
    private final String revision;

    private MinecraftVersion(String version) {
        this.full = version;
        String lower = version.toLowerCase(Locale.ENGLISH);
        char first = this.full.charAt(0);

        if ("15w14a".equals(this.full)) { //2015 April Fools
            this.week = 14;
            this.year = 15;
            this.type = Type.SNAPSHOT;
            this.nearest = splitDots("1.10");
            this.pre = 0;
            this.revision = "a";
        } else if ("1.rv-pre1".equals(lower)) { //2016 April Fools
            this.week = 14;
            this.year = 16;
            this.type = Type.SNAPSHOT;
            this.nearest = splitDots("1.9.3");
            this.pre = 0;
            this.revision = "`";
        } else if ("3d shareware v1.34".equals(lower)) { //2019 April Fools
            this.week = 14;
            this.year = 19;
            this.type = Type.SNAPSHOT;
            this.nearest = splitDots("1.14");
            this.pre = 0;
            this.revision = "`";
        } else if ("inf-20100618".equals(lower)) {
            this.week = 25;
            this.year = 10;
            this.type = Type.ALPHA;
            this.nearest = splitDots("1.0.4");
            this.pre = 0;
            this.revision = "a";
        } else if (lower.startsWith("rd-")) {
            this.year = 9;
            this.type = Type.ALPHA;
            this.pre = 0;
            this.nearest = splitDots("0.0.1");
            switch (lower) {
                case "rd-132211":
                    this.week = 20;
                    this.revision = "a";
                    break;
                case "rd-132328":
                    this.week = 20;
                    this.revision = "b";
                    break;
                case "rd-20090515":
                    this.week = 20;
                    this.revision = "c";
                    break;
                case "rd-160052":
                    this.week = 20;
                    this.revision = "d";
                    break;
                case "rd-161348":
                    this.week = 20;
                    this.revision = "e";
                    break;
                default:
                   throw new IllegalArgumentException("Unknown 'rd' version: " + full);
            }
        } else if ("c0.0.13a_03".equals(lower)) { //Rather then screw with the logic of the alpha/beta parser, special case this weird one
            this.week = -1;
            this.year = -1;
            this.type = Type.ALPHA;
            this.revision = "`";
            this.nearest = splitDots("0.0.13");
            this.pre = 0;
        } else if (first == 'a' || first == 'b' || first == 'c') {
            this.week = -1;
            this.year = -1;
            this.type = first == 'b' ? Type.BETA : Type.ALPHA;
            String clean = this.full.substring(1).replace('_', '.');
            char end = clean.charAt(clean.length() - 1);
            if (end < '0' || end > '9') {
                this.revision = Character.toString(end);
                this.nearest = splitDots(clean.substring(0, clean.length() - 1));
            } else {
                this.revision = null;
                this.nearest = splitDots(clean);
            }
            this.pre = 0;
        } else if (SNAPSHOT_PATTERN.matcher(lower).matches()) {
            Matcher matcher = SNAPSHOT_PATTERN.matcher(lower);
            matcher.matches();
            this.year = Integer.parseInt(matcher.group(1));
            this.week = Integer.parseInt(matcher.group(2));
            this.revision = matcher.group(3);
            this.type = Type.SNAPSHOT;
            this.nearest = splitDots(fromSnapshot(this.year, this.week));
            this.pre = 0;
        } else {
            this.week = -1;
            this.year = -1;
            this.type = Type.RELEASE;
            this.revision = null;
            if (this.full.contains("-pre")) {
                String[] pts = full.split("-pre");
                this.pre = Integer.parseInt(pts[1]);
                this.nearest = splitDots(pts[0]);
            } else if (this.full.contains("_Pre-Release_")) {
                String[] pts = full.split("_Pre-Release_");
                this.pre = Integer.parseInt(pts[1]);
                this.nearest = splitDots(pts[0]);
            } else if (this.full.contains(" Pre-Release ")) {
                String[] pts = full.split(" Pre-Release ");
                this.pre = Integer.parseInt(pts[1]);
                this.nearest = splitDots(pts[0]);
            } else if (this.full.contains("-rc")) {
                String[] pts = full.split("-rc");
                this.pre = -1 * Integer.parseInt(pts[1]);
                this.nearest = splitDots(pts[0]);
            } else {
                this.pre = 0;
                this.nearest = splitDots(full);
            }
        }
    }

    @Override
    public String toString() {
        return this.full;
    }

    @Override
    public int hashCode() {
        return this.full.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof MinecraftVersion))
            return false;
        return this.full.equals(((MinecraftVersion)o).full);
    }

    @Override
    public int compareTo(MinecraftVersion o) {
        if (o == null)
            return 1;

        if (this.type != o.type) {
            int ret = 0;
            switch (this.type) {
                case ALPHA: return -1;
                case BETA:  return o.type == Type.ALPHA ? 1 : -1;
                case SNAPSHOT:
                    ret = compareFull(o);
                    return ret == 0 ? -1 : ret;
                case RELEASE:
                    switch (o.type) {
                        case ALPHA:
                        case BETA: return 1;
                        case SNAPSHOT:
                        case RELEASE:
                            ret = compareFull(o);
                            return ret == 0 ? 1 : ret;
                    }
            }
        }

        switch (this.type) {
            case ALPHA:
            case BETA:
                int ret = compareFull(o);
                if (ret != 0)                                    return ret;
                if (this.revision == null && o.revision != null) return  1;
                if (this.revision != null && o.revision == null) return -1;
                return this.revision.compareTo(o.revision);

            case SNAPSHOT:
                if (this.year != o.year) return this.year - o.year;
                if (this.week != o.week) return this.week - o.week;
                return this.revision.compareTo(o.revision);

            case RELEASE:
                return compareFull(o);

            default:
                throw new IllegalArgumentException("Invalid type: " + this.type);
        }
    }

    private int compareFull(MinecraftVersion o) {
        for (int x = 0; x < this.nearest.length; x++) {
            if (x >= o.nearest.length) return 1;
            if (this.nearest[x] != o.nearest[x])
                return this.nearest[x] - o.nearest[x];
        }
        if (this.nearest.length < o.nearest.length)
            return -1;
        if (this.type == Type.RELEASE && o.type != Type.RELEASE) return 1;
        if (this.type != Type.RELEASE && o.type == Type.RELEASE) return -1;
        //Release candidates have negative numbers to make them sort differently then pre releases.
        if (this.pre == 0 && o.pre != 0) return 1;
        if (this.pre != 0 && o.pre == 0) return -1;
        if (this.pre < 0 && o.pre > 0) return 1;
        if (this.pre > 0 && o.pre < 0) return -1;
        return this.pre > 0 ? this.pre - o.pre :
            -this.pre - -o.pre;
    }

    private static enum Type {
        RELEASE,
        SNAPSHOT,
        BETA,
        ALPHA
    }
}
