package com.videoeditor;

import javafx.geometry.Insets;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

import java.util.ArrayList;
import java.util.List;

public class Timeline extends VBox {

    private final Main app;
    private final Canvas canvas;
    private final ScrollPane scroll;
    private final List<Main.Clip> clips = new ArrayList<>();
    private double zoom = 50; // pixels per second
    private double playhead = 0;
    private Main.Clip selected;
    private double dragStartX, dragOrigStart;
    private boolean draggingEdge;
    private boolean leftEdge;
    private static final int TRACK_H = 48;
    private static final int TRACK_COUNT = 5;
    private static final String[] TRACK_NAMES = {"V1", "V2", "A1", "A2", "T"};

    public Timeline(Main app) {
        this.app = app;
        canvas = new Canvas(2000, TRACK_COUNT * TRACK_H + 30);
        scroll = new ScrollPane(canvas);
        scroll.setFitToHeight(true);
        scroll.setPannable(true);
        getChildren().add(scroll);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        canvas.setOnMousePressed(this::onPress);
        canvas.setOnMouseDragged(this::onDrag);
        canvas.setOnMouseReleased(this::onRelease);
        canvas.setOnScroll(e -> {
            if (e.isControlDown()) {
                setZoom(zoom * (e.getDeltaY() > 0 ? 1.1 : 0.9));
                e.consume();
            }
        });

        redraw();
    }

    public void redraw() {
        double totalDur = Math.max(60, getTotalDuration() + 10);
        canvas.setWidth(Math.max(800, totalDur * zoom + 100));
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.setFill(Color.web("#1e1e1e"));
        g.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());

        // tracks
        for (int i = 0; i < TRACK_COUNT; i++) {
            g.setFill(Color.web(i % 2 == 0 ? "#252525" : "#2a2a2a"));
            g.fillRect(0, 20 + i * TRACK_H, canvas.getWidth(), TRACK_H);
            g.setFill(Color.web("#666"));
            g.setFont(Font.font(11));
            g.fillText(TRACK_NAMES[i], 4, 20 + i * TRACK_H + 16);
        }

        // time ruler
        g.setStroke(Color.web("#444"));
        g.strokeLine(0, 20, canvas.getWidth(), 20);
        g.setFill(Color.web("#aaa"));
        for (double t = 0; t < totalDur; t += 5) {
            double x = t * zoom;
            g.strokeLine(x, 10, x, 20);
            if (t % 10 == 0) g.fillText(Main.formatTime(t), x + 2, 12);
        }

        // clips
        for (Main.Clip c : clips) {
            double x = c.startOnTimeline * zoom;
            double w = c.duration() * zoom;
            double y = 20 + c.track * TRACK_H + 4;
            Color col = c.isText ? Color.web("#8f6a3a") :
                    (c.track >= 2 ? Color.web("#5a8f5a") : Color.web("#3a7ca5"));
            if (c == selected) {
                g.setStroke(Color.WHITE);
                g.setLineWidth(2);
                g.strokeRoundRect(x, y, w, TRACK_H - 8, 4, 4);
            }
            g.setFill(col);
            g.fillRoundRect(x, y, w, TRACK_H - 8, 4, 4);
            g.setFill(Color.WHITE);
            g.setFont(Font.font(11));
            String label = c.isText ? c.text : c.item.file.getName();
            if (label.length() > 20) label = label.substring(0, 18) + "…";
            g.fillText(label, x + 4, y + 18);
        }

        // markers
        g.setStroke(Color.ORANGE);
        for (Main.Marker m : app.getMarkers()) {
            double x = m.time * zoom;
            g.strokeLine(x, 0, x, canvas.getHeight());
            g.setFill(Color.ORANGE);
            g.fillText(m.name, x + 2, 18);
        }

