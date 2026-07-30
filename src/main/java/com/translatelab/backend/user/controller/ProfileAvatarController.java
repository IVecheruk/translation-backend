package com.translatelab.backend.user.controller;

import com.translatelab.backend.config.OpenApiConfig;
import com.translatelab.backend.user.dto.AvatarDownloadResult;
import com.translatelab.backend.user.service.AvatarService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/profile/avatar")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class ProfileAvatarController {

    private final AvatarService avatarService;

    public ProfileAvatarController(AvatarService avatarService) {
        this.avatarService = avatarService;
    }

    @PutMapping(
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void uploadAvatar(
            @AuthenticationPrincipal Jwt jwt,
            @RequestPart("file") MultipartFile file
    ) {
        UUID userId = authenticatedUserId(jwt);

        avatarService.uploadAvatar(
                userId,
                file
        );
    }

    @GetMapping
    public ResponseEntity<InputStreamResource> downloadAvatar(
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID userId = authenticatedUserId(jwt);

        AvatarDownloadResult result =
                avatarService.downloadAvatar(userId);

        InputStreamResource resource =
                new InputStreamResource(result.inputStream());

        return ResponseEntity
                .ok()
                .contentType(
                        MediaType.parseMediaType(
                                result.contentType()
                        )
                )
                .cacheControl(CacheControl.noStore())
                .body(resource);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAvatar(
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID userId = authenticatedUserId(jwt);

        avatarService.deleteAvatar(userId);
    }

    private UUID authenticatedUserId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}