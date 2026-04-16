package com.gakkaweo.backend.config.openapi;

import com.gakkaweo.backend.common.exception.ErrorBody;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@ApiResponses({
  @ApiResponse(
      responseCode = "400",
      description = "잘못된 요청",
      content =
          @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = ErrorBody.class))),
  @ApiResponse(
      responseCode = "401",
      description = "인증 실패",
      content =
          @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = ErrorBody.class))),
  @ApiResponse(
      responseCode = "404",
      description = "리소스 없음",
      content =
          @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = ErrorBody.class))),
  @ApiResponse(
      responseCode = "429",
      description = "요청 제한 초과",
      content =
          @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = ErrorBody.class))),
  @ApiResponse(
      responseCode = "503",
      description = "서비스 불가",
      content =
          @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = ErrorBody.class)))
})
public @interface StandardErrorResponses {}
