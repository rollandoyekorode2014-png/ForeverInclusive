package os;

import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Supplier;

public final class Apps {

    public interface App {
        String id();
        String name();
        String icon();
        UI.OSWindow createWindow();
    }

    private static final Map<String, App> REGISTRY = new LinkedHashMap<>();

    public static void register(App app) {
        REGISTRY.put(app.id(), app);
    }

    public static Collection<App> all() { return REGISTRY.values(); }
    public static App get(String id) { return REGISTRY.get(id); }

    public static void initBuiltin() {
        register(new TerminalApp());
        register(new NotepadApp());
        register(new CalculatorApp());
        register(new FileManagerApp());
        register(new SettingsApp());
        register(new AboutApp());
    }

    /* ===================== Terminal ===================== */
    static class TerminalApp implements App {
        public String id() { return "terminal"; }
        public String name() { return "Terminal"; }
        public String icon() { return "terminal"; }

        public UI.OSWindow createWindow() {
            TextArea output = new TextArea();
            output.setEditable(false);
            output.setFont(Font.font("Monospaced", 13));
            output.setStyle("-fx-control-inner-background: #0c0c0c; -fx-text-fill: #00ff66;");
            output.setText("JavaOS Terminal v1.0\nType 'help' for commands.\n\n");

            TextField input = new TextField();
            input.setFont(Font.font("Monospaced", 13));
            input.setStyle("-fx-control-inner-background: #0c0c0c; -fx-text-fill: #00ff66;");
            input.setPromptText("command...");

            String[] cwd = {"/home/" + Core.get().currentUser};

            input.setOnKeyPressed(e -> {
                if (e.getCode() == KeyCode.ENTER) {
                    String line = input.getText().trim();
                    input.clear();
                    output.appendText(cwd[0] + " $ " + line + "\n");
                    String result = execute(line, cwd);
                    if (!result.isEmpty()) output.appendText(result + "\n");
                    output.appendText("\n");
                    output.setScrollTop(Double.MAX_VALUE);
                }
            });

            VBox root = new VBox(output, input);
            VBox.setVgrow(output, Priority.ALWAYS);
            UI.OSWindow win = new UI.OSWindow("term-" + System.nanoTime(), "Terminal", root, 640, 420);
            return win;
        }

        private String execute(String line, String[] cwd) {
            if (line.isEmpty()) return "";
            String[] parts = line.split("\\s+");
            String cmd = parts[0].toLowerCase();
            Core.VFS vfs = Core.get().vfs;
            try {
                return switch (cmd) {
                    case "help" -> """
                        help, clear, pwd, ls, cd, cat, echo, mkdir, rm, whoami, date, ps, free, df, exit
                        """;
                    case "clear" -> { /* handled by caller if needed */ ""; }
                    case "pwd" -> cwd[0];
                    case "whoami" -> Core.get().currentUser;
                    case "date" -> LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                    case "ls" -> {
                        String path = parts.length > 1 ? resolve(cwd[0], parts[1]) : cwd[0];
                        List<String> files = vfs.list(path);
                        yield String.join("  ", files);
                    }
                    case "cd" -> {
                        if (parts.length < 2) { cwd[0] = "/home/" + Core.get().currentUser; yield ""; }
                        String np = resolve(cwd[0], parts[1]);
                        if (vfs.exists(np)) { cwd[0] = np; yield ""; }
                        yield "cd: no such directory: " + parts[1];
                    }
                    case "cat" -> {
                        if (parts.length < 2) yield "cat: missing operand";
                        yield vfs.read(resolve(cwd[0], parts[1]));
                    }
                    case "echo" -> line.length() > 5 ? line.substring(5) : "";
                    case "mkdir" -> {
                        if (parts.length < 2) yield "mkdir: missing operand";
                        vfs.mkdir(resolve(cwd[0], parts[1]));
                        yield "";
                    }
                    case "rm" -> {
                        if (parts.length < 2) yield "rm: missing operand";
                        vfs.trash(resolve(cwd[0], parts[1]));
                        yield "";
                    }
                    case "ps" -> {
                        StringBuilder sb = new StringBuilder("PID   STATE      NAME\n");
                        for (Core.Process p : Core.get().processes.all()) {
                            sb.append(String.format("%-5d %-10s %s%n", p.pid, p.getState(), p.name));
                        }
                        yield sb.toString();
                    }
                    case "free" -> {
                        Core.Metrics m = Core.get().metrics;
                        yield String.format("Heap used: %.1f MB / %.1f MB",
                                m.usedHeap()/1e6, m.maxHeap()/1e6);
                    }
                    case "df" -> "Filesystem  Size  Used\nJavaOS_Disk  ∞     (virtual)";
                    case "exit" -> { /* window close handled externally */ "Use the window close button."; }
                    default -> "command not found: " + cmd;
                };
            } catch (Exception ex) {
                return "Error: " + ex.getMessage();
            }
        }

