package kr.shkworld.shktown.core.taxi.model;

import java.math.BigDecimal;
import java.util.Objects;

public record TaxiFarePolicy(
        double baseDistance,
        BigDecimal baseFare,
        double extraDistance,
        BigDecimal extraFare
) {
    public TaxiFarePolicy {
        Objects.requireNonNull(baseFare, "baseFare");
        Objects.requireNonNull(extraFare, "extraFare");

        if (!Double.isFinite(baseDistance) || baseDistance < 0
                || !Double.isFinite(extraDistance) || extraDistance < 0
                || baseFare.signum() < 0 || extraFare.signum() < 0) {
            throw new IllegalArgumentException("Invalid taxi fare policy");
        }
    }

    public BigDecimal calculate(double distance) {
        if (!Double.isFinite(distance) || distance < 0) {
            throw new IllegalArgumentException("Taxi distance must be finite and non-negative");
        }

        double steps = Math.ceil(
                Math.max(0, distance - baseDistance) / extraDistance
        );

        if (!Double.isFinite(steps)) {
            throw new IllegalArgumentException("Taxi distance exceeds the supported range");
        }

        return baseFare.add(
                extraFare.multiply(BigDecimal.valueOf(steps))
        );
    }
}
