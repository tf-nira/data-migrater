package io.mosip.packet.core.util.regclient;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.packet.core.entity.RegistrationCommonFields;
import io.mosip.packet.core.logger.DataProcessLogger;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import io.mosip.kernel.core.dataaccess.exception.DataAccessLayerException;
import io.mosip.kernel.core.util.DateUtils;


/**
 * MetaDataUtils class provide methods to copy values from DTO to entity along
 * with that it create some meta data which is required before an entity to be
 * saved into database.
 * 
 * @author Sreekar Chukka
 * @since 1.0.0
 * @see MapperUtils
 *
 */
@Component
@SuppressWarnings("unchecked")
public class MetaDataUtils {

	private static final Logger LOGGER = DataProcessLogger.getLogger(MetaDataUtils.class);


	private static String contextUser = "BATCH";

	private MetaDataUtils() {
		super();
	}

	/**
	 * This method takes <code>source</code> object like an DTO and a class which
	 * must extends {@link RegistrationCommonFields} and map all values from DTO
	 * object to the <code>destination</code> object and return it.
	 *
	 * @param <S>           the generic type
	 * @param <D>           the generic type
	 * @param source        is the source
	 * @param destination   is the destination
	 * @param mapNullvalues if marked as false then field inside source which are
	 *                      null will not be mapped into destination
	 * @return an entity class which extends {@link RegistrationCommonFields}
	 * @throws DataAccessLayerException if any error occurs while mapping values
	 * @see MapperUtils#map(Object, Object, Boolean)
	 */
	public static <S, D extends RegistrationCommonFields> D setUpdateMetaData(final S source, D destination,
			Boolean mapNullvalues) {

		D entity = MapperUtils.map(source, destination, mapNullvalues);

		setUpdatedDateTime(contextUser, entity);
		return entity;
	}

	/**
	 * This method takes <code>source</code> object like an DTO and a class which
	 * must extends {@link RegistrationCommonFields} and map all values from DTO
	 * object to the <code>destinationClass</code> object and return it.
	 * 
	 * @param <T>              is a type parameter
	 * @param <D>              is a type parameter
	 * @param source           is the source
	 * @param destinationClass is the destination class
	 * @return an entity class which extends {@link RegistrationCommonFields}
	 * @throws DataAccessLayerException if any error occurs while mapping values
	 * @see MapperUtils#map(Object, Class)
	 */
	public static <T, D extends RegistrationCommonFields> D setCreateMetaData(final T source,
			Class<? extends RegistrationCommonFields> destinationClass) {

		D entity = (D) MapperUtils.map(source, destinationClass);

		setCreatedDateTime(contextUser, entity);
		return entity;
	}

	/**
	 * Sets the create meta data.
	 *
	 * @param <T>         the generic type
	 * @param <D>         the generic type
	 * @param dtoList     the dto list
	 * @param entityClass the entity class
	 * @return the list
	 */
	public static <T, D extends RegistrationCommonFields> List<D> setCreateMetaData(final Collection<T> dtoList,
																					Class<? extends RegistrationCommonFields> entityClass) {

		List<D> entities = new ArrayList<>();

		if (null != dtoList && !dtoList.isEmpty()) {
			for (T dto : dtoList) {
				D entity = (D) MapperUtils.map(dto, entityClass);
				setCreatedDateTime(contextUser, entity);
				entities.add(entity);
			}
		}

		return entities;

	}

	/**
	 * Sets the created date time.
	 *
	 * @param <D>         the generic type
	 * @param contextUser the context user
	 * @param entity      the entity
	 */
	private static <D extends RegistrationCommonFields> void setCreatedDateTime(String contextUser, D entity) {
		entity.setCrDtime(Timestamp.valueOf(DateUtils.getUTCCurrentDateTime()));
		entity.setCrBy(contextUser);
	}

	/**
	 * Sets the updated date time.
	 *
	 * @param <D>         the generic type
	 * @param contextUser the context user
	 * @param entity      the entity
	 */
	private static <D extends RegistrationCommonFields> void setUpdatedDateTime(String contextUser, D entity) {
		entity.setUpdDtimes(Timestamp.valueOf(DateUtils.getUTCCurrentDateTime()));
		entity.setUpdBy(contextUser);
	}

	/**
	 * This method takes <code>source</code> JSONObject and a class which must
	 * extends {@link RegistrationCommonFields} and map all values from Json object
	 * to the <code>destinationClass</code> object and return it.
	 * 
	 * @param <T>              is a type parameter
	 * @param <D>              is a type parameter
	 * @param jsonObject       is the source
	 * @param entityClass is the destination class
	 * @return an entity class which extends {@link RegistrationCommonFields}
	 * @throws Throwable
	 * @throws DataAccessLayerException if any error occurs while mapping values
	 */
	public static <T, D extends RegistrationCommonFields> D setCreateJSONObjectToMetaData(final JSONObject jsonObject,
			Class<?> entityClass) throws Throwable {
		D entity = null;
		try {
			entity = (D) MapperUtils.mapJSONObjectToEntity(jsonObject, entityClass);
		} catch (Throwable t) {
			LOGGER.error(t.getMessage(), t);
			throw t;
		}
		setCreatedDateTime(contextUser, entity);
		return entity;
	}

}