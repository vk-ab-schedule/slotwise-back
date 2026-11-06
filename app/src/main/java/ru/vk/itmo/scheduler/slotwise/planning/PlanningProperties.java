package ru.vk.itmo.scheduler.slotwise.planning;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.Map;

@Getter
@Setter
@Validated
@ConfigurationProperties("slotwise.planning")
public class PlanningProperties {
    // D-16 and the database buffer constraint use a fixed five-minute grid.
    @Min(5)
    @Max(5)
    private int slotMinutes = 5;
    @NotNull
    private Map<String, @NotNull @PositiveOrZero Integer> defaultWeights =
            Map.of("S1", 1, "S2", 1, "S3", 1, "S4", 1, "S5", 1);
}
