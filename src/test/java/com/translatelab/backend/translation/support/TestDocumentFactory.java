package com.translatelab.backend.translation.support;

import org.apache.poi.xwpf.usermodel.XWPFDocument;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public final class TestDocumentFactory {

    private TestDocumentFactory() {
    }

    public static byte[] docx(String text) {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText(text);
            document.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Не удалось создать тестовый DOCX",
                    exception
            );
        }
    }
}
