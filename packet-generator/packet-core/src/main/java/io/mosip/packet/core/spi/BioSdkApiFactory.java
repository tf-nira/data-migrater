package io.mosip.packet.core.spi;

import io.mosip.packet.core.dto.biosdk.BioSDKRequestWrapper;
import io.mosip.packet.core.dto.biosdk.QualityCheckRequest;

public interface BioSdkApiFactory {
    public Double calculateBioQuality(BioSDKRequestWrapper bioSDKRequestWrapper, String trackerRefid) throws Exception;
}
