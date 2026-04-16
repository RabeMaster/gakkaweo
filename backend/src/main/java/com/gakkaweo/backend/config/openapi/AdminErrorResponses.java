package com.gakkaweo.backend.config.openapi;

import com.gakkaweo.backend.common.exception.ErrorBody;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
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
  @ApiResponse(responseCode = "200", useReturnTypeSchema = true),
  @ApiResponse(
      responseCode = "400",
      description = "잘못된 요청",
      content =
          @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = ErrorBody.class),
              examples =
                  @ExampleObject(
                      value =
                          """
              {"status":400,"code":"VALIDATION_FAILED","message":"요청 검증에 실패했습니다","timestamp":"2026-04-17T12:00:00Z"}"""))),
  @ApiResponse(
      responseCode = "401",
      description = "인증 실패",
      content =
          @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = ErrorBody.class),
              examples =
                  @ExampleObject(
                      value =
                          """
              {"status":401,"code":"UNAUTHORIZED","message":"인증이 필요합니다","timestamp":"2026-04-17T12:00:00Z"}"""))),
  @ApiResponse(
      responseCode = "403",
      description = "관리자 권한 필요",
      content =
          @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = ErrorBody.class),
              examples =
                  @ExampleObject(
                      value =
                          """
              {"status":403,"code":"ACCESS_DENIED","message":"접근 권한이 없습니다","timestamp":"2026-04-17T12:00:00Z"}"""))),
  @ApiResponse(
      responseCode = "404",
      description = "리소스 없음",
      content =
          @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = ErrorBody.class),
              examples =
                  @ExampleObject(
                      value =
                          """
              {"status":404,"code":"MEMBER_NOT_FOUND","message":"회원을 찾을 수 없습니다","timestamp":"2026-04-17T12:00:00Z"}"""))),
  @ApiResponse(
      responseCode = "429",
      description = "요청 제한 초과",
      content =
          @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = ErrorBody.class),
              examples =
                  @ExampleObject(
                      value =
                          """
              {"status":429,"code":"RATE_LIMIT_EXCEEDED","message":"요청이 너무 많습니다. 잠시 후 다시 시도해주세요","timestamp":"2026-04-17T12:00:00Z"}"""))),
  @ApiResponse(
      responseCode = "503",
      description = "서비스 불가",
      content =
          @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = ErrorBody.class),
              examples =
                  @ExampleObject(
                      value =
                          """
              {"status":503,"code":"AI_SERVICE_UNAVAILABLE","message":"AI 서비스를 일시적으로 이용할 수 없습니다","timestamp":"2026-04-17T12:00:00Z"}""")))
})
public @interface AdminErrorResponses {}
