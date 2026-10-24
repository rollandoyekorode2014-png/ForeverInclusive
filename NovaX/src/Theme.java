package com.novax;

/** Colors, sizes and theme loading for NovaX dark-neon look. */
public final class Theme {
    public String name = "NovaX Dark Neon";
    public float[] background   = hex("#0D0D12");
    public float[] surface      = hex("#16161F");
    public float[] surfaceHover = hex("#1E1E2A");
    public float[] accent       = hex("#FF2A6D");
    public float[] textPrimary  = hex("#F0F0F5");
    public float[] textSecondary= hex("#A0A0B0");
    public float[] border       = hex("#2A2A3A");
    public float[] titleBar     = hex("#0A0A0F");
    public float[] tabActive    = hex("#1A1A28");
    public float[] danger       = hex("#FF3B3B");
    public float[] addressBg    = hex("#12121A");
    public float radius = 6f;
    public float titleBarH = 36f;
    public float tabBarH = 34f;
    public float addrBarH = 40f;
    public float fontSize = 14f;

    private static Theme CURRENT = new Theme();
    public static Theme get() { return CURRENT; }

    public static float[] hex(String h) {
        h = h.replace("#", "");
        if (h.length() == 6) h += "FF";
        long v = Long.parseLong(h, 16);
        return new float[]{
                ((v >> 24) & 0xFF) / 255f,
                ((v >> 16) & 0xFF) / 255f,
                ((v >> 8) & 0xFF) / 255f,
                (v & 0xFF) / 255f
        };
    }
}