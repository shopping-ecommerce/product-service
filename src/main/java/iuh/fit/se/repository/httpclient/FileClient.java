package iuh.fit.se.repository.httpclient;

import feign.Logger;
import feign.Retryer;
import feign.codec.Encoder;
import feign.form.spring.SpringFormEncoder;
import iuh.fit.se.configuration.AuthenticationRequestInterceptor;
import iuh.fit.se.dto.request.DeleteRequest;
import iuh.fit.se.dto.response.FileClientResponse;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.support.SpringEncoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@FeignClient(name = "file-service", configuration = {FileClient.FormConfig.class, AuthenticationRequestInterceptor.class,FileClient.FeignRetryConfig.class,FileClient.FeignLogConfig.class})
public interface FileClient {
    @PostMapping(value = "/s3/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    FileClientResponse uploadFile(@RequestPart("files") List<MultipartFile> files);

    @PostMapping(value = "/s3/delete", consumes = MediaType.APPLICATION_JSON_VALUE)
    FileClientResponse deleteByUrl(@RequestBody DeleteRequest deleteRequest);


    @Configuration
    class FormConfig {
        @Bean
        public Encoder feignFormEncoder(ObjectFactory<HttpMessageConverters> messageConverters) {
            return new SpringFormEncoder(new SpringEncoder(messageConverters));
        }
    }

    @Configuration
    public class FeignRetryConfig{
        @Bean
        public Retryer feignRetryConfig(){
            return new Retryer.Default(100,1000,3);
        }
    }

    @Configuration
    class FeignLogConfig {
        @Bean
        Logger.Level feignLoggerLevel() {
            return Logger.Level.FULL;
        }
    }
}