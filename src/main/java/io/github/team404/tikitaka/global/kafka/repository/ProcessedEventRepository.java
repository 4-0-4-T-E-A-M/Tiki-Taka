package io.github.team404.tikitaka.global.kafka.repository;

import io.github.team404.tikitaka.global.kafka.entity.ProcessedEvent;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {
}
