package edens.zac.portfolio.backend.entity;

import edens.zac.portfolio.backend.types.ContentType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

// TODO: Verify tomorrow if this is a valid test file, such as using 'violations'

class ContentEntityTest {

    private Validator validator;

    // Concrete implementation for testing
    static class TestContent extends ContentEntity {
        @Override
        public ContentType getContentType() {
            return ContentType.TEXT;
        }
    }

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void shouldPassValidationWithValidData() {
        // Given
        ContentEntity block = new TestContent();
        block.setContentType(ContentType.TEXT);

        // When
        Set<ConstraintViolation<ContentEntity>> violations = validator.validate(block);
        
        // Then
        assertThat(violations).isEmpty();
    }

    @Test
    void testGetContentTypeImplementation() {
        // Given
        TestContent block = new TestContent();
        
        // When/Then
        assertThat(block.getContentType()).isEqualTo(ContentType.TEXT);
    }

    @Test
    void testTimestampsAreNotTestedDirectly() {
        // Note: We don't directly test @CreationTimestamp and @UpdateTimestamp
        // as they are managed by Hibernate at persistence time
        // This test is mostly documentation of this fact
        ContentEntity block = new TestContent();
        assertThat(block.getCreatedAt()).isNull();
        assertThat(block.getUpdatedAt()).isNull();
    }
}