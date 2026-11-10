package com.tgpoker.web;

import com.tgpoker.model.AppUser;
import com.tgpoker.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户检索。
 *
 * <p>目前只服务于"发红包选联系人"：按昵称或用户 ID 找人。
 * 只下发 id / nick / avatar，避免变成"查任何人余额"的接口。</p>
 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /** 搜索用户（不含自己）。 */
    @GetMapping("/search")
    public ApiResponse<List<Map<String, Object>>> search(@RequestAttribute("userId") Long userId,
                                                         @RequestParam("q") String q) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (AppUser u : userService.search(q, userId, 20)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("userId", u.getId());
            m.put("nick", u.getNick());
            m.put("avatar", u.getAvatar());
            out.add(m);
        }
        return ApiResponse.ok(out);
    }
}
