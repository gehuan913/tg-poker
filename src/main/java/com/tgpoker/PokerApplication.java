package com.tgpoker;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.tgpoker.mapper")
public class PokerApplication {

    public static void main(String[] args) {
        SpringApplication.run(PokerApplication.class, args);
    }
}
