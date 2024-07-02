package io.mosip.packet.data.service;

import io.mosip.packet.core.dto.ResponseWrapper;
import io.mosip.packet.data.dto.IdRequestDto;

public interface ImportIdentityService {

    ResponseWrapper importIdentity(IdRequestDto idRequestDto);
}
