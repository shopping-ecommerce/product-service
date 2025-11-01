# Product-Service - Quản Lý Sản Phẩm & Kho Hàng

[![Build Status](https://img.shields.io/badge/build-passing-brightgreen.svg)](https://github.com/shopping-ecommerce/product-service/actions) [![Coverage](https://img.shields.io/badge/coverage-95%25-brightgreen.svg)](https://codecov.io/gh/shopping-ecommerce/product-service) [![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE) [![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-green.svg)](https://spring.io/projects/spring-boot) [![Java](https://img.shields.io/badge/Java-17%2B-orange.svg)](https://openjdk.org/)

## 📋 Mô Tả
Product-Service là một microservice backend quản lý sản phẩm, categories, và kho hàng cho ứng dụng e-commerce. Xây dựng bằng **Spring Boot 3.x**, sử dụng **MongoDB** làm database chính (product_db), **Elasticsearch** cho search/indexing, và **Kafka** cho event-driven (order created/updated/cancelled, product invalid). Service hỗ trợ CRUD categories, search sản phẩm (by ID/seller/size/category), update stock từ order events, và batch cleanup discontinued products (xóa vĩnh viễn sau 30 ngày, integrate File/Gemini/Review services).

Dự án tập trung vào performance (ES search, Kafka concurrency=1), bảo mật (JWT public endpoints cho search), và maintainability (scheduled cleanup với configurable cron).

### 🏗️ Architecture
Kiến trúc microservices với Product-Service làm core cho catalog & inventory. Các thành phần chính:
- **Communication**: REST API, Feign clients (File, User, Gemini, Review), Kafka (consume order events, publish invalid?).
- **Database**: MongoDB (JPA-like), Elasticsearch (search/suggest).
- **Security**: JWT (OAuth2), public search endpoints.
- **Deployment**: Docker + Kubernetes (giả định), port 8083 (context-path: /product).
```mermaid
graph TD
    A[Client/App] -->|REST API| B[Product-Service Port 8083 /product]
    B -->|Search/Index| C[Elasticsearch Port 9200]
    B -->|Persist Product| D[MongoDB product_db]
    B -->|Events| E[Kafka Topics]
    B -->|Cleanup/Delete| F[File-Service Feign]
    B -->|Index Remove| G[Gemini-Service Feign]
    B -->|Reviews Delete| H[Review-Service Feign]
    I[Batch Job] -->|Cleanup| J[Discontinued Products]
    
    style B fill:#f9f,stroke:#333,stroke-width:2px
```

## ✨ Tính Năng Chính
- **Product Management**: Search by ID/seller/size/category/query/suggest, get products, update stock từ order events.
- **Category Management**: CRUD categories (create/update/delete/get by ID/all).
- **Event Handling**: Kafka listeners cho order created (update stock), status changed/cancel (restore stock), product invalid (delete/mark inactive).
- **Batch Processing**: Scheduled cleanup discontinued products (sau 30 ngày: delete Mongo/ES/Gemini, images S3, reviews).
- **Integration**: Feign cho File (delete images), Gemini (remove index), Review (delete by product), User (nếu cần).
- **Search & Indexing**: Elasticsearch cho full-text search, suggest; public endpoints không cần auth.
- **Error Handling**: Standardized ApiResponse, JWT entry point.

## 🛠️ Tech Stack
| Component          | Technology                  | Details                                      |
|--------------------|-----------------------------|----------------------------------------------|
| **Language/Framework** | Java 17+ / Spring Boot 3.x | REST Controllers, MongoDB repos, Security    |
| **Database**       | MongoDB                     | product_db (Product, Category entities)      |
| **Search**         | Elasticsearch               | Indexing/search/suggest (host: elasticsearch:9200) |
| **Messaging**      | Apache Kafka                | Topics: create-order, order-updated, user-cancel-order, product-invalid; group: product-group |
| **Security**       | Spring Security (OAuth2)    | JWT decoder, public endpoints (/searchByProduct/**, etc.) |
| **Client**         | OpenFeign                   | FileClient (images), GeminiClient (index), ReviewClient; timeout 30-60s |
| **Batch/Scheduling** | Spring Scheduler           | ProductCleanupBatchJob (cron: 2h CN, batch 100, delete images) |
| **Utils**          | Lombok, Jackson             | DTOs (OrderCreatedEvent, etc.), JsonSerializer |

## 🚀 Cài Đặt & Chạy
### Yêu Cầu
- Java 17+ / Maven 3.6+.
- Docker (cho MongoDB, Elasticsearch, Kafka).
- Environment vars: `SPRING_DATA_MONGODB_URI` (mongodb://root:root@mongodb:27017/product_db), `FEIGN_FILE` (http://file-service:8084/file), `ELASTICSEARCH_URI` (http://elasticsearch:9200) (xem application.yml).

### Bước 1: Clone Repo
```bash
git clone https://github.com/shopping-ecommerce/product-service.git
cd product-service
```

### Bước 2: Setup Môi Trường
```bash
# Copy env files (nếu có example)
cp src/main/resources/application.yml.example application.yml

# Build project
mvn clean install

# Setup Docker services (MongoDB, ES, Kafka)
docker-compose up -d  # Sử dụng docker-compose.yml nếu có
```

### Bước 3: Chạy Service
```bash
# Run với Maven
mvn spring-boot:run

# Hoặc JAR
java -jar target/product-service-*.jar
```

- Port mặc định: **8083** (context: /product, e.g., http://localhost:8083/product/searchByProduct/...).
- Test endpoints: Sử dụng Postman/Swagger (http://localhost:8083/product/swagger-ui.html nếu enable).

Ví dụ test search product:
```bash
curl -X GET http://localhost:8083/product/searchByProduct/{productId}
  # Public, no auth needed
```

### Bước 4: Test & Debug
```bash
# Run tests
mvn test

# Check logs (INFO cho batch, DEBUG nếu cần)
tail -f logs/application.log  # Hoặc console
```

- Public: /getProducts, /searchByProduct/**, /categories, etc.
- Cleanup: Chạy cron hàng ngày 2h CN (config: product.cleanup.cron).

## 📚 Tài Liệu
- **API Docs**: Sử dụng SpringDoc OpenAPI (Swagger UI tại `/swagger-ui.html`).
- **Endpoints** (base: /product):
  | Method | Endpoint                          | Description                  | Auth Required    |
  |--------|-----------------------------------|------------------------------|------------------|
  | GET    | `/getProducts`                    | Lấy tất cả products          | No               |
  | GET    | `/searchByProduct/{id}`           | Search by product ID         | No               |
  | GET    | `/searchBySeller/{sellerId}`      | Search by seller             | No               |
  | GET    | `/categories`                     | Lấy tất cả categories        | No               |
  | POST   | `/categories/create`              | Tạo category                 | Yes              |
  | POST   | `/categories/update`              | Update category              | Yes              |
  | GET    | `/categories/delete/{id}`         | Delete category              | Yes              |
  | GET    | `/suggest`                        | Suggest search               | No               |
- **Deployment Guide**: Xem `docs/deploy.md` (Kubernetes manifests cho microservices).
- **Contributing Guide**: Xem `CONTRIBUTING.md`.

## 🤝 Đóng Góp
- Fork repo và tạo PR với branch `feature/[tên-feature]`.
- Tuân thủ code style: Checkstyle, Lombok annotations.
- Test coverage >80% trước merge.
  Pull requests welcome! Báo issue nếu bug hoặc feature request.

## 📄 Giấy Phép
Dự án này được phân phối dưới giấy phép MIT. Xem file [LICENSE](LICENSE) để biết chi tiết.

## 👥 Liên Hệ
- Author: [Hồ Huỳnh Hoài Thịnh] ([@github-hohuynhhoaithinh](https://github.com/hohuynhhoaithinh))
- Email: [hohuynhhoaithinh@gmail.com]

---

*Cảm ơn bạn đã sử dụng Product-Service! 🚀*