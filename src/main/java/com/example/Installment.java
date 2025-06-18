package com.example;

public class Installment {
    private String installmentId;
    private double amount;

    public Installment(String installmentId, double amount) {
        this.installmentId = installmentId;
        this.amount = amount;
    }

    public String getInstallmentId() {
        return installmentId;
    }

    public double getAmount() {
        return amount;
    }

    @Override
    public String toString() {
        return "Installment{id='" + installmentId + "', amount=" + amount + "}";
    }
}
