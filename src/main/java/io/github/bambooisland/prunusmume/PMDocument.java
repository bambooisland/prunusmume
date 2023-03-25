package io.github.bambooisland.prunusmume;

import java.awt.geom.AffineTransform;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.pdfbox.multipdf.LayerUtility;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;

public class PMDocument {
    /**
     * Collections.synchronizedList(new ArrayList<>())
     */
    private List<File> list;
    private String author;
    private String creator;
    private String producer;
    private final static File tmpDir = new Supplier<File>() {
        @Override
        public File get() {
            File file = new File(System.getProperty("java.io.tmpdir") + "/prunusmume");
            file.mkdir();
            return file;
        }
    }.get();

    private static File getNewTmpFile() throws IOException {
        File file = File.createTempFile("prunusmume", ".tmp.pdf", tmpDir);
        file.deleteOnExit();
        return file;
    }

    public PMDocument(List<File> list) {
        this.list = list;
        this.setAuthor("");
        this.setCreator("");
        this.setProducer("");
    }

    /**
     * evince: 「作者」<br>
     * default: ""<br>
     */
    public PMDocument setAuthor(String author) {
        this.author = author;
        return this;
    }

    /**
     * evince: 「PDFの作成者」<br>
     * example: "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like
     * Gecko) Chrome/91.0.4472.114 Safari/537.36"<br>
     * default: "prunusmume"
     */
    public PMDocument setCreator(String creator) {
        this.creator = creator;
        return this;
    }

    /**
     * evince: 「PDF作成ツール」<br>
     * example: "Skia/PDF m91"<br>
     * default: "Apache PDFBox"<br>
     */
    public PMDocument setProducer(String producer) {
        this.producer = producer;
        return this;
    }

