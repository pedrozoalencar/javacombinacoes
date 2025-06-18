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
             OptionalDouble minOptional = installments.stream()
                                       .mapToDouble(Installment::getAmount)
                                       .min();
             if (minOptional.isPresent()) {
                 return Optional.of(minOptional.getAsDouble());
             } else {
                 return Optional.empty();
             }
    }

    @Override
    public String toString() {
        return "Contract{id='" + contractId + "', debt=" + debtBalance + ", installments=" + installments.size() + "}";
    }
}
