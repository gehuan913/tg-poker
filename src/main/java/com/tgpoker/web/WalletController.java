package com.tgpoker.web;

import com.tgpoker.model.DepositOrder;
import com.tgpoker.model.LedgerEntry;
import com.tgpoker.model.WithdrawOrder;
import com.tgpoker.service.BlockchainService;
import com.tgpoker.service.RedPacketService;
import com.tgpoker.service.WalletService;
import com.tgpoker.util.Money;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 钱包接口：余额、流水、充值地址、充提订单、红包、对账。
 *
 * <p>所有金额参数与返回值都是 micro-USDT；前端负责除以 1e6 展示。
 * 当前区块链平台未接入时 {@code ready=false}，充值走模拟入账，提现只记账不广播。</p>
 */
@RestController
@RequestMapping("/api/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;
    private final BlockchainService blockchainService;
    private final com.tgpoker.service.UserService userService;
    private final RedPacketService redPacketService;

    @Value("${app.blockchain.ready:false}")
    private boolean blockchainReady;

    @GetMapping
    public ApiResponse<Map<String, Object>> info(@RequestAttribute("userId") Long userId) {
        Map<String, Object> m = new LinkedHashMap<>();
        long balance = walletService.balance(userId);
        m.put("balance", balance);
        m.put("balanceText", Money.fmt(balance));
        m.put("withdrawFee", BlockchainService.WITHDRAW_FEE);
        m.put("withdrawFeeText", Money.fmt(BlockchainService.WITHDRAW_FEE));
        m.put("minDeposit", BlockchainService.MIN_DEPOSIT);
        m.put("minWithdraw", BlockchainService.MIN_WITHDRAW);
        m.put("network", "TRON-TRC20");
        m.put("ready", blockchainReady);
        return ApiResponse.ok(m);
    }

    @GetMapping("/ledger")
    public ApiResponse<List<LedgerEntry>> ledger(@RequestAttribute("userId") Long userId) {
        return ApiResponse.ok(walletService.history(userId, 50));
    }

    /**
     * 充值地址：一人一址，重复调用返回同一个地址。
     * 接口未接公司平台前 ready=false，前端应提示"模拟充值"。
     */
    @GetMapping("/deposit-address")
    public ApiResponse<Map<String, Object>> depositAddress(@RequestAttribute("userId") Long userId) {
        String addr = blockchainService.ensureAddress(userId);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("address", addr);
        m.put("network", "TRON-TRC20");
        m.put("asset", "USDT");
        m.put("minDeposit", BlockchainService.MIN_DEPOSIT);
        m.put("minDepositText", Money.fmt(BlockchainService.MIN_DEPOSIT));
        m.put("ready", blockchainReady);
        m.put("note", blockchainReady ? "" : "当前为模拟模式，地址不可用于真实充值");
        return ApiResponse.ok(m);
    }

    /** 本地联调：模拟链上到账。真实充值走区块链平台回调。 */
    @PostMapping("/mock-deposit")
    public ApiResponse<Map<String, Object>> mockDeposit(@RequestAttribute("userId") Long userId,
                                                        @RequestBody Map<String, Object> body) {
        long amount = Long.parseLong(String.valueOf(body.getOrDefault("amount", "0")));
        if (amount < BlockchainService.MIN_DEPOSIT) {
            throw new IllegalArgumentException("最小充值 " + Money.fmt(BlockchainService.MIN_DEPOSIT) + " USDT");
        }
        blockchainService.devFaucet(userId, amount);
        Map<String, Object> m = new LinkedHashMap<>();
        long balance = walletService.balance(userId);
        m.put("balance", balance);
        m.put("balanceText", Money.fmt(balance));
        return ApiResponse.ok(m);
    }

    @PostMapping("/withdraw")
    public ApiResponse<Map<String, Object>> withdraw(@RequestAttribute("userId") Long userId,
                                                     @RequestBody Map<String, Object> body) {
        long amount = Long.parseLong(String.valueOf(body.getOrDefault("amount", "0")));
        String address = body.get("address") == null ? "" : String.valueOf(body.get("address"));
        // 已设置支付密码的账号，提现必须带正确密码
        if (userService.hasPayPassword(userId)) {
            String pwd = body.get("payPwd") == null ? "" : String.valueOf(body.get("payPwd"));
            if (!userService.checkPayPassword(userId, pwd)) {
                throw new IllegalArgumentException("支付密码错误");
            }
        }
        return ApiResponse.ok(blockchainService.requestWithdraw(userId, address, amount));
    }

    @GetMapping("/withdraw-orders")
    public ApiResponse<List<WithdrawOrder>> withdrawOrders(@RequestAttribute("userId") Long userId) {
        return ApiResponse.ok(blockchainService.withdrawOrders(userId));
    }

    @GetMapping("/deposit-orders")
    public ApiResponse<List<DepositOrder>> depositOrders(@RequestAttribute("userId") Long userId) {
        return ApiResponse.ok(blockchainService.depositOrders(userId));
    }

    /** 红包：发送 / 收件箱。 */
    @PostMapping("/redpacket")
    public ApiResponse<Map<String, Object>> sendRedPacket(@RequestAttribute("userId") Long userId,
                                                          @RequestBody Map<String, Object> body) {
        long receiverId = Long.parseLong(String.valueOf(body.getOrDefault("receiverId", "0")));
        long amount = Long.parseLong(String.valueOf(body.getOrDefault("amount", "0")));
        String memo = body.get("memo") == null ? "" : String.valueOf(body.get("memo"));
        String scene = body.get("scene") == null ? "CHAT" : String.valueOf(body.get("scene"));
        String tableId = body.get("tableId") == null ? null : String.valueOf(body.get("tableId"));
        return ApiResponse.ok(redPacketService.send(userId, receiverId, amount, memo, scene, tableId));
    }

    @GetMapping("/redpackets")
    public ApiResponse<List<Map<String, Object>>> redPackets(@RequestAttribute("userId") Long userId) {
        return ApiResponse.ok(redPacketService.received(userId, 30));
    }

    /** 对账：开发期手工触发，生产应定时任务 + 差额告警。 */
    @GetMapping("/reconcile")
    public ApiResponse<Map<String, Object>> reconcile(@RequestAttribute("userId") Long userId) {
        // 开发期没有平台余额接口，用 0 占位；接平台后传真实余额
        return ApiResponse.ok(blockchainService.reconcile(0));
    }
}
