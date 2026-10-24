package os;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Core services: Kernel, Process manager, EventBus, Virtual FS,
 * Clipboard, Metrics, Permissions. Everything non-UI lives here.
 */
public final class Core {

    /* ======================== Event Bus ======================== */
    public static final class EventBus {
        private final Map<String, CopyOnWriteArrayList<Consumer<Object>>> listeners = new ConcurrentHashMap<>();

        public void subscribe(String topic, Consumer<Object> h) {
            listeners.computeIfAbsent(topic, k -> new CopyOnWriteArrayList<>()).add(h);
        }

        public void unsubscribe(String topic, Consumer<Object> h) {
            var list = listeners.get(topic);
            if (list != null) list.remove(h);
        }

        public void publish(String topic, Object payload) {
            var list = listeners.get(topic);
            if (list != null) {
                for (var c : list) {
                    try { c.accept(payload); } catch (Exception ignored) {}
                }
            }
        }
    }

    /* ======================== Process ======================== */
    public static final class Process {
        public enum State { READY, RUNNING, SUSPENDED, TERMINATED, CRASHED }

        private static final AtomicInteger SEQ = new AtomicInteger(1000);
        public final int pid = SEQ.getAndIncrement();
        public final String name;
        public final String appId;
        public final long startMs = System.currentTimeMillis();
        private volatile State state = State.RUNNING;
        private volatile Thread worker;
        private volatile Object window; // OSWindow

        public Process(String name, String appId) {
            this.name = name;
            this.appId = appId;
        }

        public State getState() { return state; }
        public void setState(State s) { state = s; }
        public void setWorker(Thread t) { worker = t; }
        public void setWindow(Object w) { window = w; }
        public Object getWindow() { return window; }
        public boolean isAlive() {
            return state == State.READY || state == State.RUNNING || state == State.SUSPENDED;
        }
    }

    /* ======================== Process Manager ======================== */
    public static final class ProcessManager {
        private final Map<Integer, Process> map = new ConcurrentHashMap<>();
        private final EventBus bus;

        public ProcessManager(EventBus bus) { this.bus = bus; }

        public Process create(String name, String appId) {
            Process p = new Process(name, appId);
            map.put(p.pid, p);
            bus.publish("process.started", p);
            return p;
        }

        public void terminate(int pid, boolean force) {
            Process p = map.remove(pid);
            if (p == null) return;
            p.setState(Process.State.TERMINATED);
            if (p.worker != null && p.worker.isAlive() && force) p.worker.interrupt();
            bus.publish("process.terminated", p);
        }

        public void crash(int pid, Throwable t) {
            Process p = map.remove(pid);
            if (p == null) return;
            p.setState(Process.State.CRASHED);
            bus.publish("process.crashed", Map.of("process", p, "cause", t));
        }

        public Collection<Process> all() { return map.values(); }
        public Process get(int pid) { return map.get(pid); }
        public void killAll() { new ArrayList<>(map.keySet()).forEach(id -> terminate(id, true)); }
    }

    /* ======================== Metrics ======================== */
    public static final class Metrics {
        private final com.sun.management.OperatingSystemMXBean osBean =
                (com.sun.management.OperatingSystemMXBean) java.lang.management.ManagementFactory.getOperatingSystemMXBean();
        private final java.lang.management.MemoryMXBean mem =
                java.lang.management.ManagementFactory.getMemoryMXBean();

        public double cpu() {
            double v = osBean.getCpuLoad();
            return v < 0 ? 0 : Math.min(1.0, v);
        }
        public long usedHeap() { return mem.getHeapMemoryUsage().getUsed(); }
        public long maxHeap() { return mem.getHeapMemoryUsage().getMax(); }
        public long uptime() { return java.lang.management.ManagementFactory.getRuntimeMXBean().getUptime(); }
    }

    /* ======================== Clipboard ======================== */
    public static final class Clipboard {
        private String text = "";
        public synchronized void set(String t) { text = t == null ? "" : t; }
        public synchronized String get() { return text; }
        public synchronized boolean has() { return !text.isEmpty(); }
    }

    /* ======================== Permissions ======================== */
    public enum Permission { FILES, NETWORK, AUDIO }

    public static final class PermissionManager {
        private final Map<String, Set<Permission>> granted = new ConcurrentHashMap<>();
        public boolean has(String app, Permission p) {
            return granted.getOrDefault(app, Set.of()).contains(p);
        }
        public void grant(String app, Permission p) {
            granted.computeIfAbsent(app, k -> ConcurrentHashMap.newKeySet()).add(p);
        }
        public boolean check(String app, Permission p) {
            if (has(app, p)) return true;
            grant(app, p); // Stage-1 auto-grant
            return true;
        }
    }

    /* ======================== Virtual File System ======================== */
    public static final class VFS {
        private final Path root;
        private final Map<String, String> users = new ConcurrentHashMap<>(); // user -> salt:hash

