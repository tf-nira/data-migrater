package io.mosip.packet.data.convertion;

import io.mosip.packet.core.spi.BioDocApiFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(value = "mosip.packet.bio.doc.data.converter.classname", havingValue = "BioDocDataFetchFromFile")
public class BioDocDataFetchFromLocal implements BioDocApiFactory {

    private static final Logger logger = LoggerFactory.getLogger(BioDocDataFetchFromLocal.class);

    @Override
    public Map<String, byte[]> getBioData(byte[] byteval, String fieldName) throws Exception {

        Map<String, byte[]> map = new HashMap<>();
        map.put(fieldName, getBioFileByteArray(byteval));
        return map;
    }

    @Override
    public Map<String, byte[]> getDocData(byte[] byteval, String fieldName) throws Exception {

        Map<String, byte[]> map = new HashMap<>();
        List<BufferedImage> bufferedImages = convertDocFiles(byteval);
        byte[] docPdfBytes = DocPdfConversionUtil.asPDF(bufferedImages, null);
        map.put(fieldName, docPdfBytes);
        return map;
    }

    private byte[] getBioFileByteArray(byte[] byteval) throws Exception {

        if (byteval != null) {
            String filepath = new String(byteval, StandardCharsets.UTF_8);
            filepath = filepath.replaceAll("'","");
            File inputFile = new File(filepath);
            if (inputFile != null && inputFile.isFile()) {
                return new FileInputStream(inputFile).readAllBytes();
            }
            logger.info("Biometric file not found or not a file: {}", filepath);
        }
        return null;
    }

    private static List<BufferedImage> convertDocFiles(byte[] byteval) throws Exception {

        List<BufferedImage> bufferedImageList = new ArrayList<>();
        if (byteval != null) {
            String filepath = new String(byteval, StandardCharsets.UTF_8);
            String[] filepaths = filepath.replaceAll("'","").split(",");
            if (filepaths.length == 2) {
                BufferedImage image1 = getBufferedImage(filepaths[0]);
                if (image1 != null) bufferedImageList.add(image1);
                BufferedImage image2 = getBufferedImage(filepaths[1]);
                if (image2 != null) bufferedImageList.add(image2);
            } else if(filepaths.length == 1) {
                BufferedImage image1 = getBufferedImage(filepaths[0]);
                if (image1 != null) bufferedImageList.add(image1);
            }
        }
        return bufferedImageList;
    }

    private static BufferedImage getBufferedImage(String filepath) throws IOException {
        File inputFile = new File(filepath);
        if (inputFile != null && inputFile.isFile()) {
            return ImageIO.read(inputFile);
        }
        logger.info("Document file not found or not a file: {}", filepath);
        return null;
    }
}