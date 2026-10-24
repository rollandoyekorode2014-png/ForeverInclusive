package os;

import javafx.geometry.*;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

import java.util.function.Consumer;

public final class UI {

    public static final class Theme {
        public enum Mode { LIGHT, DARK }
        public Mode mode = Mode.DARK;
        public Color accent = Color.web("#0078D4");

        public String css() {
            String bg     = (mode == Mode.DARK) ? "#1e1e1e" : "#f0f0f0";
            String fg     = (mode == Mode.DARK) ? "#ffffff" : "#000000";
            String panel  = (mode == Mode.DARK) ? "#2d2d2d" : "#ffffff";
            String border = (mode == Mode.DARK) ? "#404040" : "#c0c0c0";
            String accentHex = String.format("#%02X%02X%02X",
                    (int)(accent.getRed()*255),
                    (int)(accent.getGreen()*255),
                    (int)(accent.getBlue()*255));

            StringBuilder sb = new StringBuilder();
            sb.append(".root { -fx-base: ").append(bg).append("; -fx-background: ").append(bg)
                    .append("; -fx-text-fill: ").append(fg).append("; }\n");
            sb.append(".os-window { -fx-background-color: ").append(panel)
                    .append("; -fx-border-color: ").append(border)
                    .append("; -fx-border-width: 1; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.45), 14, 0, 0, 4); }\n");
            sb.append(".os-titlebar { -fx-background-color: ").append(accentHex).append("; -fx-padding: 4 8 4 10; }\n");
            sb.append(".os-titlebar-label { -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 13; }\n");
            sb.append(".taskbar { -fx-background-color: ").append(panel)
                    .append("; -fx-border-color: ").append(border).append("; -fx-border-width: 1 0 0 0; }\n");
            sb.append(".start-btn { -fx-background-color: transparent; -fx-text-fill: ").append(fg)
                    .append("; -fx-font-size: 14; -fx-padding: 6 14; }\n");
            sb.append(".start-btn:hover { -fx-background-color: ").append(accentHex).append("44; }\n");
            sb.append(".desktop-icon { -fx-background-color: transparent; -fx-text-fill: white; -fx-font-size: 11; -fx-padding: 6; }\n");
            sb.append(".desktop-icon:hover { -fx-background-color: rgba(255,255,255,0.15); }\n");
            sb.append(".taskbar-app { -fx-background-color: transparent; -fx-text-fill: ").append(fg)
                    .append("; -fx-padding: 4 10; }\n");
            sb.append(".taskbar-app:hover, .taskbar-app.active { -fx-background-color: ").append(accentHex).append("55; }\n");
            return sb.toString();
        }
    }

    public static Node icon(String name, double size) {
        Canvas c = new Canvas(size, size);
        GraphicsContext g = c.getGraphicsContext2D();
        switch (name.toLowerCase()) {
            case "terminal" -> {
                g.setFill(Color.web("#0c0c0c"));
                g.fillRoundRect(2, 2, size-4, size-4, 4, 4);
                g.setFill(Color.web("#00ff66"));
                g.fillText(">_", 7, size*0.68);
            }
            case "notepad" -> {
                g.setFill(Color.web("#f4e8c1"));
                g.fillRect(5, 3, size-10, size-6);
                g.setStroke(Color.web("#8b7355"));
                g.strokeRect(5, 3, size-10, size-6);
                g.setStroke(Color.web("#444"));
                for (int i = 0; i < 4; i++) g.strokeLine(9, 9+i*5, size-9, 9+i*5);
            }
            case "calculator" -> {
                g.setFill(Color.web("#222"));
                g.fillRoundRect(3, 3, size-6, size-6, 5, 5);
                g.setFill(Color.web("#eee"));
                g.fillRect(7, 7, size-14, 9);
                g.setFill(Color.web("#666"));
                for (int r=0;r<3;r++) for (int col=0;col<3;col++)
                    g.fillRoundRect(7+col*9, 20+r*8, 7, 6, 1, 1);
            }
            case "settings" -> {
                g.setStroke(Color.web("#ddd"));
                g.setLineWidth(2.2);
                g.strokeOval(7, 7, size-14, size-14);
                g.strokeLine(size/2, 3, size/2, 9);
                g.strokeLine(size/2, size-9, size/2, size-3);
                g.strokeLine(3, size/2, 9, size/2);
                g.strokeLine(size-9, size/2, size-3, size/2);
            }
            case "folder" -> {
                g.setFill(Color.web("#e6b800"));
                g.fillRect(4, 11, size-8, size-15);
                g.fillPolygon(new double[]{4,4,15,20}, new double[]{11,6,6,11}, 4);
            }
            case "file" -> {
                g.setFill(Color.WHITE);
                g.fillRect(7, 4, size-14, size-8);
                g.setStroke(Color.GRAY);
                g.strokeRect(7, 4, size-14, size-8);
            }
            case "about" -> {
                g.setFill(Color.web("#0078D4"));
                g.fillOval(4, 4, size-8, size-8);
                g.setFill(Color.WHITE);
                g.fillText("i", size*0.42, size*0.68);
            }
            default -> {
                g.setFill(Color.web("#0078D4"));
                g.fillRoundRect(3, 3, size-6, size-6, 6, 6);
            }
        }
        return c;
    }