        public VFS(Path root) throws IOException {
            this.root = root.toAbsolutePath().normalize();
            Files.createDirectories(this.root);
            // standard layout
            for (String d : List.of("system/logs", "system/config", "tmp", "trash",
                    "apps", "home/guest/Desktop", "home/guest/Documents",
                    "home/guest/Pictures", "home/guest/Downloads")) {
                Files.createDirectories(this.root.resolve(d));
            }
            // default users (password = "password")
            createUser("admin", "password");
            createUser("guest", "guest");
            // log file
            Path log = resolve("/system/logs/os.log");
            if (!Files.exists(log)) Files.writeString(log, "JavaOS log started " + Instant.now() + "\n");
        }

        public Path resolve(String virtual) {
            String clean = virtual.replace('\\', '/');
            if (!clean.startsWith("/")) clean = "/" + clean;
            Path p = root.resolve(clean.substring(1)).normalize();
            if (!p.startsWith(root)) throw new SecurityException("Path escape: " + virtual);
            return p;
        }

        public boolean exists(String path) { return Files.exists(resolve(path)); }

        public List<String> list(String path) throws IOException {
            Path dir = resolve(path);
            if (!Files.isDirectory(dir)) return List.of();
            try (var s = Files.list(dir)) {
                return s.map(p -> p.getFileName().toString()).sorted().collect(Collectors.toList());
            }
        }

        public String read(String path) throws IOException {
            return Files.readString(resolve(path), StandardCharsets.UTF_8);
        }

        public void write(String path, String content) throws IOException {
            Path p = resolve(path);
            Files.createDirectories(p.getParent());
            Files.writeString(p, content, StandardCharsets.UTF_8);
        }

        public void append(String path, String content) throws IOException {
            Path p = resolve(path);
            Files.createDirectories(p.getParent());
            Files.writeString(p, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }

        public void mkdir(String path) throws IOException {
            Files.createDirectories(resolve(path));
        }

        public void delete(String path) throws IOException {
            Path p = resolve(path);
            if (Files.isDirectory(p)) {
                try (var walk = Files.walk(p)) {
                    walk.sorted(Comparator.reverseOrder()).forEach(f -> {
                        try { Files.deleteIfExists(f); } catch (IOException ignored) {}
                    });
                }
            } else {
                Files.deleteIfExists(p);
            }
        }

        public void move(String from, String to) throws IOException {
            Files.move(resolve(from), resolve(to), StandardCopyOption.REPLACE_EXISTING);
        }

        public void copy(String from, String to) throws IOException {
            Files.copy(resolve(from), resolve(to), StandardCopyOption.REPLACE_EXISTING);
        }

        public void trash(String path) throws IOException {
            String name = Path.of(path).getFileName().toString();
            String dest = "/trash/" + System.currentTimeMillis() + "_" + name;
            move(path, dest);
        }

        public long size(String path) throws IOException {
            Path p = resolve(path);
            if (Files.isDirectory(p)) {
                try (var walk = Files.walk(p)) {
                    return walk.filter(Files::isRegularFile).mapToLong(f -> {
                        try { return Files.size(f); } catch (IOException e) { return 0; }
                    }).sum();
                }
            }
            return Files.size(p);
        }

        /* ---------- users ---------- */
        public void createUser(String user, String password) {
            try {
                byte[] salt = new byte[16];
                new SecureRandom().nextBytes(salt);
                String hash = hash(password, salt);
                users.put(user, Base64.getEncoder().encodeToString(salt) + ":" + hash);
                Files.createDirectories(resolve("/home/" + user + "/Desktop"));
                Files.createDirectories(resolve("/home/" + user + "/Documents"));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        public boolean authenticate(String user, String password) {
            String stored = users.get(user);
            if (stored == null) return false;
            String[] parts = stored.split(":");
            byte[] salt = Base64.getDecoder().decode(parts[0]);
            return hash(password, salt).equals(parts[1]);
        }

        private static String hash(String pw, byte[] salt) {
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                md.update(salt);
                byte[] dig = md.digest(pw.getBytes(StandardCharsets.UTF_8));
                return Base64.getEncoder().encodeToString(dig);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        public Set<String> users() { return users.keySet(); }
    }

    /* ======================== Kernel (singleton) ======================== */
    private static Core INSTANCE;

    public final EventBus bus = new EventBus();
    public final ProcessManager processes = new ProcessManager(bus);
    public final Metrics metrics = new Metrics();
    public final Clipboard clipboard = new Clipboard();
    public final PermissionManager permissions = new PermissionManager();
    public VFS vfs;
    public String currentUser = "guest";

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "JavaOS-Svc");
        t.setDaemon(true);
        return t;
    });

    private Core() {}

    public static synchronized Core get() {
        if (INSTANCE == null) INSTANCE = new Core();
        return INSTANCE;
    }

    public void init(Path diskRoot) throws IOException {
        vfs = new VFS(diskRoot);
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            try {
                StringWriter sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw));
                vfs.append("/system/logs/os.log", Instant.now() + " [" + t.getName() + "]\n" + sw + "\n");
            } catch (Exception ignored) {
                e.printStackTrace();
            }
            bus.publish("system.error", e.getMessage());
        });

        scheduler.scheduleAtFixedRate(() -> bus.publish("clock.tick", System.currentTimeMillis()), 0, 1, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(() -> bus.publish("metrics.update", metrics), 0, 2, TimeUnit.SECONDS);
    }

    public void shutdown() {
        processes.killAll();
        scheduler.shutdownNow();
    }
}