package com.novax;

import org.lwjgl.glfw.*;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;
import java.nio.IntBuffer;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.nanovg.NanoVG.*;
import static org.lwjgl.nanovg.NanoVGGL3.*;
import static org.lwjgl.system.MemoryUtil.NULL;

/**
 * Undecorated GLFW window + NanoVG render loop + input dispatch.
 * Custom title-bar drag / min / max / close.
 */
public class Window implements AutoCloseable {
    private long handle, nvg;
    private int width = 1400, height = 900;
    private float scaleX = 1, scaleY = 1;
    private Widgets.Widget root;
    private Widgets.Widget focused;
    private boolean running = true, dragging, maximized;
    private double dragSX, dragSY;
    private int winSX, winSY;
    private int fps, frames;
    private double fpsTimer, lastTime;

    public void create(String title) {
        if (!glfwInit()) throw new IllegalStateException("GLFW init failed");
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        glfwWindowHint(GLFW_DECORATED, GLFW_FALSE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);
        glfwWindowHint(GLFW_SAMPLES, 4);

        handle = glfwCreateWindow(width, height, title, NULL, NULL);
        if (handle == NULL) throw new IllegalStateException("Window create failed");

        try (MemoryStack st = MemoryStack.stackPush()) {
            IntBuffer px = st.mallocInt(1), py = st.mallocInt(1);
            glfwGetWindowContentScale(handle, px, py);
            scaleX = px.get(0); scaleY = py.get(0);
        }
        GLFWVidMode vid = glfwGetVideoMode(glfwGetPrimaryMonitor());
        if (vid != null) glfwSetWindowPos(handle, (vid.width() - width) / 2, (vid.height() - height) / 2);

        glfwMakeContextCurrent(handle);
        glfwSwapInterval(1);
        GL.createCapabilities();

        nvg = nvgCreate(NVG_ANTIALIAS | NVG_STENCIL_STROKES);
        if (nvg == NULL) throw new IllegalStateException("NanoVG create failed");
        if (nvgCreateFont(nvg, "sans", "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf") == -1)
            if (nvgCreateFont(nvg, "sans", "C:/Windows/Fonts/segoeui.ttf") == -1)
                nvgCreateFont(nvg, "sans", "/System/Library/Fonts/SFNSText.ttf");
        nvgCreateFont(nvg, "sans-bold", "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf");

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        setupCallbacks();
        glfwShowWindow(handle);
        lastTime = glfwGetTime();
    }

    private void setupCallbacks() {
        glfwSetWindowSizeCallback(handle, (w, ww, hh) -> {
            width = ww; height = hh;
            if (root != null) { root.setBounds(0, 0, ww, hh); root.layout(); }
        });
        glfwSetCursorPosCallback(handle, (w, mx, my) -> {
            if (dragging) {
                glfwSetWindowPos(handle, winSX + (int)(mx - dragSX), winSY + (int)(my - dragSY));
                return;
            }
            if (root != null) root.dispatchMouse(new Events.Mouse(Events.Mouse.Type.MOVE, (float)mx, (float)my, -1, 0, 0));
        });
        glfwSetMouseButtonCallback(handle, (w, button, action, mods) -> {
            double[] mx = {0}, my = {0};
            glfwGetCursorPos(handle, mx, my);
            float x = (float)mx[0], y = (float)my[0];
            Theme t = Theme.get();
            if (y < t.titleBarH && button == GLFW_MOUSE_BUTTON_LEFT && action == GLFW_PRESS) {
                if (x > width - 46) { running = false; return; }
                if (x > width - 92) { toggleMax(); return; }
                if (x > width - 138) { glfwIconifyWindow(handle); return; }
                dragging = true; dragSX = x; dragSY = y;
                int[] wx = {0}, wy = {0};
                glfwGetWindowPos(handle, wx, wy);
                winSX = wx[0]; winSY = wy[0];
                return;
            }
            if (action == GLFW_RELEASE) dragging = false;
            if (root != null) {
                var type = action == GLFW_PRESS ? Events.Mouse.Type.DOWN : Events.Mouse.Type.UP;
                if (type == Events.Mouse.Type.DOWN) setFocus(root.findFocusable(x, y));
                root.dispatchMouse(new Events.Mouse(type, x, y, button, 0, mods));
            }
        });
        glfwSetScrollCallback(handle, (w, sx, sy) -> {
            double[] mx = {0}, my = {0};
            glfwGetCursorPos(handle, mx, my);
            if (root != null) root.dispatchMouse(new Events.Mouse(Events.Mouse.Type.SCROLL, (float)mx[0], (float)my[0], -1, (float)sy, 0));
        });
        glfwSetKeyCallback(handle, (w, key, scancode, action, mods) -> {
            var type = switch (action) {
                case GLFW_PRESS -> Events.Key.Type.PRESS;
                case GLFW_RELEASE -> Events.Key.Type.RELEASE;
                case GLFW_REPEAT -> Events.Key.Type.REPEAT;
                default -> Events.Key.Type.PRESS;
            };
            if (root != null) root.dispatchKey(new Events.Key(type, key, scancode, mods, 0));
        });
        glfwSetCharCallback(handle, (w, cp) -> {
            if (root != null) root.dispatchKey(new Events.Key(Events.Key.Type.CHAR, 0, 0, 0, cp));
        });
        glfwSetWindowCloseCallback(handle, w -> running = false);
    }

    private void toggleMax() {
        if (maximized) { glfwRestoreWindow(handle); maximized = false; }
        else { glfwMaximizeWindow(handle); maximized = true; }
    }

    public void setRoot(Widgets.Widget r) {
        root = r;
        root.setBounds(0, 0, width, height);
        root.layout();
    }

    public void setFocus(Widgets.Widget w) {
        if (focused != null) focused.focused = false;
        focused = w;
        if (focused != null) focused.focused = true;
    }

    public void run(Runnable perFrame) {
        while (running && !glfwWindowShouldClose(handle)) {
            double now = glfwGetTime();
            double dt = now - lastTime; lastTime = now;
            frames++; fpsTimer += dt;
            if (fpsTimer >= 1.0) { fps = frames; frames = 0; fpsTimer = 0; }

            glfwPollEvents();
            if (perFrame != null) perFrame.run();

            float[] bg = Theme.get().background;
            glViewport(0, 0, width, height);
            glClearColor(bg[0], bg[1], bg[2], 1f);
            glClear(GL_COLOR_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);

            float ratio = Math.max(scaleX, scaleY);
            nvgBeginFrame(nvg, width, height, ratio);
            if (root != null) root.render(nvg);
            nvgFontSize(nvg, 12);
            nvgFontFace(nvg, "sans");
            nvgTextAlign(nvg, NVG_ALIGN_LEFT | NVG_ALIGN_TOP);
            nvgFillColor(nvg, Widgets.rgba(Theme.get().textSecondary));
            nvgText(nvg, 8, height - 18, "NovaX  |  " + fps + " FPS");
            nvgEndFrame(nvg);

            glfwSwapBuffers(handle);
        }
    }

    public long handle() { return handle; }
    public long nvg() { return nvg; }
    public int width() { return width; }
    public int height() { return height; }
    public int fps() { return fps; }

    @Override public void close() {
        if (nvg != NULL) { nvgDelete(nvg); nvg = NULL; }
        if (handle != NULL) { glfwDestroyWindow(handle); handle = NULL; }
        glfwTerminate();
    }
}