    public static class OSWindow extends BorderPane {
        public final String id;
        public final String title;
        private final Label titleLabel;
        private double dragOffsetX, dragOffsetY;
        private boolean maximized = false;
        private double restoreX, restoreY, restoreW, restoreH;
        private Consumer<OSWindow> onClose = w -> {};

        public OSWindow(String id, String title, Node content, double w, double h) {
            this.id = id;
            this.title = title;
            getStyleClass().add("os-window");
            setPrefSize(w, h);
            setMinSize(220, 140);

            titleLabel = new Label(title);
            titleLabel.getStyleClass().add("os-titlebar-label");
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            Button min = chrome("–", e -> setVisible(false));
            Button max = chrome("□", e -> toggleMaximize());
            Button close = chrome("×", e -> close());

            HBox bar = new HBox(6, titleLabel, spacer, min, max, close);
            bar.getStyleClass().add("os-titlebar");
            bar.setAlignment(Pos.CENTER_LEFT);
            bar.setMinHeight(30);

            bar.setOnMousePressed(e -> {
                if (e.isPrimaryButtonDown() && !maximized) {
                    dragOffsetX = e.getSceneX() - getLayoutX();
                    dragOffsetY = e.getSceneY() - getLayoutY();
                    toFront();
                }
            });
            bar.setOnMouseDragged(e -> {
                if (!maximized) {
                    setLayoutX(Math.max(0, e.getSceneX() - dragOffsetX));
                    setLayoutY(Math.max(0, e.getSceneY() - dragOffsetY));
                }
            });

            setTop(bar);
            setCenter(content);

            Rectangle grip = new Rectangle(14, 14, Color.TRANSPARENT);
            grip.setCursor(Cursor.SE_RESIZE);
            grip.setOnMouseDragged(e -> {
                if (!maximized) {
                    setPrefWidth(Math.max(220, e.getX() + 14));
                    setPrefHeight(Math.max(140, e.getY() + 14));
                }
            });
            StackPane bottomRight = new StackPane(grip);
            bottomRight.setAlignment(Pos.BOTTOM_RIGHT);
            if (content instanceof Pane p) {
                p.getChildren().add(bottomRight);
            } else {
                StackPane wrapper = new StackPane(content, bottomRight);
                setCenter(wrapper);
            }
        }

        private Button chrome(String txt, javafx.event.EventHandler<javafx.event.ActionEvent> h) {
            Button b = new Button(txt);
            b.setStyle("-fx-background-color:transparent; -fx-text-fill:white; -fx-font-size:13; -fx-padding:2 9;");
            b.setOnAction(h);
            b.setOnMouseEntered(e -> b.setStyle("-fx-background-color:rgba(255,255,255,0.18); -fx-text-fill:white; -fx-font-size:13; -fx-padding:2 9;"));
            b.setOnMouseExited(e -> b.setStyle("-fx-background-color:transparent; -fx-text-fill:white; -fx-font-size:13; -fx-padding:2 9;"));
            return b;
        }

        public void setOnClose(Consumer<OSWindow> c) { onClose = c; }
        public void close() { onClose.accept(this); }

        public void toggleMaximize() {
            Pane parent = (Pane) getParent();
            if (parent == null) return;
            if (!maximized) {
                restoreX = getLayoutX(); restoreY = getLayoutY();
                restoreW = getWidth(); restoreH = getHeight();
                setLayoutX(0); setLayoutY(0);
                setPrefSize(parent.getWidth(), parent.getHeight() - 42);
                maximized = true;
            } else {
                setLayoutX(restoreX); setLayoutY(restoreY);
                setPrefSize(restoreW, restoreH);
                maximized = false;
            }
        }

        public void focus() { toFront(); requestFocus(); }
    }
}