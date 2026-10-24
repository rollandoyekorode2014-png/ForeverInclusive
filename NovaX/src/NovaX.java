package com.novax;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * NovaX – gaming browser.
 * Custom LWJGL/NanoVG UI + JCEF off-screen Chromium.
 * Type a URL, press Enter, click and sign in.
 */
public class NovaX {
    private Window window;
    private CefBridge.Browser browser;
    private Widgets.TextField addressBar;
    private Widgets.Label titleLabel;
    private Widgets.Panel root;

    public static void main(String[] args) {
        new NovaX().start();
    }

    void start() {
        window = new Window();
        window.create("NovaX");
        buildUI();
        window.setRoot(root);

        browser.start("https://www.google.com");
        browser.onAddress = url -> {
            if (addressBar != null && !addressBar.focused) addressBar.setText(url);
        };
        browser.onTitle = title -> {
            if (titleLabel != null) titleLabel.setText(title != null ? title : "NovaX");
        };

        System.out.println("========================================");
        System.out.println("  NovaX ready");
        System.out.println("  Type a URL in the address bar + Enter");
        System.out.println("  Click the page and sign in normally");
        System.out.println("========================================");

        window.run(() -> {
            browser.layout();
            browser.drawGL();
        });

        browser.dispose();
        CefBridge.shutdown();
        window.close();
    }

    private void buildUI() {
        Theme t = Theme.get();
        float titleH = t.titleBarH, tabH = t.tabBarH, addrH = t.addrBarH;
        float chrome = titleH + tabH + addrH;
        int W = 1400, H = 900;

        root = new Widgets.Panel(t.background);
        root.setBounds(0, 0, W, H);

        Widgets.Panel titleBar = new Widgets.Panel(t.titleBar);
        titleBar.setBounds(0, 0, W, titleH);
        titleBar.radius = 0;
        root.add(titleBar);

        titleLabel = new Widgets.Label("NovaX");
        titleLabel.setBounds(14, 0, 500, titleH);
        titleLabel.bold = true;
        titleLabel.color = t.accent;
        titleBar.add(titleLabel);

        Widgets.Label minL = new Widgets.Label("—");
        minL.setBounds(W - 138, 0, 46, titleH);
        minL.align = org.lwjgl.nanovg.NanoVG.NVG_ALIGN_CENTER | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_MIDDLE;
        minL.color = t.textSecondary;
        titleBar.add(minL);

        Widgets.Label maxL = new Widgets.Label("□");
        maxL.setBounds(W - 92, 0, 46, titleH);
        maxL.align = org.lwjgl.nanovg.NanoVG.NVG_ALIGN_CENTER | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_MIDDLE;
        maxL.color = t.textSecondary;
        titleBar.add(maxL);

        Widgets.Label closeL = new Widgets.Label("✕");
        closeL.setBounds(W - 46, 0, 46, titleH);
        closeL.align = org.lwjgl.nanovg.NanoVG.NVG_ALIGN_CENTER | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_MIDDLE;
        closeL.color = t.danger;
        titleBar.add(closeL);

        Widgets.Panel tabBar = new Widgets.Panel(t.surface);
        tabBar.setBounds(0, titleH, W, tabH);
        tabBar.radius = 0;
        root.add(tabBar);

        Widgets.Panel tab = new Widgets.Panel(t.tabActive);
        tab.setBounds(8, 4, 160, tabH - 8);
        tabBar.add(tab);

        Widgets.Label tabLbl = new Widgets.Label("New Tab");
        tabLbl.setBounds(16, 4, 130, tabH - 8);
        tab.add(tabLbl);

        Widgets.Button newTab = new Widgets.Button("+");
        newTab.setBounds(180, 4, 28, tabH - 8);
        newTab.setOnClick(b -> System.out.println("[NovaX] New tab (scaffold)"));
        tabBar.add(newTab);

        Widgets.Panel nav = new Widgets.Panel(t.surface);
        nav.setBounds(0, titleH + tabH, W, addrH);
        nav.radius = 0;
        root.add(nav);

        Widgets.Button back = new Widgets.Button("◀");
        back.setBounds(8, 6, 32, addrH - 12);
        back.setOnClick(b -> browser.goBack());
        nav.add(back);

        Widgets.Button fwd = new Widgets.Button("▶");
        fwd.setBounds(44, 6, 32, addrH - 12);
        fwd.setOnClick(b -> browser.goForward());
        nav.add(fwd);

        Widgets.Button reload = new Widgets.Button("⟳");
        reload.setBounds(80, 6, 32, addrH - 12);
        reload.setOnClick(b -> browser.reload());
        nav.add(reload);

        addressBar = new Widgets.TextField("https://www.google.com");
        addressBar.setBounds(120, 6, W - 200, addrH - 12);
        addressBar.placeholder = "Search or enter address";
        addressBar.onSubmit = this::navigate;
        nav.add(addressBar);

        Widgets.Button go = new Widgets.Button("Go");
        go.setBounds(W - 72, 6, 60, addrH - 12);
        go.bgColor = t.accent;
        go.setOnClick(b -> navigate(addressBar.getText()));
        nav.add(go);

        browser = new CefBridge.Browser();
        browser.setBounds(0, chrome, W, H - chrome);
        root.add(browser);

        root.layout();
    }

    private void navigate(String raw) {
        if (raw == null || raw.isBlank()) return;
        String url = raw.trim();
        if (!url.contains("://") && !url.startsWith("about:")) {
            if (url.contains(".") && !url.contains(" ")) url = "https://" + url;
            else url = "https://www.google.com/search?q=" + URLEncoder.encode(url, StandardCharsets.UTF_8);
        }
        System.out.println("[NovaX] → " + url);
        browser.loadURL(url);
        addressBar.setText(url);
    }
}