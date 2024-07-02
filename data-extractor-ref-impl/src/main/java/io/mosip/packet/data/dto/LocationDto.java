package io.mosip.packet.data.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LocationDto {

	private String code;

	private String name;

	private short hierarchyLevel;

	private String hierarchyName;

	private String parentLocCode;

	private String langCode;

	private Boolean isActive;
}
