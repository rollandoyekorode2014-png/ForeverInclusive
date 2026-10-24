package os;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.*;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class JavaOS extends Application {

    public static final UI.Theme THEME = new UI.Theme();
    private static JavaOS INSTANCE;

    private Stage primaryStage;
    private StackPane rootDesktop;          // holds wallpaper + windows
    private Pane windowLayer;               // all OSWindows live here
    private HBox taskbar;
    private HBox taskbarApps;
    private Label clockLabel;
    private VBox startMenu;
    private boolean startMenuOpen = false;

    private final Map<String, Button> taskbarButtons = new HashMap<>();

    @Override
    public void start(Stage stage) throws Exception {
        INSTANCE = this;
        primaryStage = stage;

        Path disk = Path.of(System.getProperty("user.home"), "JavaOS_Disk");
        Core.get().init(disk);
        Apps.initBuiltin();

        showSplashThenLogin();
    }

    private void showSplashThenLogin() {
        StackPane splash = new StackPane();
        splash.setStyle("-fx-background-color: #0a0a0a;");
        Label logo = new Label("JavaOS");
        logo.setFont(Font.font("Segoe UI", 48));
        logo.setTextFill(Color.web("#0078D4"));
        Label sub = new Label("Booting…");
        sub.setTextFill(Color.LIGHTGRAY);
        VBox box = new VBox(16, logo, sub);
        box.setAlignment(Pos.CENTER);
        splash.getChildren().add(box);

        Scene splashScene = new Scene(splash, 900, 600);
        primaryStage.setScene(splashScene);
        primaryStage.setTitle("JavaOS");
        primaryStage.show();

        // fake POST messages
        new Thread(() -> {
            String[] msgs = {
                    "Detecting CPU… OK",
                    "Initializing virtual filesystem… OK",
                    "Starting kernel services… OK",
                    "Loading window manager… OK",
                    "Ready."
            };
            for (String m : msgs) {
                try { Thread.sleep(350); } catch (InterruptedException ignored) {}
                Platform.runLater(() -> sub.setText(m));
            }
            try { Thread.sleep(400); } catch (InterruptedException ignored) {}
            Platform.runLater(this::showLogin);
        }).start();
    }

    private void showLogin() {
        VBox form = new VBox(14);
        form.setAlignment(Pos.CENTER);
        form.setMaxWidth(320);
        form.setPadding(new Insets(40));
        form.setStyle("-fx-background-color: #1e1e1e; -fx-background-radius: 12;");

        Label title = new Label("JavaOS");
        title.setFont(Font.font(32));
        title.setTextFill(Color.web("#0078D4"));

        TextField user = new TextField("guest");
        user.setPromptText("Username");
        PasswordField pass = new PasswordField();
        pass.setPromptText("Password");
        Label error = new Label();
        error.setTextFill(Color.SALMON);

        Button login = new Button("Sign in");
        login.setDefaultButton(true);
        login.setMaxWidth(Double.MAX_VALUE);
        login.setOnAction(e -> {
            if (Core.get().vfs.authenticate(user.getText().trim(), pass.getText())) {
                Core.get().currentUser = user.getText().trim();
                showDesktop();
            } else {
                error.setText("Invalid username or password");
            }
        });

        Button guest = new Button("Continue as Guest");
        guest.setMaxWidth(Double.MAX_VALUE);
        guest.setOnAction(e -> {
            Core.get().currentUser = "guest";
            showDesktop();
        });

        form.getChildren().addAll(title,
                new Label("Username"), user,
                new Label("Password"), pass,
                error, login, guest);

        StackPane root = new StackPane(form);
        root.setStyle("-fx-background-color: #0d0d0d;");
        Scene scene = new Scene(root, 1000, 650);
        primaryStage.setScene(scene);
        primaryStage.setMaximized(true);
    }

    private void showDesktop() {
        rootDesktop = new StackPane();
        rootDesktop.setStyle("-fx-background-color: linear-gradient(to bottom right, #0f2027, #203a43, #2c5364);");

        windowLayer = new Pane();
        windowLayer.setMouseTransparent(false);

        // desktop icons
        VBox icons = new VBox(18);
        icons.setPadding(new Insets(24));
        icons.setAlignment(Pos.TOP_LEFT);
        for (Apps.App app : Apps.all()) {
            Button iconBtn = createDesktopIcon(app);
            icons.getChildren().add(iconBtn);
        }

        // taskbar
        taskbar = new HBox(4);
        taskbar.getStyleClass().add("taskbar");
        taskbar.setPrefHeight(42);
        taskbar.setAlignment(Pos.CENTER_LEFT);
        taskbar.setPadding(new Insets(0, 8, 0, 8));

        Button startBtn = new Button("⊞ Start");
        startBtn.getStyleClass().add("start-btn");
        startBtn.setOnAction(e -> toggleStartMenu());

        taskbarApps = new HBox(4);
        taskbarApps.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(taskbarApps, Priority.ALWAYS);

        clockLabel = new Label();
        clockLabel.setTextFill(Color.WHITE);
        clockLabel.setPadding(new Insets(0, 10, 0, 10));
        updateClock();
        Core.get().bus.subscribe("clock.tick", o -> Platform.runLater(this::updateClock));

        taskbar.getChildren().addAll(startBtn, taskbarApps, clockLabel);

        // start menu (hidden)
        startMenu = buildStartMenu();
        startMenu.setVisible(false);
        startMenu.setManaged(false);

        BorderPane desktopLayout = new BorderPane();
        desktopLayout.setCenter(new StackPane(icons, windowLayer));
        desktopLayout.setBottom(taskbar);

        rootDesktop.getChildren().addAll(desktopLayout, startMenu);
        StackPane.setAlignment(startMenu, Pos.BOTTOM_LEFT);
        startMenu.setTranslateY(-42);

        Scene scene = new Scene(rootDesktop, 1200, 750);
        applyTheme();
        primaryStage.setScene(scene);
        primaryStage.setMaximized(true);

        // global shortcuts
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.D, KeyCombination.CONTROL_DOWN, KeyCombination.ALT_DOWN),
                () -> Core.get().processes.killAll());
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.T, KeyCombination.CONTROL_DOWN, KeyCombination.ALT_DOWN),
                () -> launch("terminal"));
    }

    private Button createDesktopIcon(Apps.App app) {
        VBox box = new VBox(4, UI.icon(app.icon(), 40), new Label(app.name()));
        box.setAlignment(Pos.CENTER);
        Button b = new Button();
        b.setGraphic(box);
        b.getStyleClass().add("desktop-icon");
        b.setOnAction(e -> launch(app.id()));
        return b;
    }

    private VBox buildStartMenu() {
        VBox menu = new VBox(4);
        menu.setStyle("-fx-background-color: #2d2d2d; -fx-padding: 12; -fx-background-radius: 8 8 0 0;");
        menu.setPrefWidth(260);
        menu.setMaxHeight(420);

        Label header = new Label("Applications");
        header.setTextFill(Color.WHITE);
        header.setFont(Font.font(14));
        menu.getChildren().add(header);

        for (Apps.App app : Apps.all()) {
            Button item = new Button(app.name(), UI.icon(app.icon(), 20));
            item.setMaxWidth(Double.MAX_VALUE);
            item.setAlignment(Pos.CENTER_LEFT);
            item.setStyle("-fx-background-color: transparent; -fx-text-fill: white; -fx-padding: 8;");
            item.setOnMouseEntered(e -> item.setStyle("-fx-background-color: #0078D455; -fx-text-fill: white; -fx-padding: 8;"));
            item.setOnMouseExited(e -> item.setStyle("-fx-background-color: transparent; -fx-text-fill: white; -fx-padding: 8;"));
            item.setOnAction(e -> { launch(app.id()); toggleStartMenu(); });
            menu.getChildren().add(item);
        }

        menu.getChildren().add(new Separator());
        Button logout = new Button("Log out");
        logout.setMaxWidth(Double.MAX_VALUE);
        logout.setOnAction(e -> {
            Core.get().processes.killAll();
            showLogin();
        });
        Button shutdown = new Button("Shut down");
        shutdown.setMaxWidth(Double.MAX_VALUE);
        shutdown.setOnAction(e -> {
            Core.get().shutdown();
            Platform.exit();
        });
        menu.getChildren().addAll(logout, shutdown);
        return menu;
    }

    private void toggleStartMenu() {
        startMenuOpen = !startMenuOpen;
        startMenu.setVisible(startMenuOpen);
        startMenu.setManaged(startMenuOpen);
    }

    public void launch(String appId) {
        Apps.App app = Apps.get(appId);
        if (app == null) return;

        Core.Process proc = Core.get().processes.create(app.name(), appId);
        UI.OSWindow win = app.createWindow();
        proc.setWindow(win);

        win.setOnClose(w -> {
            windowLayer.getChildren().remove(w);
            taskbarApps.getChildren().remove(taskbarButtons.remove(w.id));
            Core.get().processes.terminate(proc.pid, false);
        });

        // cascade position
        int offset = windowLayer.getChildren().size() * 28;
        win.setLayoutX(80 + offset);
        win.setLayoutY(60 + offset);

        windowLayer.getChildren().add(win);
        win.focus();

        // taskbar button
        Button tb = new Button(app.name());
        tb.getStyleClass().add("taskbar-app");
        tb.setOnAction(e -> {
            win.setVisible(true);
            win.focus();
        });
        taskbarButtons.put(win.id, tb);
        taskbarApps.getChildren().add(tb);
    }

    private void updateClock() {
        clockLabel.setText(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
    }

    public static void applyTheme() {
        if (INSTANCE == null || INSTANCE.primaryStage == null) return;
        Scene sc = INSTANCE.primaryStage.getScene();
        if (sc != null) {
            sc.getStylesheets().clear();
            sc.getStylesheets().add("data:text/css," + THEME.css().replace("\n", ""));
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}