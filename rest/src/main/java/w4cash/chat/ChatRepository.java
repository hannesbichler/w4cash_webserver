package w4cash.chat;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

interface ChatRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findByTimestampAfterOrderByTimestampAsc(LocalDateTime since);
}
