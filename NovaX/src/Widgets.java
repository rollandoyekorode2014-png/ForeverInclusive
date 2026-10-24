package com.novax;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import static org.lwjgl.nanovg.NanoVG.*;
import static org.lwjgl.glfw.GLFW.*;

/**
 * Complete custom widget toolkit (no Swing/AWT/JavaFX).
 * Contains: Widget base, Panel, Label, Button, TextField.
 */
public final class Widgets {
    private Widgets() {}

    public static abstract class Widget {
        public float x, y, w, h;
        public final List<Widget> children = new ArrayList<>();
        public Widget parent;
        public boolean visible = true, enabled = true, focused, hovered;

        public void add(Widget c) { children.add(c); c.parent = this; }
        public void setBounds(float x, float y, float w, float h) {
            this.x = x; this.y = y; this.w = w; this.h = h;
        }
        public boolean contains(float px, float py) {
            return px >= x && px <= x + w && py >= y && py <= y + h;
        }
        public void layout() { for (Widget c : children) c.layout(); }

        public final void render(long nvg) {
            if (!visible) return;
            renderSelf(nvg);
            for (Widget c : children) c.render(nvg);
        }
        protected abstract void renderSelf(long nvg);

        public boolean dispatchMouse(Events.Mouse e) {
            if (!visible || !enabled) return false;
            for (int i = children.size() - 1; i >= 0; i--) {
                Widget c = children.get(i);
                if (c.contains(e.x, e.y) || c.hovered) {
                    if (c.dispatchMouse(e)) return true;
                }
            }
            return onMouse(e);
        }
        protected boolean onMouse(Events.Mouse e) {
            boolean inside = contains(e.x, e.y);
            if (e.type == Events.Mouse.Type.MOVE) {
                if (inside && !hovered) { hovered = true; }
                else if (!inside && hovered) { hovered = false; }
            }
            return false;
        }

        public boolean dispatchKey(Events.Key e) {
            if (!visible || !enabled) return false;
            if (focused && onKey(e)) return true;
            for (Widget c : children) if (c.dispatchKey(e)) return true;
            return false;
        }
        protected boolean onKey(Events.Key e) { return false; }

        public Widget findFocusable(float px, float py) {
            for (int i = children.size() - 1; i >= 0; i--) {
                Widget c = children.get(i);
                if (c.visible && c.contains(px, py)) {
                    Widget f = c.findFocusable(px, py);
                    if (f != null) return f;
                }
            }
            return focusable() ? this : null;
        }
        protected boolean focusable() { return false; }
    }

    public static class Panel extends Widget {
        public float[] bg;
        public float radius = -1;
        public boolean drawBg = true;
        public Panel() {}
        public Panel(float[] bg) { this.bg = bg; }
        @Override protected void renderSelf(long nvg) {
            if (!drawBg) return;
            Theme t = Theme.get();
            float[] c = bg != null ? bg : t.surface;
            float r = radius >= 0 ? radius : t.radius;
            nvgBeginPath(nvg);
            nvgRoundedRect(nvg, x, y, w, h, r);
            nvgFillColor(nvg, rgba(c));
            nvgFill(nvg);
        }
    }

    public static class Label extends Widget {
        public String text = "";
        public float[] color;
        public float fontSize = -1;
        public boolean bold;
        public int align = NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE;
        public Label() {}
        public Label(String text) { this.text = text != null ? text : ""; }
        public void setText(String s) { text = s != null ? s : ""; }
        @Override protected void renderSelf(long nvg) {
            if (text.isEmpty()) return;
            Theme t = Theme.get();
            float size = fontSize > 0 ? fontSize : t.fontSize;
            float[] col = color != null ? color : t.textPrimary;
            nvgFontSize(nvg, size);
            nvgFontFace(nvg, bold ? "sans-bold" : "sans");
            nvgTextAlign(nvg, align);
            nvgFillColor(nvg, rgba(col));
            float tx = x;
            if ((align & NVG_ALIGN_CENTER) != 0) tx = x + w * 0.5f;
            else if ((align & NVG_ALIGN_RIGHT) != 0) tx = x + w;
            nvgText(nvg, tx, y + h * 0.5f, text);
        }
    }

    public static class Button extends Widget {
        public String text = "";
        public Consumer<Button> onClick;
        public float[] bgColor;
        private boolean pressed;
        public Button() {}
        public Button(String text) { this.text = text != null ? text : ""; }
        public void setOnClick(Consumer<Button> h) { onClick = h; }
        @Override protected boolean focusable() { return true; }
        @Override protected boolean onMouse(Events.Mouse e) {
            super.onMouse(e);
            if (!contains(e.x, e.y)) { if (pressed) pressed = false; return false; }
            if (e.type == Events.Mouse.Type.DOWN && e.left()) { pressed = true; return true; }
            if (e.type == Events.Mouse.Type.UP && e.left() && pressed) {
                pressed = false;
                if (onClick != null) onClick.accept(this);
                return true;
            }
            return false;
        }
        @Override protected void renderSelf(long nvg) {
            Theme t = Theme.get();
            float[] bg = pressed ? t.accent : (hovered ? (bgColor != null ? bgColor : t.surfaceHover) : (bgColor != null ? bgColor : t.surface));
            nvgBeginPath(nvg);
            nvgRoundedRect(nvg, x, y, w, h, t.radius);
            nvgFillColor(nvg, rgba(bg));
            nvgFill(nvg);
            if (hovered || pressed) {
                nvgBeginPath(nvg);
                nvgRoundedRect(nvg, x, y, w, h, t.radius);
                nvgStrokeColor(nvg, rgba(t.accent));
                nvgStrokeWidth(nvg, 1.5f);
                nvgStroke(nvg);
            }
            nvgFontSize(nvg, t.fontSize);
            nvgFontFace(nvg, "sans");
            nvgTextAlign(nvg, NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE);
            nvgFillColor(nvg, rgba(t.textPrimary));
            nvgText(nvg, x + w * 0.5f, y + h * 0.5f, text);
        }
    }

