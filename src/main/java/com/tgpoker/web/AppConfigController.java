package com.tgpoker.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 前端需要的公开配置（客服、官方群、频道、Bot 用户名）。
 *
 * <p>这些值随部署环境变化，不能写死在前端；统一在这里下发，
 * 前端只负责展示与跳转。</p>
 */
@RestController
@RequestMapping("/api/app")
public class AppConfigController {

    /** 客服地址：默认指向测试 Bot，正式环境换成客服账号。 */
    @Value("${app.support.url:https://t.me/momofayu123}")
    private String supportUrl;

    @Value("${app.support.name:在线客服}")
    private String supportName;

    @Value("${app.tg.bot-username:}")
    private String botUsername;

    @Value("${app.tg.group-url:}")
    private String groupUrl;

    @Value("${app.tg.channel-url:}")
    private String channelUrl;

    /** 应用展示名，用于水印与分享文案。 */
    @Value("${app.brand.name:永旺德州}")
    private String brandName;

    @GetMapping("/config")
    public ApiResponse<Map<String, Object>> config() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("supportUrl", supportUrl);
        m.put("supportName", supportName);
        m.put("botUsername", botUsername);
        m.put("groupUrl", groupUrl);
        m.put("channelUrl", channelUrl);
        m.put("brandName", brandName);
        return ApiResponse.ok(m);
    }
}
