package iuh.fit.se.service.impl;

import iuh.fit.se.dto.request.DeleteRequest;
import iuh.fit.se.dto.request.ProductRequest;
import iuh.fit.se.dto.request.ProductUpdateRequest;
import iuh.fit.se.dto.request.SearchSizeAndIDRequest;
import iuh.fit.se.dto.response.FileClientResponse;
import iuh.fit.se.dto.response.OrderItemProductResponse;
import iuh.fit.se.dto.response.ProductResponse;
import iuh.fit.se.entity.Product;
import iuh.fit.se.entity.enums.Status;
import iuh.fit.se.entity.records.Image;
import iuh.fit.se.entity.records.Size;
import iuh.fit.se.exception.AppException;
import iuh.fit.se.exception.ErrorCode;
import iuh.fit.se.mapper.ProductMapper;
import iuh.fit.se.repository.ProductRepository;
import iuh.fit.se.repository.httpclient.FileClient;
import iuh.fit.se.service.ProductService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequiredArgsConstructor
@Slf4j
public class ProductServiceImpl implements ProductService {
    ProductRepository productRepository;
    ProductMapper productMapper;
    FileClient fileClient;
    @Override
    public ProductResponse findById(String id) {
        Product product = productRepository.findById(id).orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));
        return productMapper.toProductResponse(product);
    }

    @Override
    public ProductResponse createProduct(ProductRequest request, List<MultipartFile> images) {
        // 1. Upload ảnh đến AWS qua file-service
        if(images == null || images.isEmpty()) {
            throw new AppException(ErrorCode.UPLOAD_FILE_FAILED);
        }
        FileClientResponse fileClientResponse = fileClient.uploadFile(images);
        List<String> uploadedUrls = fileClientResponse.getResult();

        if (uploadedUrls.isEmpty()) {
            throw new AppException(ErrorCode.FILE_PROCESSING_ERROR);
        }

        // 2. Ghép URL vào position từ request
        List<Image> finalImages = new ArrayList<>();
        for (int i = 0; i < uploadedUrls.size(); i++) {
            finalImages.add(Image.builder()
                            .url((uploadedUrls.get(i)))
                            .position(i+1)
                    .build());
        }

        // 3. Map DTO -> Entity
        Product product = productMapper.toProduct(request);
        product.setImages(finalImages);
        product.setStatus(Status.AVAILABLE);

        // 4. Lưu DB
        product = productRepository.save(product);

        return productMapper.toProductResponse(product);
    }

    @Override
    @Transactional
    public ProductResponse updateProduct(ProductUpdateRequest request, List<MultipartFile> images) {
        Product product = productRepository.findById(request.getId())
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));

        // === 0) Lọc file hợp lệ (tránh gọi file-service khi rỗng) ===
        List<MultipartFile> validFiles = (images == null) ? List.of()
                : images.stream().filter(f -> f != null && !f.isEmpty() && f.getSize() > 0).toList();

        log.info("Update product {}, incomingFiles={}, validFiles={}",
                request.getId(), images == null ? 0 : images.size(), validFiles.size());

        // === 1) Lấy & chuẩn hoá position 1..n hiện tại ===
        List<Image> working = new ArrayList<>(product.getImages() != null ? product.getImages() : List.of());
        // sort theo position (null đưa xuống cuối)
        working.sort((a, b) -> {
            int pa = a.position() == null ? Integer.MAX_VALUE : a.position();
            int pb = b.position() == null ? Integer.MAX_VALUE : b.position();
            return Integer.compare(pa, pb);
        });
        List<Image> normalized = new ArrayList<>(working.size());
        for (int i = 0; i < working.size(); i++) {
            Image img = working.get(i);
            normalized.add(Image.builder()
                    .url(img.url())
                    .position(i + 1)
                    .build());
        }
        working = normalized;

        // === 2) Xoá theo position (nếu có) ===
        if (request.getRemoveImage() != null && !request.getRemoveImage().isEmpty()) {
            Set<Integer> removePositions = new HashSet<>(request.getRemoveImage());

            int size = working.size();
            for (Integer p : removePositions) {
                if (p == null || p < 1 || p > size) {
                    throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
                }
            }
            List<Image> kept = new ArrayList<>();
            List<String> removeImages  = new ArrayList<>();
            for (Image img : working) {
                if (!removePositions.contains(img.position())) {
                    kept.add(img);
                }else{
                    removeImages.add(img.url());
                }
            }
            log.info("Removing {} image(s) from product {}, kept {} images",
                    removeImages.getFirst(), product.getId(), kept.size());
            // Gọi file-service để xoá ảnh
            FileClientResponse fileClientResponse = fileClient.deleteByUrl(DeleteRequest.builder()
                    .urls(removeImages)
                    .build());
            log.info("Deleted image from product:", fileClientResponse.getMessage());

            working = kept;
        }

        // === 3) Upload & append ảnh mới vào cuối (nếu có) ===
        if (!validFiles.isEmpty()) {
            FileClientResponse up = fileClient.uploadFile(validFiles);
            List<String> urls = (up == null) ? null : up.getResult();
            if (urls == null || urls.isEmpty()) {
                throw new AppException(ErrorCode.FILE_PROCESSING_ERROR);
            }

            int nextPos = working.size() + 1;
            for (String url : urls) {
                working.add(Image.builder().url(url).position(nextPos++).build());
            }
        }

        // === 4) Chuẩn hoá lại position 1..n lần cuối ===
        List<Image> finalImages = new ArrayList<>(working.size());
        for (int i = 0; i < working.size(); i++) {
            finalImages.add(Image.builder()
                    .url(working.get(i).url())
                    .position(i + 1)
                    .build());
        }
        product.setImages(finalImages);

        // === 5) Partial update các field khác ===
        if (request.getName() != null) product.setName(request.getName());
        if (request.getDescription() != null) product.setDescription(request.getDescription());
        if (request.getStatus() != null) product.setStatus(request.getStatus());
        if (request.getCategoryId() != null) product.setCategoryId(request.getCategoryId());
        if (request.getSizes() != null) product.setSizes(request.getSizes());
        if (request.getPercentDiscount() != null) product.setPercentDiscount(request.getPercentDiscount());

        return productMapper.toProductResponse(productRepository.save(product));
    }

    @Override
    public void deleteProduct(String id) {
        Product product = productRepository.findById(id).orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));
        product.builder().status(Status.OUT_OF_STOCK);
        productRepository.save(product);
    }

    @Override
    public List<ProductResponse> findAllByCategory(String category) {
        List<Product> product = productRepository.findByCategoryId(category);

        return product.stream()
                .map(p -> productMapper.toProductResponse(p))
                .collect(Collectors.toList());
    }

    @Override
    public List<ProductResponse> findAllProducts() {
        return productRepository.findAll().stream().map(p-> productMapper.toProductResponse(p))
                .collect(Collectors.toList());
    }

    @Override
    public List<ProductResponse> findAllBySellerId(String sellerId) {
        return productRepository.findBySellerId(sellerId).stream()
                .map(productMapper::toProductResponse)
                .collect(Collectors.toList());
    }

    @Override
    public OrderItemProductResponse findByIdAndSize(SearchSizeAndIDRequest request) {
        Product product = productRepository.findById(request.getId())
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));

        Size selectedSize = product.getSizes().stream()
                .filter(s -> s.size().equals(request.getSize()))
                .findFirst()
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));

        return OrderItemProductResponse.builder()
                .productId(product.getId())
                .sellerId(product.getSellerId())
                .name(product.getName())
                .image(product.getImages() != null && !product.getImages().isEmpty()
                        ? product.getImages().get(0).url() : null)
                .size(selectedSize.size())
                .price(selectedSize.price())
                .compareAtPrice(selectedSize.compareAtPrice())
                .available(selectedSize.available())
                .stock(selectedSize.quantity())
                .status(product.getStatus().name())
                .build();
    }
}