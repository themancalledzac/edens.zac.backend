package edens.zac.portfolio.backend.entity;

import edens.zac.portfolio.backend.types.ContentBlockType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

// TODO: Verify tomorrow if this is a valid test file, such as using 'violations'

class ContentBlockEntityTest {

    private Validator validator;

    // Concrete implementation for testing
    static class TestContentBlock extends ContentBlockEntity {
        @Override
        public ContentBlockType getBlockType() {
            return ContentBlockType.TEXT;
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
        ContentBlockEntity block = new TestContentBlock();
        block.setCollectionId(1L);
        block.setOrderIndex(1);
        block.setBlockType(ContentBlockType.TEXT);
        block.setCaption("Test Caption");
        
        // When
        Set<ConstraintViolation<ContentBlockEntity>> violations = validator.validate(block);
        
        // Then
        assertThat(violations).isEmpty();
    }

    @Test
    void shouldFailValidationWhenCollectionIdIsNull() {
        // Given
        ContentBlockEntity block = new TestContentBlock();
        block.setOrderIndex(1);
        block.setBlockType(ContentBlockType.TEXT);
        
        // When
        Set<ConstraintViolation<ContentBlockEntity>> violations = validator.validate(block);
        
        // Then
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("collectionId");
    }

    @Test
    void shouldFailValidationWhenOrderIndexIsNull() {
        // Given
        ContentBlockEntity block = new TestContentBlock();
        block.setCollectionId(1L);
        block.setBlockType(ContentBlockType.TEXT);
        
        // When
        Set<ConstraintViolation<ContentBlockEntity>> violations = validator.validate(block);
        
        // Then
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("orderIndex");
    }

    @Test
    void shouldFailValidationWhenBlockTypeIsNull() {
        // Given
        ContentBlockEntity block = new TestContentBlock();
        block.setCollectionId(1L);
        block.setOrderIndex(1);
        
        // When
        Set<ConstraintViolation<ContentBlockEntity>> violations = validator.validate(block);
        
        // Then
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("blockType");
    }

    @Test
    void testGetBlockTypeImplementation() {
        // Given
        TestContentBlock block = new TestContentBlock();
        
        // When/Then
        assertThat(block.getBlockType()).isEqualTo(ContentBlockType.TEXT);
    }

    @Test
    void testTimestampsAreNotTestedDirectly() {
        // Note: We don't directly test @CreationTimestamp and @UpdateTimestamp
        // as they are managed by Hibernate at persistence time
        // This test is mostly documentation of this fact
        ContentBlockEntity block = new TestContentBlock();
        assertThat(block.getCreatedAt()).isNull();
        assertThat(block.getUpdatedAt()).isNull();
    }
}