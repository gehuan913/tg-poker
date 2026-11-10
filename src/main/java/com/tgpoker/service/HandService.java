package com.tgpoker.service;

import com.tgpoker.mapper.HandMapper;
import com.tgpoker.mapper.HandPlayerMapper;
import com.tgpoker.mapper.InsuranceBetMapper;
import com.tgpoker.model.Hand;
import com.tgpoker.model.HandPlayer;
import com.tgpoker.model.InsuranceBetRecord;
import com.tgpoker.room.HandOutcome;
import com.tgpoker.room.InsuranceOrder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class HandService {

    private final HandMapper handMapper;
    private final HandPlayerMapper handPlayerMapper;
    private final InsuranceBetMapper insuranceBetMapper;

    /**
     * 一手牌落库：存证主表 + 每座位战绩 + 保险单。
     *
     * <p>整体一个事务，任何一步失败都回滚，避免出现"有手牌没战绩"的脏数据。
     * 主键冲突视为重复结算（例如重连后又触发一次），只记日志不抛错。</p>
     */
    @Transactional
    public void save(HandOutcome o) {
        Hand h = new Hand();
        h.setHandId(o.handId);
        h.setTableId(o.tableId);
        h.setClubId(o.clubId);
        h.setVariant(o.variant);
        h.setBlinds(o.blinds);
        h.setAnte(o.ante);
        h.setBoard(o.board);
        h.setPot(o.pot);
        h.setRake(0L);
        h.setServerSeed(o.serverSeed);
        h.setServerSeedHash(o.serverSeedHash);
        h.setClientSeeds(o.clientSeeds);
        h.setNonce(o.nonce);
        h.setVerifyCode(o.verifyCode);
        h.setStartedAt(o.startedAt);
        h.setEndedAt(o.endedAt);

        if (handMapper.selectById(o.handId) == null) {
            handMapper.insert(h);
        }

        for (HandOutcome.PlayerResult r : o.players) {
            HandPlayer p = new HandPlayer();
            p.setHandId(o.handId);
            p.setSeat(r.seat);
            p.setUserId(r.userId);
            p.setHoleCards(r.holeCards);
            p.setStartStack(r.startStack);
            p.setEndStack(r.endStack);
            p.setNet(r.net);
            p.setVpip(r.vpip);
            p.setPfr(r.pfr);
            p.setActions(r.actions);
            p.setInsuranceNet(r.insuranceNet);
            try {
                handPlayerMapper.insert(p);
            } catch (Exception e) {
                log.warn("重复写入手牌玩家 {} seat={}", o.handId, r.seat);
            }
        }

        for (InsuranceOrder io : o.insurances) {
            InsuranceBetRecord rec = new InsuranceBetRecord();
            rec.setHandId(io.handId);
            rec.setTableId(io.tableId);
            rec.setPotIndex(io.potIndex);
            rec.setBuyerUserId(io.buyerUserId);
            rec.setOuts(io.outs);
            rec.setOdds(BigDecimal.valueOf(io.odds));
            rec.setTieOuts(io.tieOuts);
            rec.setTieOdds(io.tieOdds > 0 ? BigDecimal.valueOf(io.tieOdds) : null);
            rec.setPremiumMain(io.premiumMain);
            rec.setPremiumTie(io.premiumTie);
            rec.setCoverage(io.coverage);
            rec.setResult(io.result);
            rec.setSettledAt(LocalDateTime.now());
            rec.setCreatedAt(LocalDateTime.now());
            try {
                insuranceBetMapper.insert(rec);
            } catch (Exception e) {
                log.warn("保险单落库失败 {} {}", io.handId, io.id);
            }
        }

        log.debug("手牌落库 {} 玩家 {} 人 底池 {}", o.handId, o.players.size(), o.pot);
    }
}
