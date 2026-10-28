package hu.blockeater;

/** Számformázás: 1 234 567 */
public final class Fmt {
    private Fmt() {}

    public static String num(long v) {
        String s = Long.toString(Math.abs(v));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (i > 0 && (s.length() - i) % 3 == 0) sb.append(' ');
            sb.append(s.charAt(i));
        }
        return (v < 0 ? "-" : "") + sb;
    }

    public static int clampInt(long v) {
        return (int) Math.max(0, Math.min(Integer.MAX_VALUE, v));
    }
}