        private String resolve(String cwd, String path) {
            if (path.startsWith("/")) return path;
            if (cwd.endsWith("/")) return cwd + path;
            return cwd + "/" + path;
        }
    }

    /* ===================== Notepad ===================== */
    static class NotepadApp implements App {
        public String id() { return "notepad"; }
        public String name() { return "Notepad"; }
        public String icon() { return "notepad"; }

        public UI.OSWindow createWindow() {
            TextArea area = new TextArea();
            area.setFont(Font.font("Consolas", 14));
            area.setWrapText(true);

            Label status = new Label("Ready");
            status.setPadding(new Insets(4, 8, 4, 8));

            String[] currentPath = {null};

            MenuBar menu = new MenuBar();
            Menu file = new Menu("File");
            MenuItem neu = new MenuItem("New");
            MenuItem open = new MenuItem("Open…");
            MenuItem save = new MenuItem("Save");
            MenuItem saveAs = new MenuItem("Save As…");
            file.getItems().addAll(neu, open, save, saveAs);
            menu.getMenus().add(file);

            neu.setOnAction(e -> { area.clear(); currentPath[0] = null; status.setText("New file"); });
            open.setOnAction(e -> {
                TextInputDialog d = new TextInputDialog("/home/" + Core.get().currentUser + "/Documents/");
                d.setHeaderText("Open file (virtual path)");
                d.showAndWait().ifPresent(path -> {
                    try {
                        area.setText(Core.get().vfs.read(path));
                        currentPath[0] = path;
                        status.setText("Opened " + path);
                    } catch (Exception ex) {
                        status.setText("Error: " + ex.getMessage());
                    }
                });
            });
            save.setOnAction(e -> {
                if (currentPath[0] == null) {
                    saveAs.fire();
                    return;
                }
                try {
                    Core.get().vfs.write(currentPath[0], area.getText());
                    status.setText("Saved " + currentPath[0]);
                } catch (Exception ex) {
                    status.setText("Error: " + ex.getMessage());
                }
            });
            saveAs.setOnAction(e -> {
                TextInputDialog d = new TextInputDialog("/home/" + Core.get().currentUser + "/Documents/untitled.txt");
                d.setHeaderText("Save As (virtual path)");
                d.showAndWait().ifPresent(path -> {
                    try {
                        Core.get().vfs.write(path, area.getText());
                        currentPath[0] = path;
                        status.setText("Saved " + path);
                    } catch (Exception ex) {
                        status.setText("Error: " + ex.getMessage());
                    }
                });
            });

            BorderPane root = new BorderPane();
            root.setTop(menu);
            root.setCenter(area);
            root.setBottom(status);

            return new UI.OSWindow("note-" + System.nanoTime(), "Notepad", root, 620, 480);
        }
    }

    /* ===================== Calculator ===================== */
    static class CalculatorApp implements App {
        public String id() { return "calculator"; }
        public String name() { return "Calculator"; }
        public String icon() { return "calculator"; }

