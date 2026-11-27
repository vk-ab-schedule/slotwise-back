package ru.vk.itmo.scheduler.slotwise.planning;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Clock;

@Configuration(proxyBeanMethods = false)
class PlanningConfiguration {
    @Bean
    Clock planningClock() {
        return Clock.systemUTC();
    }
}
