package ru.andreyheaven.bookserver.controler;

import com.ibm.icu.text.*;
import org.springframework.core.io.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import ru.andreyheaven.bookserver.config.*;
import ru.andreyheaven.bookserver.domain.*;
import ru.andreyheaven.bookserver.repository.*;
import java.io.*;
import java.nio.file.*;
import java.util.zip.*;

@RestController
@RequestMapping("/b")
public class BookController {
    private final BookRepository bookRepository;
    private final AppProperties properties;

    public BookController(BookRepository bookRepository, AppProperties properties) {
        this.bookRepository = bookRepository;
        this.properties = properties;
    }

    @GetMapping(value = "/{id}")
    public String index() {
        return "";
    }

    @GetMapping(value = "/{id}/{format}", produces = "application/zip")
    public ResponseEntity<?> download(@PathVariable("id") Integer id,@PathVariable("format") String format) {
        return bookRepository.findById(id).map(book -> {
            final var zip = Path.of(properties.getDataDir(), book.getArchive());
            final var fileName = getFileName(book);

            try (var zipFile = new ZipFile(zip.toFile())) {
                final File tempFile = File.createTempFile(fileName, "zip");

                try (InputStream inputStream = zipFile.getInputStream(zipFile.getEntry(book.getFileID() + "." + book.getFormat()));
                     OutputStream outputStream = new FileOutputStream(tempFile);
                     ZipOutputStream zipOut = new ZipOutputStream(outputStream)) {
                    ZipEntry zipEntry = new ZipEntry(fileName);
                    zipOut.putNextEntry(zipEntry);
                    inputStream.transferTo(zipOut);
                }

                InputStreamResource resource = new InputStreamResource(new FileInputStream(tempFile));

                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + fileName+".zip")
                        .contentLength(tempFile.length())
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .body(resource);
            } catch (IOException e) {
                return ResponseEntity.badRequest().build();
            }
        }).orElse(ResponseEntity.notFound().build());
    }

    private String getFileName(Book book) {
        Transliterator toLatinTrans = Transliterator.getInstance("Cyrillic-Latin");
        final Author next = book.getAuthors().iterator().next();
        String result = toLatinTrans.transliterate(next.getSurname() + " " + book.getTitle());
        result = result.replace(" ", "_");
        return result + "." + book.getFormat();
    }
}
