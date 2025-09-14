package iuh.fit.se.dto.request;


import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SearchSizeAndIDRequest {
    String size;
    String id;
}
