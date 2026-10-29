package ru.vk.itmo.scheduler.slotwise;

import org.springframework.boot.SpringApplication;

public class TestSlotwiseBackApplication {

    public static void main(String[] args) {
        SpringApplication.from(SlotwiseBackApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
