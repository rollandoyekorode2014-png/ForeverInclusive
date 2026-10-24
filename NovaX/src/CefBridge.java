package com.novax;

import me.friwi.jcefmaven.CefAppBuilder;
import me.friwi.jcefmaven.MavenCefAppHandlerAdapter;
import org.cef.CefApp;
import org.cef.CefClient;
import org.cef.CefSettings;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.CefDisplayHandlerAdapter;
import org.cef.handler.CefLifeSpanHandlerAdapter;
import org.cef.handler.CefLoadHandlerAdapter;
import org.cef.handler.CefRenderHandlerAdapter;
import org.cef.callback.CefDragData;

import java.awt.Component;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_BGRA;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;

/**
 * JCEF Off-Screen Rendering bridge + OpenGL texture + BrowserView widget.
 *
 * AWT ISOLATION: JCEF needs Component/Rectangle/AWT events internally.
 * Confined to this one file. Rest of NovaX uses zero AWT/Swing/JavaFX.
 */
public class CefBridge {

    private static final AtomicBoolean INIT = new AtomicBoolean();
    private static CefApp cefApp;

    public static void init() throws Exception {
        if (INIT.getAndSet(true)) return;
        CefAppBuilder b = new CefAppBuilder();
        b.getCefSettings().windowless_rendering_enabled = true;
        b.getCefSettings().log_severity = CefSettings.LogSeverity.LOGSEVERITY_WARNING;
        b.getCefSettings().cache_path = System.getProperty("user.home") + "/.novax/cef_cache";
        b.setAppHandler(new MavenCefAppHandlerAdapter() {});
        cefApp = b.build();
        System.out.println("[NovaX] JCEF OSR initialized");
    }

    public static void shutdown() {
        if (cefApp != null) { cefApp.dispose(); cefApp = null; }
        INIT.set(false);
    }

    public static class Browser extends Widgets.Widget {
        private CefClient client;
        private CefBrowser browser;
        private final OSRHandler osr = new OSRHandler();
        private int texId;
        private boolean ready;
        private String pendingUrl = "https://www.google.com";
        private int lastW, lastH;
        public Consumer<String> onTitle, onAddress;

        public void start(String url) {
            pendingUrl = url != null ? url : "https://www.google.com";
            try {
                init();
                client = cefApp.createClient();
                client.addLifeSpanHandler(new CefLifeSpanHandlerAdapter() {
                    @Override public void onAfterCreated(CefBrowser b) {
                        System.out.println("[NovaX] Browser created");
                    }
                });
                client.addDisplayHandler(new CefDisplayHandlerAdapter() {
                    @Override public void onTitleChange(CefBrowser b, String title) {
                        if (onTitle != null) onTitle.accept(title);
                    }
                    @Override public void onAddressChange(CefBrowser b, CefFrame f, String url) {
                        if (f.isMain() && onAddress != null) onAddress.accept(url);
                    }
                });
                client.addLoadHandler(new CefLoadHandlerAdapter() {});
                browser = client.createBrowser(pendingUrl, true, false);
                browser.createImmediately();
                texId = glGenTextures();
                glBindTexture(GL_TEXTURE_2D, texId);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
                glBindTexture(GL_TEXTURE_2D, 0);
                ready = true;
                System.out.println("[NovaX] Loading " + pendingUrl);
            } catch (Exception e) {
                System.err.println("[NovaX] JCEF start failed: " + e.getMessage());
                e.printStackTrace();
            }
        }

        public void loadURL(String url) { if (ready && browser != null) browser.loadURL(url); }
        public void goBack()    { if (ready && browser != null) browser.goBack(); }
        public void goForward() { if (ready && browser != null) browser.goForward(); }
        public void reload()    { if (ready && browser != null) browser.reload(); }

        @Override public void layout() {
            super.layout();
            int bw = Math.max(1, (int) w), bh = Math.max(1, (int) h);
            if (ready && (bw != lastW || bh != lastH)) {
                browser.wasResized(bw, bh);
                osr.w = bw; osr.h = bh;
                lastW = bw; lastH = bh;
            }
        }

        @Override protected void renderSelf(long nvg) {
            nvgBeginPath(nvg);
            nvgRect(nvg, x, y, w, h);
            nvgFillColor(nvg, Widgets.rgba(Theme.hex("#0A0A10")));
            nvgFill(nvg);
            if (!ready) {
                nvgFontSize(nvg, 16);
                nvgFontFace(nvg, "sans");
                nvgTextAlign(nvg, org.lwjgl.nanovg.NanoVG.NVG_ALIGN_CENTER | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_MIDDLE);
                nvgFillColor(nvg, Widgets.rgba(Theme.get().accent));
                nvgText(nvg, x + w * 0.5f, y + h * 0.5f, "Loading Chromium…");
            }
        }

