package br.com.lucena.erp.academic;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClassSubjectRepository extends JpaRepository<ClassSubject, UUID> {
    List<ClassSubject> findByClassIdOrderByWeekdayAscStartTimeAsc(UUID classId);

    Optional<ClassSubject> findByIdAndClassId(UUID id, UUID classId);

    boolean existsByClassIdAndSubjectId(UUID classId, UUID subjectId);

    boolean existsBySubjectId(UUID subjectId);

    boolean existsByTeacherId(UUID teacherId);

    /** Aulas do professor no mesmo período letivo e dia da semana com horário sobreposto. */
    @Query("""
            select cs from ClassSubject cs, SchoolClass c where c.id = cs.classId and c.termId = :termId
              and cs.teacherId = :teacherId and cs.weekday = :weekday
              and cs.startTime < :end and cs.endTime > :start and (:ignoreId is null or cs.id <> :ignoreId)
            """)
    List<ClassSubject> teacherConflicts(@Param("termId") UUID termId, @Param("teacherId") UUID teacherId, @Param("weekday") Short weekday,
                                        @Param("start") LocalTime start, @Param("end") LocalTime end, @Param("ignoreId") UUID ignoreId);

    /** Aulas na mesma sala, período e dia com horário sobreposto. */
    @Query("""
            select cs from ClassSubject cs, SchoolClass c where c.id = cs.classId and c.termId = :termId
              and c.facultyId = :facultyId and lower(c.room) = lower(:room) and cs.weekday = :weekday
              and cs.startTime < :end and cs.endTime > :start and (:ignoreId is null or cs.id <> :ignoreId)
            """)
    List<ClassSubject> roomConflicts(@Param("facultyId") UUID facultyId, @Param("termId") UUID termId, @Param("room") String room,
                                     @Param("weekday") Short weekday, @Param("start") LocalTime start, @Param("end") LocalTime end,
                                     @Param("ignoreId") UUID ignoreId);
}