        public UI.OSWindow createWindow() {
            TextField display = new TextField("0");
            display.setEditable(false);
            display.setAlignment(Pos.CENTER_RIGHT);
            display.setFont(Font.font(22));
            display.setStyle("-fx-background-color: #111; -fx-text-fill: #0f0;");

            StringBuilder expr = new StringBuilder();

            GridPane grid = new GridPane();
            grid.setHgap(6); grid.setVgap(6); grid.setPadding(new Insets(8));

            String[][] keys = {
                    {"C", "±", "%", "÷"},
                    {"7", "8", "9", "×"},
                    {"4", "5", "6", "−"},
                    {"1", "2", "3", "+"},
                    {"0", ".", "=", ""}
            };

            for (int r = 0; r < keys.length; r++) {
                for (int c = 0; c < keys[r].length; c++) {
                    String k = keys[r][c];
                    if (k.isEmpty()) continue;
                    Button b = new Button(k);
                    b.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
                    b.setPrefSize(64, 48);
                    GridPane.setHgrow(b, Priority.ALWAYS);
                    GridPane.setVgrow(b, Priority.ALWAYS);
                    final String key = k;
                    b.setOnAction(e -> {
                        switch (key) {
                            case "C" -> { expr.setLength(0); display.setText("0"); }
                            case "=" -> {
                                try {
                                    double val = eval(expr.toString());
                                    display.setText(Double.toString(val));
                                    expr.setLength(0);
                                    expr.append(val);
                                } catch (Exception ex) {
                                    display.setText("Error");
                                    expr.setLength(0);
                                }
                            }
                            case "±" -> {
                                if (expr.length() > 0 && expr.charAt(0) == '-') expr.deleteCharAt(0);
                                else expr.insert(0, '-');
                                display.setText(expr.toString());
                            }
                            default -> {
                                if (key.equals("×")) expr.append('*');
                                else if (key.equals("÷")) expr.append('/');
                                else if (key.equals("−")) expr.append('-');
                                else expr.append(key);
                                display.setText(expr.toString());
                            }
                        }
                    });
                    grid.add(b, c, r);
                }
            }

            VBox root = new VBox(8, display, grid);
            root.setPadding(new Insets(10));
            return new UI.OSWindow("calc-" + System.nanoTime(), "Calculator", root, 300, 380);
        }

        private double eval(String s) {
            // very simple left-to-right evaluator for +, -, *, /
            s = s.replaceAll("\\s+", "");
            java.util.List<Double> nums = new ArrayList<>();
            java.util.List<Character> ops = new ArrayList<>();
            StringBuilder cur = new StringBuilder();
            for (char ch : s.toCharArray()) {
                if (ch=='+'||ch=='-'||ch=='*'||ch=='/') {
                    if (cur.length() > 0) { nums.add(Double.parseDouble(cur.toString())); cur.setLength(0); }
                    ops.add(ch);
                } else cur.append(ch);
            }
            if (cur.length() > 0) nums.add(Double.parseDouble(cur.toString()));
            double res = nums.get(0);
            for (int i = 0; i < ops.size(); i++) {
                char op = ops.get(i);
                double n = nums.get(i+1);
                res = switch (op) {
                    case '+' -> res + n;
                    case '-' -> res - n;
                    case '*' -> res * n;
                    case '/' -> res / n;
                    default -> res;
                };
            }
            return res;
        }
    }

    /* ===================== File Manager ===================== */
    static class FileManagerApp implements App {
        public String id() { return "filemanager"; }
        public String name() { return "File Manager"; }
        public String icon() { return "folder"; }

