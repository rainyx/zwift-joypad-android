package com.zwiftjoystick;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 最小 protobuf 编解码（只覆盖 Zwift ZP 用到的 varint / length-delimited）。 */
final class Pb {
    private Pb() {}

    static final class Writer {
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();

        Writer varint(int field, long value) {
            rawVarint(((long) field << 3));
            rawVarint(value);
            return this;
        }

        Writer sint(int field, int value) {
            return varint(field, ((value << 1) ^ (value >> 31)) & 0xFFFFFFFFL);
        }

        Writer bool(int field, boolean value) {
            return varint(field, value ? 1 : 0);
        }

        Writer bytes(int field, byte[] value) {
            rawVarint(((long) field << 3) | 2);
            rawVarint(value.length);
            out.write(value, 0, value.length);
            return this;
        }

        Writer message(int field, Writer msg) {
            return bytes(field, msg.toByteArray());
        }

        private void rawVarint(long v) {
            while ((v & ~0x7FL) != 0) {
                out.write((int) ((v & 0x7F) | 0x80));
                v >>>= 7;
            }
            out.write((int) v);
        }

        byte[] toByteArray() {
            return out.toByteArray();
        }
    }

    /** 解析结果：field -> 值列表（Long 或 byte[]）。 */
    static Map<Integer, List<Object>> parse(byte[] buf, int off, int len) {
        Map<Integer, List<Object>> m = new LinkedHashMap<>();
        int[] pos = {off};
        int end = off + len;
        try {
            while (pos[0] < end) {
                long key = readVarint(buf, pos);
                int field = (int) (key >>> 3);
                int wt = (int) (key & 7);
                Object v;
                switch (wt) {
                    case 0:
                        v = readVarint(buf, pos);
                        break;
                    case 2: {
                        int l = (int) readVarint(buf, pos);
                        byte[] b = new byte[l];
                        System.arraycopy(buf, pos[0], b, 0, l);
                        pos[0] += l;
                        v = b;
                        break;
                    }
                    case 5: {
                        long x = 0;
                        for (int i = 0; i < 4; i++) x |= (long) (buf[pos[0]++] & 0xFF) << (8 * i);
                        v = x;
                        break;
                    }
                    case 1: {
                        long x = 0;
                        for (int i = 0; i < 8; i++) x |= (long) (buf[pos[0]++] & 0xFF) << (8 * i);
                        v = x;
                        break;
                    }
                    default:
                        return m;
                }
                m.computeIfAbsent(field, k -> new ArrayList<>()).add(v);
            }
        } catch (RuntimeException e) {
            // 截断数据：返回已解析部分
        }
        return m;
    }

    static Map<Integer, List<Object>> parse(byte[] buf) {
        return parse(buf, 0, buf.length);
    }

    static long readVarint(byte[] b, int[] pos) {
        long r = 0;
        int shift = 0;
        while (true) {
            int c = b[pos[0]++] & 0xFF;
            r |= (long) (c & 0x7F) << shift;
            if ((c & 0x80) == 0) return r;
            shift += 7;
        }
    }

    static long getLong(Map<Integer, List<Object>> m, int field, long def) {
        List<Object> l = m.get(field);
        if (l == null || l.isEmpty() || !(l.get(0) instanceof Long)) return def;
        return (Long) l.get(0);
    }

    static byte[] getBytes(Map<Integer, List<Object>> m, int field) {
        List<Object> l = m.get(field);
        if (l == null || l.isEmpty() || !(l.get(0) instanceof byte[])) return null;
        return (byte[]) l.get(0);
    }

    static int zigzagDecode(long v) {
        return (int) ((v >>> 1) ^ -(v & 1));
    }

    /** 无 schema 的通用展开，用于日志。 */
    static String dump(byte[] buf) {
        StringBuilder sb = new StringBuilder();
        dumpInto(sb, buf, 0);
        return sb.toString();
    }

    private static void dumpInto(StringBuilder sb, byte[] buf, int depth) {
        Map<Integer, List<Object>> m = parse(buf);
        sb.append('{');
        boolean first = true;
        for (Map.Entry<Integer, List<Object>> e : m.entrySet()) {
            for (Object v : e.getValue()) {
                if (!first) sb.append(", ");
                first = false;
                sb.append(e.getKey()).append(':');
                if (v instanceof Long) {
                    sb.append(v);
                } else {
                    byte[] b = (byte[]) v;
                    if (depth < 4 && b.length > 0 && looksLikeMessage(b)) {
                        dumpInto(sb, b, depth + 1);
                    } else {
                        sb.append('[').append(Hex.of(b)).append(']');
                    }
                }
            }
        }
        sb.append('}');
    }

    private static boolean looksLikeMessage(byte[] b) {
        int[] pos = {0};
        try {
            while (pos[0] < b.length) {
                long key = readVarint(b, pos);
                int field = (int) (key >>> 3);
                int wt = (int) (key & 7);
                if (field == 0 || field > 64) return false;
                if (wt == 0) readVarint(b, pos);
                else if (wt == 2) {
                    long l = readVarint(b, pos);
                    if (l < 0 || pos[0] + l > b.length) return false;
                    pos[0] += (int) l;
                } else return false;
            }
            return pos[0] == b.length;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
