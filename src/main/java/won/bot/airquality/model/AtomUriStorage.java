package won.bot.airquality.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

@Repository
public class AtomUriStorage {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private static final int MAX_OWNED_FILES = 20;
    private static final String URI_STORAGE_FILE_ENDING = ".atomuris";

    private final String uriStorageDirectory;
    private SortedSet<Path> ownedFilePaths = new TreeSet<>();
    private Set<URI> uris = new HashSet<>();

    public AtomUriStorage(String uriStorageDirectory) {
        this.uriStorageDirectory = uriStorageDirectory;
        initializeUris(this.uriStorageDirectory);
        generateNewCurrentFilePath();
    }

    public void commit() {
        writeUrisToFile(generateNewCurrentFilePath());
        int maxFiles = MAX_OWNED_FILES;
        while (this.ownedFilePaths.size() > maxFiles) {
            try {
                Path currPath = this.ownedFilePaths.first();
                if (Files.deleteIfExists(currPath)) {
                    this.ownedFilePaths.remove(currPath);
                } else {
                    maxFiles--; // make sure it does not end in an endless loop
                }
            } catch (IOException e) {
                logger.warn("Failed to delete an old uri-storage file: {}", e.getMessage());
            }
        }
        logger.info("Committed changes to URI-Storage");
    }

    public Set<URI> getUris() {
        return new TreeSet<>(this.uris);
    }

    public boolean appendUri(URI newUri) {
        return this.uris.add(newUri);
    }

    public boolean deleteUri(URI uriToDelete) {
        return this.uris.remove(uriToDelete);
    }

    private void initializeUris(String directoryPath) {
        File directory = new File(directoryPath);
        if (!directory.exists()) {
            if (!directory.mkdir()) {
                throw new IllegalStateException("Could not create directory to store the URIs in.");
            }
        }
        File[] files = directory.listFiles((dir, filename) -> filename.endsWith(URI_STORAGE_FILE_ENDING));
        if (files == null) {
            throw new IllegalStateException("The directoryPath for the URI-storage could not be used");
        }

        this.ownedFilePaths = Arrays.stream(files).map(file -> Paths.get(file.getAbsolutePath())).collect(Collectors.toCollection(TreeSet::new));
        if (files.length == 0) {
            generateNewCurrentFilePath();
            this.uris = new HashSet<>();
        }
        this.uris = fetchUris(ownedFilePaths.last());
    }

    private Set<URI> fetchUris(Path filePath) {
        if (!filePath.toFile().exists()) {
            return new HashSet<>();
        }
        try {
            return Files.readAllLines(filePath).stream().map(URI::create).collect(Collectors.toSet());
        } catch (IOException e) {
            throw new IllegalStateException("The owned uris could not be read from the given file", e);
        }
    }

    private void writeUrisToFile(Path filePath) {
        StringBuilder sb = new StringBuilder();
        uris.stream().map(URI::toString).forEach(uri -> {
            sb.append(uri);
            sb.append('\n');
        });
        try (FileWriter writer = new FileWriter(filePath.toString(), false)) {
            writer.write(sb.toString());
        } catch (final IOException e) {
            throw new IllegalStateException("IO error when removing from the URI-Storage", e);
        }
    }

    private Path generateNewCurrentFilePath() {
        Path newPath = Paths.get(this.uriStorageDirectory,
                new SimpleDateFormat("yyyyMMddHHmmssSSS'" + URI_STORAGE_FILE_ENDING + "'").format(new Date()));
        this.ownedFilePaths.add(newPath);
        return newPath;
    }
}
