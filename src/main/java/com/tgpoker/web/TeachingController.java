package com.tgpoker.web;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.tgpoker.mapper.LessonFavoriteMapper;
import com.tgpoker.model.Lesson;
import com.tgpoker.model.QuizQuestion;
import com.tgpoker.service.TeachingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 教学：知识 / 工具 / 训练 / 我的。 */
@RestController
@RequestMapping("/api/teach")
@RequiredArgsConstructor
public class TeachingController {

    private final TeachingService teachingService;
    private final LessonFavoriteMapper favoriteMapper;

    @GetMapping("/lessons")
    public ApiResponse<List<Lesson>> lessons(@RequestParam(required = false) String category) {
        return ApiResponse.ok(teachingService.lessons(category));
    }

    @GetMapping("/lessons/{id}")
    public ApiResponse<Lesson> lesson(@PathVariable long id) {
        return ApiResponse.ok(teachingService.lesson(id));
    }

    /** 收藏 / 取消收藏。 */
    @PostMapping("/lessons/{id}/favorite")
    public ApiResponse<Map<String, Object>> favorite(@RequestAttribute("userId") Long userId,
                                                     @PathVariable long id,
                                                     @RequestBody(required = false) Map<String, Object> body) {
        boolean on = body == null || body.get("on") == null || Boolean.parseBoolean(String.valueOf(body.get("on")));
        if (on) {
            try {
                favoriteMapper.insert(userId, id);
            } catch (Exception ignore) {
                // 已收藏
            }
        } else {
            favoriteMapper.delete(userId, id);
        }
        return ApiResponse.ok(Map.of("favorite", on));
    }

    @GetMapping("/favorites")
    public ApiResponse<List<Map<String, Object>>> favorites(@RequestAttribute("userId") Long userId) {
        return ApiResponse.ok(com.tgpoker.util.Maps.camelAll(favoriteMapper.list(userId)));
    }

    @GetMapping("/quiz")
    public ApiResponse<List<QuizQuestion>> quiz(@RequestParam(defaultValue = "10") int limit) {
        return ApiResponse.ok(teachingService.quiz(limit));
    }

    @PostMapping("/quiz/answer")
    public ApiResponse<Map<String, Object>> answer(@RequestBody Map<String, Object> body) {
        long id = Long.parseLong(String.valueOf(body.getOrDefault("id", "0")));
        int choice = Integer.parseInt(String.valueOf(body.getOrDefault("choice", "-1")));
        return ApiResponse.ok(teachingService.answer(id, choice));
    }

    /**
     * 胜率计算器。
     * 示例：/api/teach/equity?hole=AsKd&board=2c7h9s&opponents=2&variant=LONG
     */
    @GetMapping("/equity")
    public ApiResponse<Map<String, Object>> equity(@RequestParam String hole,
                                                   @RequestParam(required = false) String board,
                                                   @RequestParam(defaultValue = "1") int opponents,
                                                   @RequestParam(defaultValue = "LONG") String variant,
                                                   @RequestParam(defaultValue = "20000") int iterations) {
        return ApiResponse.ok(teachingService.equity(hole, board, opponents, variant, iterations));
    }

    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats(@RequestAttribute("userId") Long userId) {
        QueryWrapper<Lesson> q = new QueryWrapper<>();
        long total = 0;
        return ApiResponse.ok(Map.of("lessons", total));
    }
}
