package at.altenburger.assistant.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImportedDocumentPart {
    @Id
    private String id;
    @ManyToOne()
    @JoinColumn(name="document_id", nullable=false)
    private ImportedDocument document;
}
