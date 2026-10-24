package com.videoeditor;

import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.javacv.*;
import org.bytedeco.javacv.Frame;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import java.util.function.BiConsumer;

public class Exporter {

    private final List<Main.Clip> clips;
    private final File output;
    private final String resolution;
    private final String quality;
    private final List<Main.Marker> markers;
    private volatile boolean cancelled;
    private BiConsumer<Double, String> progressCb;

    public Exporter(List<Main.Clip> clips, File output, String resolution, String quality, List<Main.Marker> markers) {
        this.clips = clips;
        this.output = output;
        this.resolution = resolution;
        this.quality = quality;
        this.markers = markers;
    }

    public void setProgressCallback(BiConsumer<Double, String> cb) {
        this.progressCb = cb;
    }

    public void cancel() {
        cancelled = true;
    }

    public void export() throws Exception {
        int w = 1920, h = 1080;
        switch (resolution) {
            case "720p" -> { w = 1280; h = 720; }
            case "4K" -> { w = 3840; h = 2160; }
        }
        int bitrate = switch (quality) {
            case "fast" -> 4_000_000;
            case "high" -> 20_000_000;
            default -> 10_000_000;
        };

        double totalDur = 0;
        for (Main.Clip c : clips) totalDur = Math.max(totalDur, c.startOnTimeline + c.duration());
        if (totalDur < 0.1) throw new Exception("Timeline is empty");

        FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(output, w, h);
        recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
        recorder.setFormat("mp4");
        recorder.setFrameRate(30);
        recorder.setVideoBitrate(bitrate);
        recorder.setAudioChannels(2);
        recorder.setAudioCodec(avcodec.AV_CODEC_ID_AAC);
        recorder.setSampleRate(44100);
        recorder.setAudioBitrate(192_000);
        recorder.start();

        Java2DFrameConverter conv = new Java2DFrameConverter();
        long startNs = System.nanoTime();

        // Simple sequential export: for each frame time, find active clips and composite
        // (simplified – real editors use more sophisticated frame generation)
        for (double t = 0; t < totalDur; t += 1.0 / 30) {
            if (cancelled) {
                recorder.stop();
                recorder.release();
                output.delete();
                throw new Exception("Cancelled");
            }

            BufferedImage frame = new BufferedImage(w, h, BufferedImage.TYPE_3BYTE_BGR);
            Graphics2D g = frame.createGraphics();
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, w, h);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Find video/text clips active at t
            for (Main.Clip c : clips) {
                if (c.track >= 2 && !c.isText) continue; // skip pure audio for visual
                double cStart = c.startOnTimeline;
                double cEnd = cStart + c.duration();
                if (t < cStart || t >= cEnd) continue;

                double srcTime = c.inPoint + (t - cStart) * c.speed;

                if (c.isText) {
                    g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) c.opacity));
                    g.setFont(new Font(c.fontFamily, Font.BOLD, c.fontSize));
                    g.setColor(new Color(
                            (float) c.textColor.getRed(),
                            (float) c.textColor.getGreen(),
                            (float) c.textColor.getBlue()));
                    FontMetrics fm = g.getFontMetrics();
                    int tx = (w - fm.stringWidth(c.text)) / 2;
                    int ty = h - 80;
                    g.drawString(c.text, tx, ty);
                } else if (c.item != null && (c.item.isVideo || c.item.isImage)) {
                    try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(c.item.file)) {
                        grabber.start();
                        grabber.setTimestamp((long) (srcTime * 1_000_000));
                        Frame f = grabber.grabImage();
                        if (f != null) {
                            BufferedImage img = conv.convert(f);
                            if (img != null) {
                                // apply simple filters
                                img = applyFilters(img, c);
                                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) c.opacity));
                                g.drawImage(img, 0, 0, w, h, null);
                            }
                        }
                        grabber.stop();
                    } catch (Exception ignored) {}
                }
            }
            g.dispose();

            Frame outFrame = conv.convert(frame);
            recorder.record(outFrame);

            double progress = t / totalDur;
            long elapsed = (System.nanoTime() - startNs) / 1_000_000;
            long etaMs = progress > 0.01 ? (long) (elapsed / progress - elapsed) : 0;
            String eta = String.format("%d:%02d", etaMs / 60000, (etaMs / 1000) % 60);
            if (progressCb != null) progressCb.accept(progress, eta);
        }

        recorder.stop();
        recorder.release();
    }

    private BufferedImage applyFilters(BufferedImage src, Main.Clip c) {
        // Very lightweight filter approximation (real would use ColorConvertOp / LookupOp)
        if (c.brightness == 0 && c.contrast == 1 && c.saturation == 1 && !c.blackWhite)
            return src;
        BufferedImage dst = new BufferedImage(src.getWidth(), src.getHeight(), src.getType());
        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                int rgb = src.getRGB(x, y);
                int r = (rgb >> 16) & 0xff;
                int g = (rgb >> 8) & 0xff;
                int b = rgb & 0xff;
                // brightness / contrast
                r = clamp((int) ((r - 128) * c.contrast + 128 + c.brightness * 255));
                g = clamp((int) ((g - 128) * c.contrast + 128 + c.brightness * 255));
                b = clamp((int) ((b - 128) * c.contrast + 128 + c.brightness * 255));
                if (c.blackWhite) {
                    int gray = (r + g + b) / 3;
                    r = g = b = gray;
                }
                // saturation approx
                if (c.saturation != 1) {
                    float gray = (r + g + b) / 3f;
                    r = clamp((int) (gray + (r - gray) * c.saturation));
                    g = clamp((int) (gray + (g - gray) * c.saturation));
                    b = clamp((int) (gray + (b - gray) * c.saturation));
                }
                dst.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }
        return dst;
    }

    private int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }
}