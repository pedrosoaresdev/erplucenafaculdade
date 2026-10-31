package br.com.lucena.erp.academic;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CurriculumRepository extends JpaRepository<CurriculumItem, UUID> {
    List<CurriculumItem> findByCourseIdOrderBySemesterAsc(UUID courseId);

    Optional<CurriculumItem> findByCourseIdAndSubjectId(UUID courseId, UUID subjectId);

    Optional<CurriculumItem> findByIdAndCourseId(UUID id, UUID courseId);

    boolean existsByCourseIdAndPrerequisiteSubjectId(UUID courseId, UUID subjectId);

    boolean existsBySubjectId(UUID subjectId);

    boolean existsByCourseId(UUID courseId);
}
