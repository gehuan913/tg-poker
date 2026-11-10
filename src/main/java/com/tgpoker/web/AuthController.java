package com.tgpoker.web;

import com.tgpoker.auth.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 登录入口。
 *
 * <p>只做一件事：把 Mini App 传来的 initData（或浏览器联调用的 devId）交给
 * {@link AuthService} 换成本站 token。验签逻辑不在控制器里，避免被绕过。</p>
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ApiResponse<AuthService.LoginResult> login(@RequestBody Map<String, Object> body) {
        String initData = body.get("initData") == null ? null : String.valueOf(body.get("initData"));
        Long devId = body.get("devId") == null ? null : Long.valueOf(String.valueOf(body.get("devId")));
        String devName = body.get("devName") == null ? null : String.valueOf(body.get("devName"));
        return ApiResponse.ok(authService.login(initData, devId, devName));
    }
}
