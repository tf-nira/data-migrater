package io.mosip.packet.manager.util;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

import io.mosip.commons.packet.constants.Biometric;
import io.mosip.commons.packet.constants.PacketManagerConstants;
import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.packet.core.constant.RegistrationConstants;
import io.mosip.packet.core.dto.packet.BiometricsDto;
import io.mosip.packet.core.logger.DataProcessLogger;
import io.mosip.kernel.biometrics.constant.*;
import io.mosip.kernel.biometrics.entities.*;
import io.mosip.kernel.core.util.StringUtils;
import org.springframework.stereotype.Component;

@Component
public class BIRBuilder {

	private static final Logger LOGGER = DataProcessLogger.getLogger(BIRBuilder.class);

	public BIR buildBIR(BiometricsDto bioDto) {
		LOGGER.debug("started building BIR for for bioAttribute : {}", bioDto.getBioAttribute());
		BiometricType biometricType = Biometric.getSingleTypeByAttribute(bioDto.getBioAttribute());
		// Format
		RegistryIDType birFormat = new RegistryIDType();
		birFormat.setOrganization(PacketManagerConstants.CBEFF_DEFAULT_FORMAT_ORG);
		birFormat.setType(String.valueOf(Biometric.getFormatType(biometricType)));

		LOGGER.debug("started building BIR algorithm for for bioAttribute : {}", bioDto.getBioAttribute());

		// Algorithm
		RegistryIDType birAlgorithm = new RegistryIDType();
		birAlgorithm.setOrganization(PacketManagerConstants.CBEFF_DEFAULT_ALG_ORG);
		birAlgorithm.setType(PacketManagerConstants.CBEFF_DEFAULT_ALG_TYPE);

		LOGGER.debug("started building Quality type for for bioAttribute : {}", bioDto.getBioAttribute());

		// Quality Type
		QualityType qualityType = new QualityType();
		qualityType.setAlgorithm(birAlgorithm);
		qualityType.setScore((long) bioDto.getQualityScore());

		VersionType versionType = new VersionType(1, 1);

		String payLoad = null;
		if(bioDto.getAttributeISO()!=null) {
			int bioValueKeyIndex = bioDto.getPayLoad().indexOf(RegistrationConstants.BIOVALUE_KEY) + (RegistrationConstants.BIOVALUE_KEY.length() + 1);
			int bioValueStartIndex = bioDto.getPayLoad().indexOf('"', bioValueKeyIndex);
			int bioValueEndIndex = bioDto.getPayLoad().indexOf('"', (bioValueStartIndex + 1));
			String bioValue = bioDto.getPayLoad().substring(bioValueStartIndex, (bioValueEndIndex + 1));
			payLoad = bioDto.getPayLoad().replace(bioValue, RegistrationConstants.BIOVALUE_PLACEHOLDER);
		}

		return new BIR.BIRBuilder().withBdb(bioDto.getAttributeISO() == null ? new byte[0] : bioDto.getAttributeISO())
				.withVersion(versionType)
				.withCbeffversion(versionType)
				.withBirInfo(new BIRInfo.BIRInfoBuilder().withIntegrity(false).build())
				.withBdbInfo(new BDBInfo.BDBInfoBuilder().withFormat(birFormat).withQuality(qualityType)
						.withType(Arrays.asList(biometricType)).withSubtype(getSubTypes(biometricType, bioDto.getBioAttribute()))
						.withPurpose(PurposeType.ENROLL).withLevel(ProcessedLevelType.RAW)
						.withCreationDate(LocalDateTime.now(ZoneId.of("UTC"))).withIndex(UUID.randomUUID().toString())
						.build())
				.withSb(bioDto.getSignature() == null ? new byte[0] : bioDto.getSignature().getBytes(StandardCharsets.UTF_8))
				.withOthers(OtherKey.EXCEPTION, bioDto.getAttributeISO()==null ? "true" : "false")
				.withOthers(OtherKey.RETRIES, bioDto.getNumOfRetries()+RegistrationConstants.EMPTY)
				.withOthers(OtherKey.SDK_SCORE, bioDto.getSdkScore()+RegistrationConstants.EMPTY)
				.withOthers(OtherKey.FORCE_CAPTURED, bioDto.isForceCaptured()+RegistrationConstants.EMPTY)
				.withOthers(OtherKey.PAYLOAD, payLoad == null ? RegistrationConstants.EMPTY : payLoad)
				.withOthers(OtherKey.SPEC_VERSION, bioDto.getSpecVersion() == null ? RegistrationConstants.EMPTY : bioDto.getSpecVersion())
				.withOthers("NOT_AVAILABLE", bioDto.isNotAvailable() ? "true" : "false")
				.withOthers("REMARKS", bioDto.getRemarks())
				.build();

/*		return new BIR.BIRBuilder().withBdb(bioData == null ? new byte[0] : bioData)
				.withVersion(versionType)
				.withCbeffversion(versionType)
				.withBirInfo(new BIRInfo.BIRInfoBuilder().withIntegrity(false).build())
				.withBdbInfo(new BDBInfo.BDBInfoBuilder().withFormat(birFormat).withQuality(qualityType)
						.withType(Arrays.asList(biometricType)).withSubtype(getSubTypes(biometricType, bioAttribute))
						.withPurpose(PurposeType.ENROLL).withLevel(ProcessedLevelType.RAW)
						.withCreationDate(LocalDateTime.now(ZoneId.of("UTC"))).withIndex(UUID.randomUUID().toString())
						.build())
				.withSb(new byte[0]) // .withSb(bioDto.getSignature() == null ? new byte[0] : bioDto.getSignature().getBytes(StandardCharsets.UTF_8))
				.withOthers(OtherKey.EXCEPTION, bioData==null ? "true" : "false")
				.withOthers(OtherKey.RETRIES, bioData==null ? "0" : "1") //.withOthers(OtherKey.RETRIES, bioDto.getNumOfRetries()+ RegistrationConstants.EMPTY)
				.withOthers(OtherKey.SDK_SCORE, "0.0") //.withOthers(OtherKey.SDK_SCORE, bioDto.getSdkScore()+RegistrationConstants.EMPTY)
				.withOthers(OtherKey.FORCE_CAPTURED, "false") //.withOthers(OtherKey.FORCE_CAPTURED, bioDto.isForceCaptured()+RegistrationConstants.EMPTY)
				.withOthers(OtherKey.PAYLOAD, payLoad == null ? RegistrationConstants.EMPTY : payLoad)
				.withOthers(OtherKey.SPEC_VERSION, bioSpecVaersion) //.withOthers(OtherKey.SPEC_VERSION, bioDto.getSpecVersion() == null ? RegistrationConstants.EMPTY : bioDto.getSpecVersion())
				.build();*/
	}

	private List<String> getSubTypes(BiometricType biometricType, String bioAttribute) {
		List<String> subtypes = new LinkedList<>();
		switch (biometricType) {
		case FINGER:
			subtypes.add(bioAttribute.contains("left") ? SingleAnySubtypeType.LEFT.value()
					: SingleAnySubtypeType.RIGHT.value());
			if (bioAttribute.toLowerCase().contains("thumb"))
				subtypes.add(SingleAnySubtypeType.THUMB.value());
			else {
				String val = bioAttribute.toLowerCase().replace("left", "").replace("right", "");
				subtypes.add(SingleAnySubtypeType.fromValue(StringUtils.capitalizeFirstLetter(val).concat("Finger"))
						.value());
			}
			break;
		case IRIS:
			subtypes.add(bioAttribute.contains("left") ? SingleAnySubtypeType.LEFT.value()
					: SingleAnySubtypeType.RIGHT.value());
			break;

		case EXCEPTION_PHOTO:
		case FACE:
			break;
		}
		return subtypes;
	}

}