        public void drawGL() {
            if (!ready || osr.buf == null || osr.w <= 0) return;
            if (osr.dirty) {
                glBindTexture(GL_TEXTURE_2D, texId);
                if (osr.needResize) {
                    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, osr.w, osr.h, 0, GL_BGRA, GL_UNSIGNED_BYTE, osr.buf);
                    osr.needResize = false;
                } else {
                    glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, osr.w, osr.h, GL_BGRA, GL_UNSIGNED_BYTE, osr.buf);
                }
                glBindTexture(GL_TEXTURE_2D, 0);
                osr.dirty = false;
            }
        }

        @Override protected boolean onMouse(Events.Mouse e) {
            if (!ready || browser == null) return false;
            int lx = (int)(e.x - x), ly = (int)(e.y - y);
            if (lx < 0 || ly < 0 || lx > w || ly > h) return false;
            switch (e.type) {
                case MOVE -> browser.sendMouseEvent(awtMove(lx, ly));
                case DOWN -> browser.sendMouseEvent(awtButton(lx, ly, e.button, true));
                case UP   -> {
                    browser.sendMouseEvent(awtButton(lx, ly, e.button, false));
                    browser.sendMouseEvent(awtClick(lx, ly, e.button));
                }
                case SCROLL -> browser.sendMouseWheelEvent(awtWheel(lx, ly, (int)-e.scrollY));
            }
            return true;
        }

        @Override protected boolean onKey(Events.Key e) {
            if (!ready || !focused || browser == null) return false;
            if (e.type == Events.Key.Type.CHAR) {
                browser.sendKeyEvent(new KeyEvent(DUMMY, KeyEvent.KEY_TYPED, System.currentTimeMillis(), 0, 0, (char)e.codepoint));
                return true;
            }
            boolean press = e.type == Events.Key.Type.PRESS || e.type == Events.Key.Type.REPEAT;
            int id = press ? KeyEvent.KEY_PRESSED : KeyEvent.KEY_RELEASED;
            browser.sendKeyEvent(new KeyEvent(DUMMY, id, System.currentTimeMillis(), e.mods, mapKey(e.key), KeyEvent.CHAR_UNDEFINED));
            return true;
        }

        @Override protected boolean focusable() { return true; }

        public void dispose() {
            if (browser != null) { browser.close(true); browser = null; }
            if (client != null) { client.dispose(); client = null; }
            if (texId != 0) { glDeleteTextures(texId); texId = 0; }
        }

        private static final Component DUMMY = new Component() {};

        private MouseEvent awtMove(int x, int y) {
            return new MouseEvent(DUMMY, MouseEvent.MOUSE_MOVED, System.currentTimeMillis(), 0, x, y, 0, false);
        }
        private MouseEvent awtButton(int x, int y, int btn, boolean press) {
            int awtBtn = btn == 0 ? MouseEvent.BUTTON1 : (btn == 1 ? MouseEvent.BUTTON3 : MouseEvent.BUTTON2);
            int id = press ? MouseEvent.MOUSE_PRESSED : MouseEvent.MOUSE_RELEASED;
            return new MouseEvent(DUMMY, id, System.currentTimeMillis(), 0, x, y, 1, false, awtBtn);
        }
        private MouseEvent awtClick(int x, int y, int btn) {
            int awtBtn = btn == 0 ? MouseEvent.BUTTON1 : (btn == 1 ? MouseEvent.BUTTON3 : MouseEvent.BUTTON2);
            return new MouseEvent(DUMMY, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0, x, y, 1, false, awtBtn);
        }
        private MouseWheelEvent awtWheel(int x, int y, int rot) {
            return new MouseWheelEvent(DUMMY, MouseWheelEvent.MOUSE_WHEEL, System.currentTimeMillis(), 0, x, y, 0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, rot);
        }
        private int mapKey(int glfw) {
            if (glfw >= 65 && glfw <= 90) return glfw;
            if (glfw >= 48 && glfw <= 57) return glfw;
            return switch (glfw) {
                case 257 -> KeyEvent.VK_ENTER; case 259 -> KeyEvent.VK_BACK_SPACE;
                case 261 -> KeyEvent.VK_DELETE; case 263 -> KeyEvent.VK_LEFT;
                case 262 -> KeyEvent.VK_RIGHT; case 265 -> KeyEvent.VK_UP;
                case 264 -> KeyEvent.VK_DOWN; case 268 -> KeyEvent.VK_HOME;
                case 269 -> KeyEvent.VK_END; case 32 -> KeyEvent.VK_SPACE;
                case 256 -> KeyEvent.VK_ESCAPE; case 258 -> KeyEvent.VK_TAB;
                case 340, 344 -> KeyEvent.VK_SHIFT; case 341, 345 -> KeyEvent.VK_CONTROL;
                default -> KeyEvent.VK_UNDEFINED;
            };
        }

        static class OSRHandler extends CefRenderHandlerAdapter {
            ByteBuffer buf;
            volatile int w = 1, h = 1;
            volatile boolean dirty, needResize = true;
            @Override public Rectangle getViewRect(CefBrowser b) {
                return new Rectangle(0, 0, Math.max(1, w), Math.max(1, h));
            }
            @Override public void onPaint(CefBrowser b, boolean popup, Rectangle[] dirtyRects,
                                          ByteBuffer buffer, int width, int height) {
                w = width; h = height;
                if (buf == null || buf.capacity() < buffer.capacity()) {
                    buf = ByteBuffer.allocateDirect(buffer.capacity());
                    needResize = true;
                }
                buf.clear();
                buffer.rewind();
                buf.put(buffer);
                buf.flip();
                dirty = true;
            }
            @Override public boolean onCursorChange(CefBrowser b, int t) { return false; }
            @Override public boolean startDragging(CefBrowser b, CefDragData d, int m, int x, int y) { return false; }
            @Override public void updateDragCursor(CefBrowser b, int op) {}
        }
    }
}