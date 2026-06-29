package w4cash.chat;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ChatController {

    private final ChatRepository repository;

    ChatController(ChatRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/chat/messages")
    List<ChatMessage> getMessages(@RequestParam(required = false) String since) {
        if (since == null || since.isBlank()) {
            return repository.findAll();
        }
        return repository.findByTimestampAfterOrderByTimestampAsc(LocalDateTime.parse(since));
    }

    @PostMapping("/chat/messages")
    ChatMessage postMessage(@RequestBody ChatMessage message) {
        message.setTimestamp(LocalDateTime.now());
        return repository.save(message);
    }
}
