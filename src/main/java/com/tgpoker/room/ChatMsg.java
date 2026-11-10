package com.tgpoker.room;

/**
 * 桌内聊天消息。
 *
 * <p>只保留最近 30 条，随视图快照一起下发（重连不丢上下文）。
 * 不做持久化：桌内聊天是即时社交，不是账本，没必要进库。</p>
 */
public class ChatMsg {

    /** 发送者座位号；旁观者发言为 -1（当前不允许）。 */
    public int seat;

    /** 发送者用户 ID，前端用来区分"我发的"。 */
    public long userId;

    /** 发送者昵称快照。 */
    public String nick;

    /** 文本内容，已做长度截断与字符过滤。 */
    public String text;

    /** 服务端时间戳（毫秒）。 */
    public long at;

    public ChatMsg() {
    }

    public ChatMsg(int seat, long userId, String nick, String text, long at) {
        this.seat = seat;
        this.userId = userId;
        this.nick = nick;
        this.text = text;
        this.at = at;
    }
}
