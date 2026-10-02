package bg.mathmission;

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
        SpringApplication.run(MathMissionApplication.class, args);
    }
}
