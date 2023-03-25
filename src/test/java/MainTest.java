
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.Test;

import io.github.bambooisland.prunusmume.PMDocument;

public class MainTest {
    @Test
    void test() throws IOException {
        PMDocument.loadDocument(Files.newInputStream(Paths.get("sample/apache.pdf"))).rotate(false, true)
                .resize(PDRectangle.A2).combine(false).build(Files.newOutputStream(Paths.get("sample/build.pdf")));
    }
}
