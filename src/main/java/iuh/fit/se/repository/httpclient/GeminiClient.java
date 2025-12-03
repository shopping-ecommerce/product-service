// src/main/java/iuh/fit/se/repository/httpclient/GeminiClient.java
package iuh.fit.se.repository.httpclient;

import feign.codec.Encoder;
import feign.form.spring.SpringFormEncoder;
import iuh.fit.se.configuration.FeignConfig;
import iuh.fit.se.dto.request.*;
import iuh.fit.se.dto.response.*;
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

@FeignClient(
        name = "gemini-service",
        configuration = {FileClient.FormConfig.class, FeignConfig.class}  // Thêm FeignConfig
)
public interface GeminiClient {
    // POST /index/index-single-product
    @PostMapping(value = "/index/index-single-product", consumes = MediaType.APPLICATION_JSON_VALUE)
    IndexOperationResponse indexSingleProduct(@RequestBody IndexSingleProductRequest request);

    // POST /index/remove-single-product
    @PostMapping(value = "/index/remove-single-product", consumes = MediaType.APPLICATION_JSON_VALUE)
    IndexOperationResponse removeSingleProduct(@RequestBody RemoveSingleProductRequest request);

    // POST /index/upsert-single-product
    @PostMapping(value = "/index/upsert-single-product", consumes = MediaType.APPLICATION_JSON_VALUE)
    IndexOperationResponse upsertSingleProduct(@RequestBody UpsertSingleProductRequest request);

    // Index tất cả ảnh của 1 product
    @PostMapping(value = "/index/index-single-product-images", consumes = MediaType.APPLICATION_JSON_VALUE)
    IndexImagesOperationResponse indexSingleProductImages(@RequestBody IndexSingleProductImagesRequest request);

    // Remove toàn bộ image-embeddings của 1 product
    @PostMapping(value = "/index/remove-product-images", consumes = MediaType.APPLICATION_JSON_VALUE)
    RemoveImagesOperationResponse removeProductImages(@RequestBody RemoveProductImagesRequest request);

    // Upsert 1 ảnh — JSON mode (product_id, position, image_url)
    @PostMapping(value = "/index/upsert-single-image", consumes = MediaType.APPLICATION_JSON_VALUE)
    UpsertSingleImageResponse upsertSingleImageJson(@RequestBody UpsertSingleImageJsonRequest request);

    // Upsert 1 ảnh — multipart mode (product_id, position, image)
    @PostMapping(value = "/index/upsert-single-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    UpsertSingleImageResponse upsertSingleImageUpload(
            @RequestPart("product_id") String productId,
            @RequestPart("position") Integer position,
            @RequestPart("image") MultipartFile image
    );

    // Remove 1 ảnh — dùng datapoint_id hoặc (product_id + position)
    @PostMapping(value = "/index/remove-single-image", consumes = MediaType.APPLICATION_JSON_VALUE)
    BasicOperationResponse removeSingleImage(@RequestBody RemoveSingleImageRequest request);
}