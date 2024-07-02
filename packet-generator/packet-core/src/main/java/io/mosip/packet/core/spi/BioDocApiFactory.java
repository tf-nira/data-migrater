package io.mosip.packet.core.spi;

import java.util.Map;

public interface BioDocApiFactory {
    public Map<String, byte[]> getBioData(byte[] byteval, String fieldName) throws Exception;
    public Map<String, byte[]> getDocData(byte[] byteval, String fieldName) throws Exception;
}