        // playhead
        double px = playhead * zoom;
        g.setStroke(Color.web("#ff4444"));
        g.setLineWidth(2);
        g.strokeLine(px, 0, px, canvas.getHeight());
    }

    private void onPress(MouseEvent e) {
        double x = e.getX();
        double y = e.getY() - 20;
        playhead = Math.max(0, x / zoom);
        selected = null;
        for (Main.Clip c : clips) {
            double cx = c.startOnTimeline * zoom;
            double cw = c.duration() * zoom;
            double cy = c.track * TRACK_H;
            if (x >= cx && x <= cx + cw && y >= cy && y <= cy + TRACK_H) {
                selected = c;
                app.selectClip(c);
                dragStartX = x;
                dragOrigStart = c.startOnTimeline;
                // edge?
                if (x - cx < 8) { draggingEdge = true; leftEdge = true; }
                else if (cx + cw - x < 8) { draggingEdge = true; leftEdge = false; }
                else draggingEdge = false;
                break;
            }
        }
        redraw();
    }

    private void onDrag(MouseEvent e) {
        if (selected == null) {
            playhead = Math.max(0, e.getX() / zoom);
            redraw();
            return;
        }
        double dx = (e.getX() - dragStartX) / zoom;
        if (draggingEdge) {
            if (leftEdge) {
                double newIn = selected.inPoint + dx * selected.speed;
                if (newIn >= 0 && newIn < selected.outPoint - 0.1) {
                    selected.inPoint = newIn;
                    selected.startOnTimeline += dx;
                    dragStartX = e.getX();
                }
            } else {
                double newOut = selected.outPoint + dx * selected.speed;
                if (newOut > selected.inPoint + 0.1 && newOut <= selected.item.duration) {
                    selected.outPoint = newOut;
                }
            }
        } else {
            selected.startOnTimeline = Math.max(0, dragOrigStart + dx);
            // simple snap
            snap(selected);
        }
        redraw();
    }

    private void onRelease(MouseEvent e) {
        draggingEdge = false;
    }

    private void snap(Main.Clip c) {
        double thresh = 0.15; // seconds
        // to playhead
        if (Math.abs(c.startOnTimeline - playhead) < thresh) c.startOnTimeline = playhead;
        // to other edges
        for (Main.Clip o : clips) {
            if (o == c) continue;
            if (Math.abs(c.startOnTimeline - o.startOnTimeline) < thresh) c.startOnTimeline = o.startOnTimeline;
            if (Math.abs(c.startOnTimeline - (o.startOnTimeline + o.duration())) < thresh)
                c.startOnTimeline = o.startOnTimeline + o.duration();
        }
    }

    public void addClip(Main.MediaItem item, double at) {
        Main.Clip c = new Main.Clip();
        c.item = item;
        c.startOnTimeline = at;
        c.inPoint = 0;
        c.outPoint = item.duration;
        c.track = item.isAudio ? 2 : (item.isImage || item.isVideo ? 0 : 4);
        c.isText = false;
        clips.add(c);
        selected = c;
        app.selectClip(c);
        redraw();
    }

    public void addTextClip(double at, double dur) {
        Main.Clip c = new Main.Clip();
        c.isText = true;
        c.startOnTimeline = at;
        c.inPoint = 0;
        c.outPoint = dur;
        c.track = 4;
        c.text = "Text";
        clips.add(c);
        selected = c;
        app.selectClip(c);
        redraw();
    }

    public void splitAtPlayhead() {
        if (selected == null) return;
        double rel = (playhead - selected.startOnTimeline) * selected.speed + selected.inPoint;
        if (rel <= selected.inPoint + 0.05 || rel >= selected.outPoint - 0.05) return;
        Main.Clip right = new Main.Clip();
        // copy
        right.item = selected.item;
        right.startOnTimeline = playhead;
        right.inPoint = rel;
        right.outPoint = selected.outPoint;
        right.track = selected.track;
        right.volume = selected.volume;
        right.speed = selected.speed;
        right.opacity = selected.opacity;
        right.isText = selected.isText;
        right.text = selected.text;
        selected.outPoint = rel;
        clips.add(right);
        redraw();
    }

    public void deleteSelected(boolean ripple) {
        if (selected == null) return;
        double end = selected.startOnTimeline + selected.duration();
        double start = selected.startOnTimeline;
        clips.remove(selected);
        if (ripple) {
            for (Main.Clip c : clips) {
                if (c.startOnTimeline >= end - 0.01) {
                    c.startOnTimeline -= (end - start);
                }
            }
        }
        selected = null;
        redraw();
    }

    public void stepFrame(int dir) {
        playhead = Math.max(0, playhead + dir / 30.0);
        redraw();
    }

    public void setZoom(double z) {
        zoom = Math.max(5, Math.min(200, z));
        redraw();
    }

    public double getZoom() { return zoom; }

    public void zoomToFit() {
        double total = getTotalDuration();
        if (total < 1) total = 60;
        zoom = (scroll.getWidth() - 40) / total;
        redraw();
    }

    public double getPlayheadTime() { return playhead; }
    public List<Main.Clip> getClips() { return clips; }

    public double getTotalDuration() {
        double max = 0;
        for (Main.Clip c : clips) max = Math.max(max, c.startOnTimeline + c.duration());
        return max;
    }

    public Main.ProjectState getProjectState() {
        Main.ProjectState s = new Main.ProjectState();
        s.clips = new ArrayList<>(clips);
        s.markers = new ArrayList<>(app.getMarkers());
        s.playhead = playhead;
        return s;
    }

    public void loadProjectState(Main.ProjectState s) {
        clips.clear();
        clips.addAll(s.clips);
        app.getMarkers().clear();
        app.getMarkers().addAll(s.markers);
        playhead = s.playhead;
        redraw();
    }
}