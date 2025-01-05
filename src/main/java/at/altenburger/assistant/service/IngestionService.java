package at.altenburger.assistant.service;

import at.altenburger.assistant.model.ImportedDocument;
import at.altenburger.assistant.model.ImportedDocumentPart;
import at.altenburger.assistant.repository.DocumentRepository;
import at.altenburger.assistant.repository.ImportedDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Slf4j
@RequiredArgsConstructor
public class IngestionService implements CommandLineRunner {
    private final DocumentRepository documentRepository;
    private final ImportedDocumentRepository importedDocumentRepository;
    private final ResourceLoader resourceLoader;

    @Value("${embeddings.import.directory}")
    private String importDirectoryPath;

    @Value("${embeddings.import.exclusions}")
    private String importExclusions;

    @Override
    public void run(String... args) throws IOException {
        importFromDirectory();
    }

    public void importFromDirectory() throws IOException {
        log.info("Starting import from {}", importDirectoryPath);

        Path directoryPath = Paths.get(importDirectoryPath).toAbsolutePath();

        if (!directoryPath.toFile().exists() || !directoryPath.toFile().isDirectory()) {
            throw new RuntimeException("Import directory does not exist or is not a directory");
        }

        List<Path> files = new ArrayList<>();

        Files.walkFileTree(Paths.get(importDirectoryPath), new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (isFileEligibleForImport(file)) {
                    files.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });

        AtomicInteger count = new AtomicInteger(1);
        files
                .forEach(file -> {
                    log.info("Importing file {} of {}: {}", count.getAndIncrement(), files.size(), file.toAbsolutePath());
                    handleFile(file);
                });

        log.info("Vector store loaded with data");
    }

    private void handleFile(Path resource) {
        String absolutePath = resource.toFile().getAbsolutePath();
        long lastModified = resource.toFile().lastModified();

        Optional<ImportedDocument> existingDoc = importedDocumentRepository.findByPath(absolutePath);

        if (existingDoc.isPresent()) {
            if (!existingDoc.get().getLastModified().equals(lastModified)) {
                log.info("Found existing file {}, removing from DB and VectorStore", absolutePath);
                deleteDocument(existingDoc.get());
            } else {
                log.info("Skipping file {} because it is already in the DB", absolutePath);
                return;
            }
        }

        DocumentReader reader = null;
        PathResource pathResource = new PathResource(resource);

        if (resource.endsWith("pdf")) {
            reader = new PagePdfDocumentReader(pathResource);
        } else {
            reader = new TextReader(pathResource);
        }
        TextSplitter textSplitter = new TokenTextSplitter();
        List<Document> documents = textSplitter.apply(reader.get());
        ImportedDocument importedDocument = new ImportedDocument(absolutePath, lastModified);

        List<ImportedDocumentPart> parts = documents
                .stream()
                .map(doc -> new ImportedDocumentPart(doc.getId(), importedDocument))
                .toList();

        importedDocument.setParts(parts);

        importedDocumentRepository.save(importedDocument);
        documentRepository.addDocuments(documents);
    }

    private boolean isFileEligibleForImport(Path path) {
        List<String> exclusions = Arrays.stream(importExclusions.split(",")).toList();
        String filePath = path.toFile().getAbsolutePath();

        return exclusions.stream().noneMatch(filePath::contains) &&
                (filePath.toLowerCase().endsWith(".txt") /*||
                        (filePath.toLowerCase().endsWith(".pdf") && isSearchablePDF(path))*/);
    }

    private boolean isSearchablePDF(Path path) {
        boolean searchable = false;

        try (PDDocument document = Loader.loadPDF(path.toFile())) {
            PDResources resource = document.getPage(0).getResources();
            for(COSName xObjectName:resource.getXObjectNames()) {
                PDXObject xObject = resource.getXObject(xObjectName);
                if (!(xObject instanceof PDImageXObject)) {
                    searchable = true;
                    break;
                }
            }
        } catch (IOException e) {
            log.error("Error encountered during check for searchable PDF, returning false", e);
        }

        return searchable;
    }

    private void deleteDocument(ImportedDocument importedDocument) {
        List<String> idList = importedDocument.getParts()
                .stream()
                .map(ImportedDocumentPart::getId)
                .map(String::valueOf)
                .toList();

        documentRepository.deleteDocuments(idList);
        importedDocumentRepository.delete(importedDocument);
    }
}