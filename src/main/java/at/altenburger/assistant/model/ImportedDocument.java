package at.altenburger.assistant.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Data
@NoArgsConstructor
public class ImportedDocument {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String path;
    private Long lastModified;

    @OneToMany(mappedBy = "document", fetch = FetchType.EAGER)
    private List<ImportedDocumentPart> parts;

    public ImportedDocument(String path, Long lastModified) {
        this.path = path;
        this.lastModified = lastModified;
        this.parts = new ArrayList<>();
    }
}
