package com.novax;

/** Simple event types for the custom UI toolkit. */
public final class Events {
    private Events() {}

    public static class Mouse {
        public enum Type { MOVE, DOWN, UP, SCROLL }
        public final Type type;
        public final float x, y;
        public final int button;   // 0=left 1=right 2=middle
        public final float scrollY;
        public final int mods;
        public Mouse(Type type, float x, float y, int button, float scrollY, int mods) {
            this.type = type; this.x = x; this.y = y;
            this.button = button; this.scrollY = scrollY; this.mods = mods;
        }
        public boolean left() { return button == 0; }
    }

    public static class Key {
        public enum Type { PRESS, RELEASE, REPEAT, CHAR }
        public final Type type;
        public final int key, scancode, mods, codepoint;
        public Key(Type type, int key, int scancode, int mods, int codepoint) {
            this.type = type; this.key = key; this.scancode = scancode;
            this.mods = mods; this.codepoint = codepoint;
        }
        public boolean ctrl() { return (mods & 0x2) != 0; }
        public boolean shift() { return (mods & 0x1) != 0; }
    }
}