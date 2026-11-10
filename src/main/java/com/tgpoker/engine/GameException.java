package com.tgpoker.engine;

/** 规则校验失败。 */
public class GameException extends RuntimeException {

    public GameException(String message) {
        super(message);
    }
}
