package com.tgpoker.web;

import com.tgpoker.engine.GameException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(GameException.class)
    public ApiResponse<Void> onGame(GameException e) {
        return ApiResponse.fail(4001, e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ApiResponse<Void> onState(IllegalStateException e) {
        return ApiResponse.fail(4002, e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ApiResponse<Void> onArg(IllegalArgumentException e) {
        return ApiResponse.fail(4003, e.getMessage());
    }

    /** 运营接口 token 不对：403，且不泄露任何内部信息。 */
    @ExceptionHandler(SecurityException.class)
    public ApiResponse<Void> onSecurity(SecurityException e) {
        return ApiResponse.fail(4003, "无权限");
    }

    /**
     * 静态资源不存在（favicon.ico、过期 hash 的文件名等）不是服务端故障，
     * 直接 404，避免刷 ERROR 日志。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Void> onNoResource(NoResourceFoundException e) {
        log.debug("静态资源不存在：{}", e.getResourcePath());
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> onOther(Exception e) {
        log.error("未处理异常", e);
        return ApiResponse.fail(5000, "服务异常：" + e.getMessage());
    }
}
