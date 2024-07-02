package io.mosip.packet.data.service;

import io.mosip.packet.core.dto.ResponseWrapper;
import io.mosip.packet.core.exception.ApisResourceAccessException;
import io.mosip.packet.data.dto.IdRequestDto;

import java.util.Map;

public interface ImportIdentityService {

    ResponseWrapper importIdentity(IdRequestDto idRequestDto, Map<String, Object> demoDetails) throws ApisResourceAccessException;
}
