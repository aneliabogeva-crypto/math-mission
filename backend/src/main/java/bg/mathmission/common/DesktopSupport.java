package bg.mathmission.common;

import java.awt.AWTException;
import java.awt.Desktop;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.Toolkit;
import java.awt.TrayIcon;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Desktop mode (installed app): opens the browser when the server is ready and adds a tray icon
 * with "Open" and "Quit". Only active with -Dmathmission.desktop=true (set by the installers).
 */
@Component
@ConditionalOnProperty(name = "mathmission.desktop", havingValue = "true")
public class DesktopSupport {

    private static final Logger log = LoggerFactory.getLogger(DesktopSupport.class);

    private final Environment env;
    private final ConfigurableApplicationContext context;

    public DesktopSupport(Environment env, ConfigurableApplicationContext context) {
        this.env = env;
        this.context = context;
    }

    public static boolean enabled() {
        return Boolean.getBoolean("mathmission.desktop");
    }

    /** If the app is already running (second double-click), just open the browser and exit. */
    public static boolean alreadyRunning(int port) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress("127.0.0.1", port), 300);
        } catch (IOException e) {
            return false;
        }
        openBrowser("http://localhost:" + port + "/");
        return true;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ready() {
        String url = "http://localhost:" + env.getProperty("local.server.port", env.getProperty("server.port", "18080")) + "/";
        log.info("Math Mission is running at {}", url);
        try {
            installTray(url);
        } catch (Throwable t) {
            log.warn("System tray not available: {}", t.toString());
        }
        openBrowser(url);
    }

    static void openBrowser(String url) {
        try {
            if (!GraphicsEnvironment.isHeadless() && Desktop.isDesktopSupported()
                    && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
                return;
            }
            String os = System.getProperty("os.name").toLowerCase();
            String[] cmd = os.contains("win") ? new String[] {"rundll32", "url.dll,FileProtocolHandler", url}
                    : os.contains("mac") ? new String[] {"open", url} : new String[] {"xdg-open", url};
            new ProcessBuilder(cmd).start();
        } catch (Exception | Error e) {
            log.warn("Could not open the browser automatically. Open {} manually.", url);
        }
    }

    private void installTray(String url) {
        try {
            if (GraphicsEnvironment.isHeadless() || !SystemTray.isSupported()) return;
            Image image = Toolkit.getDefaultToolkit().getImage(getClass().getResource("/static/icon-192.png"));
            PopupMenu menu = new PopupMenu();
            MenuItem open = new MenuItem("Open Math Mission");
            open.addActionListener(e -> openBrowser(url));
            MenuItem quit = new MenuItem("Quit");
            quit.addActionListener(e -> {
                SystemTray.getSystemTray().remove(SystemTray.getSystemTray().getTrayIcons()[0]);
                System.exit(org.springframework.boot.SpringApplication.exit(context));
            });
            menu.add(open);
            menu.addSeparator();
            menu.add(quit);
            TrayIcon icon = new TrayIcon(image, "Math Mission", menu);
            icon.setImageAutoSize(true);
            icon.addActionListener(e -> openBrowser(url));
            SystemTray.getSystemTray().add(icon);
        } catch (AWTException | RuntimeException | Error e) {
            // No display or no tray (e.g. a headless machine): the server keeps running without it.
            log.warn("System tray not available: {}", e.toString());
        }
    }
}