        public UI.OSWindow createWindow() {
            ListView<String> list = new ListView<>();
            Label pathLabel = new Label("/");
            pathLabel.setPadding(new Insets(6));

            String[] cwd = {"/home/" + Core.get().currentUser};

            Runnable refresh = () -> {
                try {
                    pathLabel.setText(cwd[0]);
                    list.getItems().setAll(Core.get().vfs.list(cwd[0]));
                } catch (Exception e) {
                    list.getItems().setAll("(error: " + e.getMessage() + ")");
                }
            };
            refresh.run();

            list.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2) {
                    String sel = list.getSelectionModel().getSelectedItem();
                    if (sel == null) return;
                    String next = cwd[0].endsWith("/") ? cwd[0] + sel : cwd[0] + "/" + sel;
                    try {
                        if (Core.get().vfs.exists(next) &&
                                java.nio.file.Files.isDirectory(Core.get().vfs.resolve(next))) {
                            cwd[0] = next;
                            refresh.run();
                        }
                    } catch (Exception ignored) {}
                }
            });

            Button up = new Button("↑ Up");
            up.setOnAction(e -> {
                if (cwd[0].equals("/")) return;
                int idx = cwd[0].lastIndexOf('/');
                cwd[0] = idx <= 0 ? "/" : cwd[0].substring(0, idx);
                refresh.run();
            });

            Button refreshBtn = new Button("Refresh");
            refreshBtn.setOnAction(e -> refresh.run());

            HBox toolbar = new HBox(8, up, refreshBtn);
            toolbar.setPadding(new Insets(6));
            toolbar.setAlignment(Pos.CENTER_LEFT);

            BorderPane root = new BorderPane();
            root.setTop(new VBox(pathLabel, toolbar));
            root.setCenter(list);

            return new UI.OSWindow("fm-" + System.nanoTime(), "File Manager", root, 520, 400);
        }
    }

    /* ===================== Settings ===================== */
    static class SettingsApp implements App {
        public String id() { return "settings"; }
        public String name() { return "Settings"; }
        public String icon() { return "settings"; }

        public UI.OSWindow createWindow() {
            UI.Theme theme = JavaOS.THEME;

            ToggleGroup tg = new ToggleGroup();
            RadioButton dark = new RadioButton("Dark");
            RadioButton light = new RadioButton("Light");
            dark.setToggleGroup(tg); light.setToggleGroup(tg);
            dark.setSelected(theme.mode == UI.Theme.Mode.DARK);
            light.setSelected(theme.mode == UI.Theme.Mode.LIGHT);

            dark.setOnAction(e -> { theme.mode = UI.Theme.Mode.DARK; JavaOS.applyTheme(); });
            light.setOnAction(e -> { theme.mode = UI.Theme.Mode.LIGHT; JavaOS.applyTheme(); });

            ColorPicker accent = new ColorPicker(theme.accent);
            accent.setOnAction(e -> { theme.accent = accent.getValue(); JavaOS.applyTheme(); });

            Label user = new Label("Current user: " + Core.get().currentUser);
            Label jvm = new Label("Java: " + System.getProperty("java.version") +
                    " | FX: " + System.getProperty("javafx.version", "?"));

            VBox root = new VBox(14,
                    new Label("Personalization"),
                    new HBox(12, dark, light),
                    new Label("Accent color"),
                    accent,
                    new Separator(),
                    user, jvm);
            root.setPadding(new Insets(16));

            return new UI.OSWindow("settings-" + System.nanoTime(), "Settings", root, 420, 340);
        }
    }

    /* ===================== About ===================== */
    static class AboutApp implements App {
        public String id() { return "about"; }
        public String name() { return "About JavaOS"; }
        public String icon() { return "about"; }

        public UI.OSWindow createWindow() {
            Label title = new Label("JavaOS");
            title.setFont(Font.font(28));
            Label ver = new Label("Version 1.0  •  Pure JavaFX 21");
            Label desc = new Label("""
                    A complete desktop-OS simulation running on the JVM.
                    
                    Built with only 5 source files.
                    Features: custom window manager, virtual filesystem,
                    process isolation, theming, and several built-in apps.
                    
                    Default accounts:
                      admin / password
                      guest / guest
                    """);
            desc.setWrapText(true);

            VBox root = new VBox(12, title, ver, desc);
            root.setPadding(new Insets(20));
            root.setAlignment(Pos.TOP_CENTER);

            return new UI.OSWindow("about-" + System.nanoTime(), "About JavaOS", root, 440, 360);
        }
    }
}