package com.tgpoker.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tgpoker.engine.Card;
import com.tgpoker.engine.EquityCalculator;
import com.tgpoker.mapper.LessonMapper;
import com.tgpoker.mapper.QuizQuestionMapper;
import com.tgpoker.model.Lesson;
import com.tgpoker.model.QuizQuestion;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 教学模块：知识 / 工具 / 训练 / 我的。
 *
 * <p>「工具」是胜率计算器，"我的"是收藏与历史。内容本身（视频、文章）由内容运营产出，
 * 这里只提供承载与读取。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TeachingService {

    private final LessonMapper lessonMapper;
    private final QuizQuestionMapper quizMapper;
    private final ObjectMapper json = new ObjectMapper();

    /**
     * 课程列表。
     *
     * @param category STRATEGY / TOOL / TRAINING，传 ALL 或空表示全部
     */
    public List<Lesson> lessons(String category) {
        QueryWrapper<Lesson> q = new QueryWrapper<>();
        if (category != null && !category.isBlank() && !"ALL".equalsIgnoreCase(category)) {
            q.eq("category", category.toUpperCase());
        }
        q.orderByAsc("sort_no").orderByDesc("id").last("LIMIT 50");
        return lessonMapper.selectList(q);
    }

    /** 课程详情；顺带把播放量 +1（写库，量大后可改成异步/Redis 计数）。 */
    public Lesson lesson(long id) {
        Lesson l = lessonMapper.selectById(id);
        if (l != null) {
            l.setViews((l.getViews() == null ? 0 : l.getViews()) + 1);
            lessonMapper.updateById(l);
        }
        return l;
    }

    /** 取一组练习题（最多 20 道）。 */
    public List<QuizQuestion> quiz(int limit) {
        QueryWrapper<QuizQuestion> q = new QueryWrapper<>();
        q.last("LIMIT " + Math.max(1, Math.min(limit, 20)));
        return quizMapper.selectList(q);
    }

    /** 判题：返回是否正确 + 解析。 */
    public Map<String, Object> answer(long questionId, int choice) {
        QuizQuestion qq = quizMapper.selectById(questionId);
        Map<String, Object> m = new LinkedHashMap<>();
        if (qq == null) {
            m.put("correct", false);
            m.put("explain", "题目不存在");
            return m;
        }
        boolean correct = qq.getAnswer() != null && qq.getAnswer() == choice;
        m.put("correct", correct);
        m.put("answer", qq.getAnswer());
        m.put("explain", qq.getExplain());
        return m;
    }

    /**
     * 胜率计算。
     *
     * @param hole      自己底牌，如 "AsKd"
     * @param board     公牌，如 "2c7h9s"（可为空）
     * @param opponents 对手数
     * @param variant   LONG / SHORT
     */
    public Map<String, Object> equity(String hole, String board, int opponents, String variant, int iterations) {
        Card[] my = parseCards(hole, 2);
        List<Card> bd = new ArrayList<>();
        if (board != null && !board.isBlank()) {
            for (Card c : parseCards(board, 5)) {
                if (c != null) {
                    bd.add(c);
                }
            }
        }
        if (bd.size() != 0 && bd.size() != 3 && bd.size() != 4 && bd.size() != 5) {
            throw new IllegalArgumentException("公牌只能是 0 / 3 / 4 / 5 张");
        }
        EquityCalculator.Result r = EquityCalculator.compute(
                my, bd, opponents, com.tgpoker.engine.Variant.of(variant), iterations);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("win", round(r.win));
        m.put("tie", round(r.tie));
        m.put("lose", round(r.lose));
        m.put("iterations", r.iterations);
        m.put("opponents", r.opponents);
        m.put("handName", r.handName);
        m.put("winText", String.format("%.2f%%", r.win * 100));
        m.put("tieText", String.format("%.2f%%", r.tie * 100));
        m.put("loseText", String.format("%.2f%%", r.lose * 100));
        return m;
    }

    private double round(double v) {
        return Math.round(v * 10000) / 10000.0;
    }

    /** 解析 "AsKd" / "As Kd 2c" 这类紧凑写法。 */
    public static Card[] parseCards(String text, int max) {
        if (text == null) {
            throw new IllegalArgumentException("牌不能为空");
        }
        String s = text.replaceAll("[\\s,]+", "");
        if (s.length() % 2 != 0) {
            throw new IllegalArgumentException("牌格式不正确，示例：AsKd");
        }
        List<Card> out = new ArrayList<>();
        for (int i = 0; i + 1 < s.length() && out.size() < max; i += 2) {
            out.add(parseCard(s.charAt(i), s.charAt(i + 1)));
        }
        return out.toArray(new Card[0]);
    }

    public static Card parseCard(char rank, char suit) {
        int r;
        switch (Character.toUpperCase(rank)) {
            case 'A': r = 14; break;
            case 'K': r = 13; break;
            case 'Q': r = 12; break;
            case 'J': r = 11; break;
            case 'T': r = 10; break;
            default:
                if (rank < '2' || rank > '9') {
                    throw new IllegalArgumentException("非法点数：" + rank);
                }
                r = rank - '0';
        }
        int st;
        switch (Character.toLowerCase(suit)) {
            case 's': st = 0; break;
            case 'h': st = 1; break;
            case 'c': st = 2; break;
            case 'd': st = 3; break;
            default: throw new IllegalArgumentException("非法花色：" + suit);
        }
        return new Card(r, st);
    }
}