    public static class TextField extends Widget {
        private final StringBuilder text = new StringBuilder();
        private int cursor;
        private long lastBlink = System.currentTimeMillis();
        private boolean cursorOn = true;
        public String placeholder = "";
        public Consumer<String> onSubmit, onChange;

        public TextField() {}
        public TextField(String init) {
            if (init != null) { text.append(init); cursor = text.length(); }
        }
        public String getText() { return text.toString(); }
        public void setText(String s) {
            text.setLength(0);
            if (s != null) text.append(s);
            cursor = text.length();
            if (onChange != null) onChange.accept(getText());
        }
        @Override protected boolean focusable() { return true; }

        @Override protected boolean onMouse(Events.Mouse e) {
            super.onMouse(e);
            if (e.type == Events.Mouse.Type.DOWN && e.left() && contains(e.x, e.y)) {
                cursor = text.length();
                return true;
            }
            return false;
        }

        @Override protected boolean onKey(Events.Key e) {
            if (e.type == Events.Key.Type.CHAR) {
                if (e.codepoint >= 32) {
                    text.insert(cursor, (char) e.codepoint);
                    cursor++;
                    if (onChange != null) onChange.accept(getText());
                }
                return true;
            }
            if (e.type != Events.Key.Type.PRESS && e.type != Events.Key.Type.REPEAT) return false;
            switch (e.key) {
                case GLFW_KEY_BACKSPACE -> {
                    if (cursor > 0) { text.deleteCharAt(--cursor); if (onChange != null) onChange.accept(getText()); }
                    return true;
                }
                case GLFW_KEY_DELETE -> {
                    if (cursor < text.length()) { text.deleteCharAt(cursor); if (onChange != null) onChange.accept(getText()); }
                    return true;
                }
                case GLFW_KEY_LEFT -> { if (cursor > 0) cursor--; return true; }
                case GLFW_KEY_RIGHT -> { if (cursor < text.length()) cursor++; return true; }
                case GLFW_KEY_HOME -> { cursor = 0; return true; }
                case GLFW_KEY_END -> { cursor = text.length(); return true; }
                case GLFW_KEY_ENTER, GLFW_KEY_KP_ENTER -> {
                    if (onSubmit != null) onSubmit.accept(getText());
                    return true;
                }
                case GLFW_KEY_A -> {
                    if (e.ctrl()) { cursor = text.length(); return true; }
                }
            }
            return false;
        }

        @Override protected void renderSelf(long nvg) {
            Theme t = Theme.get();
            nvgBeginPath(nvg);
            nvgRoundedRect(nvg, x, y, w, h, t.radius);
            nvgFillColor(nvg, rgba(t.addressBg));
            nvgFill(nvg);
            nvgBeginPath(nvg);
            nvgRoundedRect(nvg, x, y, w, h, t.radius);
            nvgStrokeColor(nvg, rgba(focused ? t.accent : t.border));
            nvgStrokeWidth(nvg, focused ? 1.5f : 1f);
            nvgStroke(nvg);

            float pad = 10f;
            nvgFontSize(nvg, t.fontSize);
            nvgFontFace(nvg, "sans");
            nvgTextAlign(nvg, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
            String display = text.toString();
            if (display.isEmpty() && !focused) {
                nvgFillColor(nvg, rgba(t.textSecondary));
                nvgText(nvg, x + pad, y + h * 0.5f, placeholder);
                return;
            }
            nvgFillColor(nvg, rgba(t.textPrimary));
            nvgText(nvg, x + pad, y + h * 0.5f, display);

            long now = System.currentTimeMillis();
            if (now - lastBlink > 530) { cursorOn = !cursorOn; lastBlink = now; }
            if (focused && cursorOn) {
                float cx = x + pad + cursor * (t.fontSize * 0.55f);
                nvgBeginPath(nvg);
                nvgMoveTo(nvg, cx, y + 7);
                nvgLineTo(nvg, cx, y + h - 7);
                nvgStrokeColor(nvg, rgba(t.accent));
                nvgStrokeWidth(nvg, 1.5f);
                nvgStroke(nvg);
            }
        }
    }

    static org.lwjgl.nanovg.NVGColor rgba(float[] c) {
        return org.lwjgl.nanovg.NVGColor.calloc().r(c[0]).g(c[1]).b(c[2]).a(c[3]);
    }
}