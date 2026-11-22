package com.murisegames.api.modules.Event.Scoring.Adapter;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Exact arithmetic for ranking.
 *
 * Decimal values are projections for display or ledger settlement.
 * Never reconstruct an exact score from a rounded projection.
 */
public record ExactScore(
        BigInteger numerator,
        BigInteger denominator
) implements Comparable<ExactScore> {

    public static final ExactScore ZERO =
            new ExactScore(BigInteger.ZERO, BigInteger.ONE);

    public ExactScore {
        Objects.requireNonNull(numerator, "numerator");
        Objects.requireNonNull(denominator, "denominator");

        if (denominator.signum() <= 0) {
            throw new IllegalArgumentException(
                    "The score denominator must be positive."
            );
        }

        BigInteger divisor = numerator.gcd(denominator);
        numerator = numerator.divide(divisor);
        denominator = denominator.divide(divisor);
    }

    public static ExactScore of(long numerator, long denominator) {
        return new ExactScore(
                BigInteger.valueOf(numerator),
                BigInteger.valueOf(denominator)
        );
    }

    public ExactScore add(ExactScore other) {
        return new ExactScore(
                numerator.multiply(other.denominator)
                        .add(other.numerator.multiply(denominator)),
                denominator.multiply(other.denominator)
        );
    }

    public ExactScore subtract(ExactScore other) {
        return new ExactScore(
                numerator.multiply(other.denominator)
                        .subtract(other.numerator.multiply(denominator)),
                denominator.multiply(other.denominator)
        );
    }

    public BigDecimal decimal(int scale) {
        return new BigDecimal(numerator).divide(
                new BigDecimal(denominator),
                scale,
                RoundingMode.HALF_UP
        );
    }

    public String display() {
        return decimal(1).toPlainString();
    }

    public BigDecimal settlementAmount() {
        return decimal(4);
    }

    /**
     * Corrections reconcile rounded cumulative amounts.
     *
     * Do not round the exact difference independently: that can
     * make repeated corrections drift away from the settled total.
     */
    public BigDecimal settlementDeltaFrom(ExactScore previous) {
        return settlementAmount().subtract(previous.settlementAmount());
    }

    @Override
    public int compareTo(ExactScore other) {
        return numerator.multiply(other.denominator)
                .compareTo(other.numerator.multiply(denominator));
    }
}