package iuh.fit.se.dto.request;


import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SearchSizeAndIDRequest {
    Map<String,String> options;
    String id;
}