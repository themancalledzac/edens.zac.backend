package edens.zac.portfolio.backend.entity;

import jakarta.validation.constraints.NotNull;
import edens.zac.portfolio.backend.types.ContentType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "content"
)
@Inheritance(strategy = InheritanceType.JOINED)
@Data
@NoArgsConstructor
@SuperBuilder
public abstract class ContentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "content_type")
    private ContentType contentType;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // Method to get the specific content type
    public abstract ContentType getContentType();
}