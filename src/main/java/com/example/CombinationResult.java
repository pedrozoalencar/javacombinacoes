package com.example;

import java.util.List;
import java.util.ArrayList;

public class CombinationResult {
    private List<ContractSavingCandidate> chosenCandidates;
    private double totalCost;
    private double totalDebtBalance;

    public CombinationResult(List<ContractSavingCandidate> chosenCandidates, double totalCost, double totalDebtBalance) {
        this.chosenCandidates = new ArrayList<>(chosenCandidates); // Store a copy
        this.totalCost = totalCost;
        this.totalDebtBalance = totalDebtBalance;
    }

    public List<ContractSavingCandidate> getChosenCandidates() {
        return new ArrayList<>(chosenCandidates); // Return a copy
    }

    public double getTotalCost() {
        return totalCost;
    }

    public double getTotalDebtBalance() {
        return totalDebtBalance;
    }

    @Override
    public String toString() {
        return "Combination{candidates=" + chosenCandidates.size() +
               ", cost=" + totalCost +
               ", debtProtected=" + totalDebtBalance + "}";
    }
}
