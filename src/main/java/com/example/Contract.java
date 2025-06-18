package com.example;

import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Optional;
 import java.util.OptionalDouble;

public class Contract {
    private String contractId;
    private double debtBalance;
    private List<Installment> installments;

    public Contract(String contractId, double debtBalance, List<Installment> installments) {
        this.contractId = contractId;
        this.debtBalance = debtBalance;
        this.installments = new ArrayList<>(installments); // Use a copy
    }

    public String getContractId() {
        return contractId;
    }

    public double getDebtBalance() {
        return debtBalance;
    }

    public List<Installment> getInstallments() {
        return new ArrayList<>(installments); // Return a copy for immutability
    }

    // Helper method for Step 2 of the plan - can be added now or later.
    // Adding it now as it's closely related to the Contract's properties.
    public Optional<Double> getCheapestOpenInstallmentCost() {
        if (installments == null || installments.isEmpty()) {
            return Optional.empty();
        }
             // Filter for installments with amount > 0 before finding the minimum
             OptionalDouble minCost = installments.stream()
                                         .filter(inst -> inst.getAmount() > 1e-9) // Consider amounts > 0 (use tolerance for double)
                                         .mapToDouble(Installment::getAmount)
                                         .min();

             return minCost.isPresent() ? Optional.of(minCost.getAsDouble()) : Optional.empty();
    }

    @Override
    public String toString() {
        return "Contract{id='" + contractId + "', debt=" + debtBalance + ", installments=" + installments.size() + "}";
    }
}
