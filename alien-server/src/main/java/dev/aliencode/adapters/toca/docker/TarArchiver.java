package dev.aliencode.adapters.toca.docker;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.stream.Stream;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarConstants;

/**
 * Empacota uma pasta num tar para a API de cópia do Docker, já com o dono
 * que o arquivo deve ter dentro da Toca (o usuário "alien", uid 1000).
 * Links simbólicos viram links (não são seguidos), para nada de fora da pasta vazar.
 */
final class TarArchiver {

    private TarArchiver() {
    }

    /**
     * @param prefix nome da pasta raiz dentro do tar (ex.: "repo-a" → "repo-a/src/Main.java")
     * @return arquivo temporário com o tar; quem chama apaga
     */
    static Path archive(Path source, String prefix, int uid, int gid) {
        try {
            Path tar = Files.createTempFile("alien-toca-", ".tar");
            try (OutputStream out = Files.newOutputStream(tar);
                 TarArchiveOutputStream tarOut = new TarArchiveOutputStream(out);
                 Stream<Path> walk = Files.walk(source)) {
                tarOut.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
                tarOut.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX);
                List<Path> paths = walk.sorted().toList();
                for (Path path : paths) {
                    writeEntry(tarOut, source, path, prefix, uid, gid);
                }
                tarOut.finish();
            }
            return tar;
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao empacotar " + source, e);
        }
    }

    private static void writeEntry(TarArchiveOutputStream tarOut, Path source, Path path, String prefix,
                                   int uid, int gid) throws IOException {
        String relative = source.relativize(path).toString().replace('\\', '/');
        String name = relative.isEmpty() ? prefix : prefix + "/" + relative;

        TarArchiveEntry entry;
        boolean regularFile = false;
        if (Files.isSymbolicLink(path)) {
            entry = new TarArchiveEntry(name, TarConstants.LF_SYMLINK);
            entry.setLinkName(Files.readSymbolicLink(path).toString());
            entry.setMode(0120777);
        } else if (Files.isDirectory(path)) {
            entry = new TarArchiveEntry(name + "/");
            entry.setMode(TarArchiveEntry.DEFAULT_DIR_MODE);
        } else {
            entry = new TarArchiveEntry(name);
            entry.setMode(Files.isExecutable(path) ? 0100755 : 0100644);
            entry.setSize(Files.size(path));
            regularFile = true;
        }
        entry.setIds(uid, gid);
        FileTime modified = Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS);
        entry.setModTime(modified);

        tarOut.putArchiveEntry(entry);
        // Nunca pelo entry.isFile(): para links ele também é true, e o conteúdo do alvo vazaria para a Toca.
        if (regularFile) {
            Files.copy(path, tarOut);
        }
        tarOut.closeArchiveEntry();
    }
}
