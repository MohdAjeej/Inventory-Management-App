package com.b2binventory.service;

import com.b2binventory.config.AppProperties;
import com.b2binventory.exception.BadRequestException;
import com.b2binventory.exception.NotFoundException;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

/**
 * Stores uploaded images on local disk under random, unguessable names
 * (served at /api/files/{name}). Swap for object storage in production.
 */
@Service
public class FileStorageService {

    private static final Map<String, String> ALLOWED = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp");

    private final Path root;

    public FileStorageService(AppProperties props) throws IOException {
        this.root = Path.of(props.uploads() != null && props.uploads().dir() != null ? props.uploads().dir() : "uploads")
                .toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    public String storeImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("No file uploaded");
        }
        String extension = ALLOWED.get(file.getContentType());
        if (extension == null) {
            throw new BadRequestException("Only JPEG, PNG or WebP images are allowed");
        }
        String name = UUID.randomUUID().toString().replace("-", "") + extension;
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, root.resolve(name), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("Could not store file", e);
        }
        return "/api/files/" + name;
    }

    public Resource load(String name) {
        if (!name.matches("[a-f0-9]{32}\\.(jpg|png|webp)")) {
            throw new NotFoundException("File", name);
        }
        Path path = root.resolve(name).normalize();
        if (!path.startsWith(root) || !Files.exists(path)) {
            throw new NotFoundException("File", name);
        }
        return new PathResource(path);
    }

    public void deleteQuietly(String url) {
        if (url == null || !url.startsWith("/api/files/")) return;
        try {
            Files.deleteIfExists(root.resolve(url.substring("/api/files/".length())).normalize());
        } catch (IOException ignored) {
            // Orphaned files are harmless.
        }
    }
}
