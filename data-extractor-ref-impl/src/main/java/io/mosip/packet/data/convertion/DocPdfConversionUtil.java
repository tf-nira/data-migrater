package io.mosip.packet.data.convertion;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;

import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

public class DocPdfConversionUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(DocPdfConversionUtil.class);
    private static final String SCANNER_IMG_TYPE = "jpg";

    public static byte[] asPDF(@NonNull List<BufferedImage> bufferedImages, Float compressionQuality) {
        try (PDDocument pdDocument = new PDDocument();
             ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
            for(BufferedImage bufferedImage : bufferedImages) {
                PDPage pdPage = new PDPage();
                byte[] image = getCompressedImage(bufferedImage, compressionQuality);
                LOGGER.info("image size after compression : {}", image.length);
                PDImageXObject pdImageXObject = PDImageXObject.createFromByteArray(pdDocument, image, "");
                Dimension scaledDimension = getScaledDimension(new Dimension(pdImageXObject.getWidth(), pdImageXObject.getHeight()),
                        new Dimension((int)pdPage.getMediaBox().getWidth(), (int)pdPage.getMediaBox().getHeight()));
                try (PDPageContentStream contentStream = new PDPageContentStream(pdDocument, pdPage)) {
                    float startx = (pdPage.getMediaBox().getWidth() - scaledDimension.width)/2;
                    float starty = (pdPage.getMediaBox().getHeight() - scaledDimension.height)/2;
                    contentStream.drawImage(pdImageXObject, startx, starty, scaledDimension.width, scaledDimension.height);
                }
                pdDocument.addPage(pdPage);
            }
            pdDocument.save(byteArrayOutputStream);
            return byteArrayOutputStream.toByteArray();
        } catch (IOException e) {
            LOGGER.error("Failed to convert bufferedImages to PDF", e);
        }
        return null;
    }

    private static byte[] getCompressedImage(BufferedImage bufferedImage, Float compressionQuality) throws IOException {
        ImageWriter imageWriter = null;
        try(ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            imageWriter = ImageIO.getImageWritersByFormatName(SCANNER_IMG_TYPE).next();
            ImageWriteParam imageWriteParam = imageWriter.getDefaultWriteParam();
            imageWriteParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            imageWriteParam.setCompressionQuality(compressionQuality == null ? 0.7f : compressionQuality);
            imageWriter.setOutput(new MemoryCacheImageOutputStream(bos));
            imageWriter.write(bufferedImage);
            return bos.toByteArray();
        } finally {
            if(imageWriter != null)
                imageWriter.dispose();
        }
    }

    private static Dimension getScaledDimension(Dimension imgSize, Dimension boundary) {
        int original_width = imgSize.width;
        int original_height = imgSize.height;
        int bound_width = boundary.width;
        int bound_height = boundary.height;
        int new_width = original_width;
        int new_height = original_height;

        // first check if we need to scale width
        if (original_width > bound_width) {
            //scale width to fit
            new_width = bound_width;
            //scale height to maintain aspect ratio
            new_height = (new_width * original_height) / original_width;
        }

        // then check if we need to scale even with the new height
        if (new_height > bound_height) {
            //scale height to fit instead
            new_height = bound_height;
            //scale width to maintain aspect ratio
            new_width = (new_height * original_width) / original_height;
        }

        return new Dimension(new_width, new_height);
    }

}
