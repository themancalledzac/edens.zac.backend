package edens.zac.portfolio.backend.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class ContentCodeModel extends ContentModel {
    
    @NotBlank
    @Size(max = 50000) // Large code content (bigger than text for long files)
    private String code;
    
    @NotNull
    @Size(max = 50)
    private String language; // "java", "javascript", "python", "sql", etc.
    
    @Size(max = 255)
    private String title; // Optional title for the code
    
    @Size(max = 255)
    private String fileName; // Optional filename for context
    
    private Boolean showLineNumbers; // Whether to display line numbers
    
    @Size(max = 1000)
    private String description; // Optional description of what the code does
}