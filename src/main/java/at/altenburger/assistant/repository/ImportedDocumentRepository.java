package at.altenburger.assistant.repository;

import at.altenburger.assistant.model.ImportedDocument;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ImportedDocumentRepository extends CrudRepository<ImportedDocument, Integer> {

    public Optional<ImportedDocument> findByPath(String path);
}
