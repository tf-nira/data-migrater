package io.mosip.packet.core.dto;

import java.util.Map;

import io.mosip.commons.packet.dto.Document;
import lombok.Data;

@Data
public class NINDetailsResponseDto {
	private Map<String, String> demographics;
	private Map<String, Document> documents;
}
