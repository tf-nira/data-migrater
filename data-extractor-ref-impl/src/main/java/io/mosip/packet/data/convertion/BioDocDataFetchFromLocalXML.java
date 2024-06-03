package io.mosip.packet.data.convertion;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mosip.kernel.core.util.CryptoUtil;
import io.mosip.packet.core.spi.BioDocApiFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.annotation.PostConstruct;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;


@Component
@ConditionalOnProperty(value = "mosip.packet.bio.doc.data.converter.classname", havingValue = "BioDocDataFetchFromLocalXML")
public class BioDocDataFetchFromLocalXML implements BioDocApiFactory {

    private static final Logger logger = LoggerFactory.getLogger(BioDocDataFetchFromLocalXML.class);

    /*@Value("${mosip.packet.bio.doc.data.converter.filepath}")
    private String filePath;*/

    @Value("${mosip.packet.image.modality.file.mapping}")
    private String modalityFileMappingJson;

    private Map<String, String> modalityFileMapping;

    @PostConstruct
    public void init() throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        modalityFileMapping = objectMapper.readValue(modalityFileMappingJson, new TypeReference<Map<String, String>>() {});
    }

    private final String FINGER = "FINGER_POS_";
    private final String IRIS = "IRIS_POS_";
    private final String PHOTO = "PHOTO";

    @Override
    public Map<String, byte[]> getBioData(byte[] byteval, String fieldName) throws Exception {
        /*File file = new File(new String(byteval, StandardCharsets.UTF_8));
        String value = new String(byteval, StandardCharsets.UTF_8);
        FileInputStream is = new FileInputStream(file);
        Map<String, byte[]> map = new HashMap<>();
        map.put(fieldName, is.readAllBytes());
        return map;*/
        Map<String, byte[]> map = new HashMap<>();

        String filepath = new String(byteval, StandardCharsets.UTF_8);
        File file = new File(filepath);

        if (!file.exists() || !file.isFile()) {
            logger.error("File not found or is not afile: {}", filepath);
            throw new RuntimeException("File not found: " + filepath);
        }
        try {
            //parsing xml file
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(file);
            document.getDocumentElement().normalize();

            //For fingerprint xml data processing
            NodeList highResGrayImageRecords = document.getElementsByTagName("itl:PackageHighResolutionGrayscaleImageRecord");
            for (int i = 0; i < highResGrayImageRecords.getLength(); i++) {
                Node node = highResGrayImageRecords.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    Element element = (Element) node;

                    String binaryBase64Object = getElementValue(element, "nc:BinaryBase64Object");
                    String fingerPositionCode = getElementValue(element, "biom:FingerPositionCode");
                    String key = modalityFileMapping.get(FINGER + fingerPositionCode);
                    map.put(key, CryptoUtil.decodePlainBase64(binaryBase64Object));
                }
            }

            //For Iris xml data processing
            NodeList irisImageRecords = document.getElementsByTagName("itl:PackageIrisImageRecord");
            for (int i = 0; i < irisImageRecords.getLength(); i++) {
                Node node = irisImageRecords.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    Element element = (Element) node;

                    String binaryBase64Object = getElementValue(element, "nc:BinaryBase64Object");
                    String irisEyePositionCode = getElementValue(element, "biom:IrisEyePositionCode");
                    String key = modalityFileMapping.get(IRIS + irisEyePositionCode);
                    map.put(key, CryptoUtil.decodePlainBase64(binaryBase64Object));
                }
            }

            //For Face Image xml data processing
            NodeList userDefinedImageRecords = document.getElementsByTagName("itl:PackageUserDefinedImageRecord");
            for (int i = 0; i < userDefinedImageRecords.getLength(); i++) {
                Node node = userDefinedImageRecords.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    Element element = (Element) node;

                    String binaryBase64Object = getElementValue(element, "nc:BinaryBase64Object");
                    String key = modalityFileMapping.get(PHOTO);
                    map.put(key, CryptoUtil.decodePlainBase64(binaryBase64Object));
                    break;
                }
            }
        } catch (Exception e) {
            logger.error("Error processing XML file: {}", filepath, e);
            throw new RuntimeException("Error processing XML file: " + filepath, e);
        }
        return map;
    }

    @Override
    public Map<String, byte[]> getDocData(byte[] byteval, String fieldName) {
        throw new UnsupportedOperationException("getBioData is not supported for this requirement, use getDocData method instead");
    }

    private String getElementValue(Element parent, String tagName) {
        NodeList nodeList = parent.getElementsByTagName(tagName);
        if (nodeList.getLength() > 0) {
            Node node = nodeList.item(0);
            return node.getTextContent();
        }
        return "";
    }
}
