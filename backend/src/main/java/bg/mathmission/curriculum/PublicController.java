package bg.mathmission.curriculum;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public")
@Tag(name = "Public")
public class PublicController {

    @GetMapping("/curriculum")
    public List<CurriculumCatalog.LessonStop> curriculum() {
        return CurriculumCatalog.LESSONS;
    }

    @GetMapping("/avatars")
    public List<String> avatars() {
        return List.of("fox", "owl", "rocket", "cat", "robot", "planet", "dragon", "turtle");
    }

    @GetMapping("/info")
    public Map<String, String> info() {
        return Map.of("product", "Math Mission", "scope",
                "Алгебра за 7. клас: рационални изрази, едночлени, многочлени. Не е пълна симулация на НВО.");
    }
}
