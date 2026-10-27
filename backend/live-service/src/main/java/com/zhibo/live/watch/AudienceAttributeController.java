package com.zhibo.live.watch;

import com.zhibo.live.watch.dto.AttributeAudienceRequest;
import com.zhibo.live.watch.dto.AttributeAudienceResponse;
import com.zhibo.live.web.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/watch/audience")
@RequiredArgsConstructor
public class AudienceAttributeController {

    private final AudienceAttributeService audienceAttributeService;

    @PostMapping("/attribute/{id}")
    public ApiResponse<AttributeAudienceResponse> attribute(
            @PathVariable String id, @Valid @RequestBody AttributeAudienceRequest request) {
        return ApiResponse.ok(audienceAttributeService.attribute(id, request));
    }
}
