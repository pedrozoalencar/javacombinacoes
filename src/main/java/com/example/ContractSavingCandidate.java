package com.example;

public class ContractSavingCandidate {
    private String contractId;
    private double debtBalance;
    private double minInstallmentCost;

    public ContractSavingCandidate(String contractId, double debtBalance, double minInstallmentCost) {
        this.contractId = contractId;
        this.debtBalance = debtBalance;
        this.minInstallmentCost = minInstallmentCost;
    }

    public String getContractId() {
        return contractId;
    }

    public double getDebtBalance() {
        return debtBalance;
    }

    public double getMinInstallmentCost() {
        return minInstallmentCost;
    }

    @Override
    public String toString() {
        return "Candidate{id='" + contractId + "', debt=" + debtBalance + ", cost=" + minInstallmentCost + "}";
    }
}
