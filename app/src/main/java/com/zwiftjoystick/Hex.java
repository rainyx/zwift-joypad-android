package com.zwiftjoystick;

final class Hex {
    private Hex() {}

    static String of(byte[] b) {
        return of(b, 0, b.length);
    }

    static String of(byte[] b, int off, int len) {
        StringBuilder sb = new StringBuilder(len * 3);
        for (int i = off; i < off + len; i++) {
            if (i > off) sb.append(' ');
            sb.append(String.format("%02X", b[i] & 0xFF));
        }
        return sb.toString();
    }

    static byte[] parse(String s) {
        String t = s.replaceAll("[^0-9A-Fa-f]", "");
        byte[] out = new byte[t.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(t.substring(2 * i, 2 * i + 2), 16);
        }
        return out;
    }
}
