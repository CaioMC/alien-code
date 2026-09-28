package dev.aliencode.adapters.toca.docker;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TarArchiverTest {

    @TempDir
    Path source;

    @Test
    void empacotaComPrefixoDonoModoELinks() throws IOException {
        Files.createDirectories(this.source.resolve("src/main"));
        Files.writeString(this.source.resolve("src/main/App.java"), "class App {}");
        Path script = Files.writeString(this.source.resolve("mvnw"), "#!/bin/sh");
        Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwxr-xr-x"));
        Files.createSymbolicLink(this.source.resolve("atalho"), Path.of("/etc/passwd"));

        Path tar = TarArchiver.archive(this.source, "api", 1000, 1000);
        Map<String, TarArchiveEntry> entries = new HashMap<>();
        Map<String, String> contents = new HashMap<>();
        try (InputStream in = Files.newInputStream(tar); TarArchiveInputStream tarIn = new TarArchiveInputStream(in)) {
            TarArchiveEntry entry;
            while ((entry = tarIn.getNextEntry()) != null) {
                entries.put(entry.getName(), entry);
                if (entry.isFile()) {
                    contents.put(entry.getName(), new String(tarIn.readAllBytes(), StandardCharsets.UTF_8));
                }
            }
        } finally {
            Files.delete(tar);
        }

        assertThat(entries).containsKeys("api/", "api/src/", "api/src/main/", "api/src/main/App.java", "api/mvnw", "api/atalho");
        assertThat(contents).containsEntry("api/src/main/App.java", "class App {}");
        assertThat(entries.values()).allSatisfy(e -> {
            assertThat(e.getLongUserId()).isEqualTo(1000);
            assertThat(e.getLongGroupId()).isEqualTo(1000);
        });
        assertThat(entries.get("api/mvnw").getMode() & 0777).isEqualTo(0755);
        assertThat(entries.get("api/src/main/App.java").getMode() & 0777).isEqualTo(0644);
        assertThat(entries.get("api/atalho").isSymbolicLink()).isTrue();
        assertThat(entries.get("api/atalho").getLinkName()).isEqualTo("/etc/passwd");
    }
}
