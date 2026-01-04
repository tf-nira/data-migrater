package io.mosip.packet.core.repository;

import io.mosip.kernel.core.dataaccess.spi.repository.BaseRepository;
import io.mosip.packet.core.entity.PacketTracker;

import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PacketTrackerRepository extends BaseRepository<PacketTracker, String> {

    List<PacketTracker> findByStatusIn(List<String> statusList);
    
    @Query(value = "SELECT * FROM mosip.packet_tracker WHERE status IN (:statusList) FETCH FIRST :limit ROWS ONLY", nativeQuery = true)
    List<PacketTracker> findByStatusInWithLimit(@Param("statusList") List<String> statusList, @Param("limit") int limit);
}
