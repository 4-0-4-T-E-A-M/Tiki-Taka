package io.github.team404.tikitaka.performanceseat.repository;

import io.github.team404.tikitaka.performanceseat.entity.Section;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SectionRepository extends JpaRepository<Section, Long> {

    List<Section> findAllByScheduleIdIn(List<Long> scheduleIds);

    void deleteAllByScheduleIdIn(List<Long> scheduleIds);
}
