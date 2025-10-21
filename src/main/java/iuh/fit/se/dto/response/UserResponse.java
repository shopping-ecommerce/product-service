package iuh.fit.se.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserResponse {
    String id;  // Khớp trực tiếp

    @JsonProperty("account_id")  // Map từ JSON "account_id"
    String accountId;

    @JsonProperty("first_name")  // Map từ JSON "first_name"
    String firstName;

    @JsonProperty("last_name")   // Map từ JSON "last_name"
    String lastName;

    int points;  // Khớp trực tiếp

    String address;  // Khớp trực tiếp (null OK)

    @JsonProperty("created_time")  // Map từ JSON "created_time"
    LocalDateTime createdTime;

    @JsonProperty("modified_time") // Map từ JSON "modified_time"
    LocalDateTime modifiedTime;

    @JsonProperty("public_id")     // Map từ JSON "public_id"
    String publicId;
}