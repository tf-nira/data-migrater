package io.mosip.packet.core.dto.packet;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Data
@Getter
@Setter
public class RegistrationIdRequest {
    List<String> rids;
}
