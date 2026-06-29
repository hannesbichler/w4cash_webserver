package w4cash.chat;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

@Entity
public class ChatMessage {

    private @Id @GeneratedValue Long id;
    private String sender;
    private String text;
    private LocalDateTime timestamp;

    public ChatMessage() {}

    public ChatMessage(String sender, String text, LocalDateTime timestamp) {
        this.sender = sender;
        this.text = text;
        this.timestamp = timestamp;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSender() { return sender; }
    public void setSender(String sender) { this.sender = sender; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}
