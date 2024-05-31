package io.mosip.packet.extractor;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import io.mosip.kernel.dataaccess.hibernate.config.HibernateDaoConfig;
import io.mosip.kernel.dataaccess.hibernate.repository.impl.HibernateRepositoryImpl;
import io.mosip.packet.core.dto.RequestWrapper;
import io.mosip.packet.core.dto.dbimport.DBImportRequest;
import io.mosip.packet.core.dto.dbimport.PacketCreatorResponse;
import io.mosip.packet.extractor.service.DataExtractionService;
import io.mosip.packet.extractor.util.ConfigUtil;
import io.mosip.packet.extractor.util.Reprocessor;
import io.mosip.packet.manager.util.mock.sbi.devicehelper.MockDeviceUtil;
import org.apache.commons.lang.RandomStringUtils;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.io.FileInputStream;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

import static io.mosip.packet.core.constant.GlobalConfig.*;

@SpringBootApplication(scanBasePackages = { "io.mosip.packet.*", "${mosip.auth.adapter.impl.basepackage}", "io.mosip.kernel.clientcrypto.*", "io.mosip.kernel.dataaccess", "io.mosip.kernel.keymanagerservice.*", "io.mosip.kernel.biometrics.*","io.mosip.kernel.cbeffutil.*"}, exclude = {SecurityAutoConfiguration.class, HibernateDaoConfig.class, HibernateJpaAutoConfiguration.class})
@EntityScan(basePackages = {"io.mosip.packet.core.entity", "io.mosip.kernel.idgenerator.rid.entity", "io.mosip.kernel.keymanagerservice.entity"})
@EnableJpaRepositories(basePackages = {"io.mosip.packet.core.repository", "io.mosip.kernel.idgenerator.rid.repository", "io.mosip.kernel.keymanagerservice.repository"} , repositoryBaseClass = HibernateRepositoryImpl.class)
public class DataProcessApplication {

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(DataProcessApplication.class, args);
        try {
            boolean internal = Boolean.parseBoolean(context.getEnvironment().getProperty("mosip.packet.creator.refer.internal.json.file"));
            if(context.getEnvironment().getProperty("mosip.extractor.enable.quality.check.only") != null)
                IS_ONLY_FOR_QUALITY_CHECK = Boolean.parseBoolean(context.getEnvironment().getProperty("mosip.extractor.enable.quality.check.only"));
            if(context.getEnvironment().getProperty("mosip.biometric.sdk.provider.write.sdk.response") != null)
                WRITE_BIOSDK_RESPONSE = Boolean.parseBoolean(context.getEnvironment().getProperty("mosip.biometric.sdk.provider.write.sdk.response"));
            if(context.getEnvironment().getProperty("mosip.packet.creator.tracking.required") != null)
                IS_TRACKER_REQUIRED = Boolean.parseBoolean(context.getEnvironment().getProperty("mosip.packet.creator.tracking.required"));
            if(context.getEnvironment().getProperty("mosip.packet.creator.run.as.batch.execution") != null)
                IS_RUNNING_AS_BATCH = Boolean.parseBoolean(context.getEnvironment().getProperty("mosip.packet.creator.run.as.batch.execution"));
            if(context.getEnvironment().getProperty("mosip.packet.creator.use.existing.session.key") != null)
                SESSION_KEY = context.getEnvironment().getProperty("mosip.packet.creator.use.existing.session.key");
            else
                SESSION_KEY = RandomStringUtils.randomAlphanumeric(20);

            Boolean isReprocessEnable = true;
            if(context.getEnvironment().getProperty("mosip.packet.creator.reprocessor.enable") != null)
                isReprocessEnable = Boolean.parseBoolean(context.getEnvironment().getProperty("mosip.packet.creator.reprocessor.enable"));

            context.getBean(MockDeviceUtil.class).resetDevices();
            context.getBean(MockDeviceUtil.class).initDeviceHelpers();
            context.getBean(ConfigUtil.class).loadConfigDetails();
            if(isReprocessEnable)
                context.getBean(Reprocessor.class).reprocess();

            if(internal) {
                System.out.println("Current Session Key is " + SESSION_KEY + ". Please Enter New Session Key in-case Change.");
                String sessionKey = "";

                if(!IS_RUNNING_AS_BATCH) {
                    Scanner scanner = new Scanner(System.in);
                    sessionKey = scanner.next();
                    SESSION_KEY = sessionKey.trim().toUpperCase();
                }

                System.out.println("Current Flow Enabled for  " + (IS_ONLY_FOR_QUALITY_CHECK ? "Quality Calculation" : "Packet Creation") + " . Do you want to Continue (Y-Yes, N-No)");
                String option = "";

                if(!IS_RUNNING_AS_BATCH) {
                    Scanner scanner = new Scanner(System.in);
                    option = scanner.next();
                } else {
                    option = "Y";
                }

                if(option.equalsIgnoreCase("Y")) {
                    FileInputStream io = new FileInputStream("./ApiRequest.json");
                    String requestJson = new String(io.readAllBytes(), StandardCharsets.UTF_8);
                    ObjectMapper mapper = new ObjectMapper();
                    RequestWrapper<DBImportRequest> request = mapper.readValue(requestJson, new TypeReference<RequestWrapper<DBImportRequest>>() {});
        //            System.out.println("Request : " + (new Gson()).toJson(request));
                    PacketCreatorResponse response =  context.getBean(DataExtractionService.class).createPacketFromDataBase(request.getRequest());
        //            System.out.println("Response : " + (new Gson()).toJson(response));
                }

  // TODO Temporary removing this Need uncomment
                //  if(!IS_RUNNING_AS_BATCH)
                    System.exit(0);
            }
        } catch (UnknownHostException e) {
            e.printStackTrace();
            System.exit(0);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(0);
        }
    }
}
