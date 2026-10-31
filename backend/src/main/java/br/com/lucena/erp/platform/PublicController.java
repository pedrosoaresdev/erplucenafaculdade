package br.com.lucena.erp.platform;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** Dados públicos mínimos para a tela de login (nome e logotipo da instituição). */
@Tag(name = "Público")
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicController {
    private final FacultyService faculties;

    @GetMapping("/faculties/{slug}")
    public FacultyDtos.PublicFaculty faculty(@PathVariable String slug) {
        return faculties.publicInfo(slug.toLowerCase());
    }
}
