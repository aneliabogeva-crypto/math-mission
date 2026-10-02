package bg.mathmission;

import bg.mathmission.common.DesktopSupport;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Math Mission modular monolith. Modules (one package each): identity, consent, curriculum,
 * content, assessment, progress, gamification, classroom, reporting, audit, math.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class MathMissionApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(MathMissionApplication.class);
        if (DesktopSupport.enabled()) {
            // Installed desktop app: one instance per computer, local-only server, browser + tray icon.
            if (DesktopSupport.alreadyRunning(Integer.getInteger("server.port", 18080))) {
                return;
            }
            app.setAdditionalProfiles("desktop");
            app.setHeadless(false);
        }
        app.run(args);
    }
}