    public static PMDocument loadDocument(InputStream stream) throws IOException {
        List<File> list = Collections.synchronizedList(new ArrayList<>());
        try (PDDocument document = PDDocument.load(stream)) {
            document.getPages().forEach(page -> {
                try (PDDocument doc = new PDDocument();) {
                    doc.importPage(page);
                    File file = getNewTmpFile();
                    doc.save(file);
                    list.add(file);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        }
        return new PMDocument(list);
    }

    public static PMDocument emptyDocument() {
        return new PMDocument(Collections.synchronizedList(new ArrayList<>()));
    }

    private PDDocument rotate(PDDocument doc, boolean turnLeft) throws IOException {
        PDDocument newDoc = new PDDocument();
        PDRectangle rect = doc.getPage(0).getMediaBox();
        PDPage newPage = new PDPage(new PDRectangle(rect.getHeight(), rect.getWidth()));
        LayerUtility util = new LayerUtility(newDoc);
        if (turnLeft) {
            double halfHeight = rect.getHeight() / 2;
            util.appendFormAsLayer(newPage, util.importPageAsForm(doc, 0),
                    AffineTransform.getRotateInstance(Math.toRadians(90), halfHeight, halfHeight),
                    UUID.randomUUID().toString());
        } else {
            double halfWidth = rect.getWidth() / 2;
            util.appendFormAsLayer(newPage, util.importPageAsForm(doc, 0),
                    AffineTransform.getRotateInstance(Math.toRadians(-90), halfWidth, halfWidth),
                    UUID.randomUUID().toString());
        }
        newDoc.addPage(newPage);
        return newDoc;
    }

    private File rotate(File file, boolean turnLeft) throws IOException {
        try (PDDocument doc = PDDocument.load(file)) {
            try (PDDocument newDoc = rotate(doc, turnLeft)) {
                newDoc.save(file);
            }
            return file;
        }
    }

    /**
     * @param longer { true => height > width, false => height < width }
     */
    public PMDocument rotate(boolean longer, boolean turnLeft) {
        list = list.stream().map(file -> {
            try (PDDocument doc = PDDocument.load(file)) {
                PDRectangle rect = doc.getPage(0).getMediaBox();
                if ((rect.getHeight() >= rect.getWidth() && longer == true)
                        || rect.getHeight() <= rect.getWidth() && longer == false) {
                    return file;
                }
                try (PDDocument newDoc = rotate(doc, turnLeft)) {
                    newDoc.save(file);
                }
                return file;
            } catch (IOException e) {
                e.printStackTrace();
                return file;
            }
        }).collect(Collectors.toList());
        return this;
    }

    private static File resize(File file, PDRectangle newRect) {
        try (PDDocument oldDoc = PDDocument.load(file); PDDocument newDoc = new PDDocument()) {
            PDRectangle oldRect = oldDoc.getPage(0).getMediaBox();
            if ((oldRect.equals(newRect))) {
                return file;
            }
            PDPage newPage;
            if (oldRect.getWidth() > oldRect.getHeight() == newRect.getWidth() > newRect.getHeight()) {
                newPage = new PDPage(newRect);
            } else {
                newPage = new PDPage(new PDRectangle(newRect.getHeight(), newRect.getWidth()));
            }
            LayerUtility util = new LayerUtility(newDoc);
            double heightRate = newPage.getMediaBox().getHeight() / oldRect.getHeight();
            double widthRate = newPage.getMediaBox().getWidth() / oldRect.getWidth();
            double oldAspect = oldRect.getHeight() / oldRect.getWidth();
            double newAspect = newPage.getMediaBox().getHeight() / newPage.getMediaBox().getWidth();
            AffineTransform transform = null;
            if (oldAspect == newAspect) {
                transform = AffineTransform.getScaleInstance(heightRate, heightRate);
            } else if (oldAspect > newAspect) {
                transform = AffineTransform.getScaleInstance(heightRate, heightRate);
                transform.translate((newPage.getMediaBox().getWidth() - oldRect.getWidth() * heightRate) / 2, 0);
            } else {
                transform = AffineTransform.getScaleInstance(widthRate, widthRate);
                transform.concatenate(AffineTransform.getTranslateInstance(0,
                        (newPage.getMediaBox().getHeight() - oldRect.getHeight() * widthRate) / 2));
            }
            util.appendFormAsLayer(newPage, util.importPageAsForm(oldDoc, 0), transform, UUID.randomUUID().toString());
            newDoc.addPage(newPage);
            newDoc.save(file);
            return file;

        } catch (IOException e) {
            e.printStackTrace();
            return file;
        }
    }

    public PMDocument resize(PDRectangle newRect) {
        list = list.stream().map(file -> {
            return resize(file, newRect);
        }).collect(Collectors.toList());
        return this;
    }

    public PMDocument combine(boolean turnLeft) throws IOException {
        List<File> newList = new ArrayList<>();
        for (int i = 0; i < list.size(); i += 2) {
            if (i == list.size() - 1) {
                newList.add(list.get(i));
                break;
            }

            PDDocument left = null;
            PDDocument right = null;
            PDDocument newDoc = null;

            try {
                left = PDDocument.load(list.get(i));
                right = PDDocument.load(list.get(i + 1));
                newDoc = new PDDocument();

                PDRectangle leftRect = left.getPage(0).getMediaBox();
                if (leftRect.getHeight() < leftRect.getWidth()) {
                    leftRect = new PDRectangle(leftRect.getHeight(), leftRect.getWidth());
                    left.close();
                    rotate(list.get(i), turnLeft);
                    left = PDDocument.load(list.get(i));
                }
                PDRectangle rightRect = right.getPage(0).getMediaBox();
                if (rightRect.getHeight() < rightRect.getWidth()) {
                    right.close();
                    rotate(list.get(i + 1), turnLeft);
                    right = PDDocument.load(list.get(i + 1));
                }
                resize(list.get(i + 1), leftRect);
                right.close();
                right = PDDocument.load(list.get(i + 1));

                PDPage page = new PDPage(new PDRectangle(leftRect.getWidth() * 2, leftRect.getHeight()));

                LayerUtility util = new LayerUtility(newDoc);

                util.appendFormAsLayer(page, util.importPageAsForm(left, 0), new AffineTransform(),
                        UUID.randomUUID().toString());
                util.appendFormAsLayer(page, util.importPageAsForm(right, 0),
                        AffineTransform.getTranslateInstance(leftRect.getWidth(), 0.0), UUID.randomUUID().toString());
                newDoc.addPage(page);
                File file = getNewTmpFile();
                newDoc.save(file);
                newList.add(file);
            } finally {
                left.close();
                right.close();
                newDoc.close();
            }
        }
        list = Collections.synchronizedList(newList);
        return this;

    }

    /**
     * thread-safe
     */
    public PMDocument addDocument(PMDocument keeper) {
        this.list.addAll(keeper.list);
        return this;
    }

    public List<File> getFileList() {
        return this.list;
    }

    public void build(OutputStream stream) throws IOException {
        PDFMergerUtility util = new PDFMergerUtility();
        list.forEach(file -> {
            try {
                util.addSource(file);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        });
        util.setDestinationStream(stream);
        PDDocumentInformation info = new PDDocumentInformation();
        info.setAuthor(author);
        info.setCreator(creator);
        info.setProducer(producer);
        util.setDestinationDocumentInformation(info);
        util.mergeDocuments(null);
    }
}
