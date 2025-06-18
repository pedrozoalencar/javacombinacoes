package com.example;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class Benchmark {

    private static final Random random = new Random(System.currentTimeMillis());
    private static final double MAX_INSTALLMENT_AMOUNT = 200.0;
    private static final double MAX_DEBT_BALANCE = 5000.0;
    private static final int MAX_INSTALLMENTS_PER_CONTRACT = 3;
    private static final double AVAILABLE_FUNDS_SCALAR = 0.2; // e.g., 20% of total cheapest installments sum

    private static Installment createRandomInstallment(int instNum) {
        // Ensure installment amount is > 0 as per new logic
        double amount = Math.max(1.0, random.nextDouble() * MAX_INSTALLMENT_AMOUNT);
        return new Installment("P" + instNum, Math.round(amount * 100.0)/100.0);
    }

    private static Contract createRandomContract(int contractNum) {
        int numInstallments = 1 + random.nextInt(MAX_INSTALLMENTS_PER_CONTRACT);
        List<Installment> installments = new ArrayList<>();
        for (int i = 0; i < numInstallments; i++) {
            installments.add(createRandomInstallment(i + 1));
        }
        double debtBalance = Math.max(100.0, random.nextDouble() * MAX_DEBT_BALANCE);
        return new Contract("C" + contractNum, Math.round(debtBalance*100.0)/100.0, installments);
    }

    private static List<Contract> generateContracts(int numContracts) {
        List<Contract> contracts = new ArrayList<>();
        for (int i = 0; i < numContracts; i++) {
            contracts.add(createRandomContract(i + 1));
        }
        return contracts;
    }

    public static void main(String[] args) {
        System.out.println("Starting benchmark...");
        System.out.println("Num Contracts | Exponential Time (ms) | Knapsack Time (ms)");
        System.out.println("----------------------------------------------------------");

        int[] contractCounts = {5, 10, 12, 15, 18, 20, 22}; // Add 22 for more knapsack data

        for (int count : contractCounts) {
            List<Contract> contracts = generateContracts(count);

            // Calculate a reasonable availableFunds for the dataset
            double totalMinCostSum = 0;
            // Need to use a temporary list for preprocessing as candidates list is modified by some methods if passed around directly
            List<ContractSavingCandidate> tempCandidatesForFunds = PaymentAllocatorLogic.preprocessContracts(new ArrayList<>(contracts));
            if (tempCandidatesForFunds.isEmpty() && count > 0) {
                System.out.printf("%-13d | %-21s | %-16s%n", count, "SKIP (no candidates)", "SKIP (no candidates)");
                continue;
            }
            for (ContractSavingCandidate cand : tempCandidatesForFunds) {
                totalMinCostSum += cand.getMinInstallmentCost();
            }
            double availableFunds = Math.max(50.0, totalMinCostSum * AVAILABLE_FUNDS_SCALAR); // Ensure some funds
            availableFunds = Math.round(availableFunds * 100.0)/100.0;


            List<ContractSavingCandidate> candidates = PaymentAllocatorLogic.preprocessContracts(new ArrayList<>(contracts));
            if (candidates.isEmpty() && count > 0) { // Should be caught above, but defensive
                System.out.printf("%-13d | %-21s | %-16s%n", count, "SKIP (no candidates)", "SKIP (no candidates)");
                continue;
            }

            long startTimeExp = 0, endTimeExp = 0;
            long durationExp = -1; // Default to -1, meaning not run or error initially

            if (count <= 20) { // Limit exponential to avoid excessive runtimes
                try {
                    startTimeExp = System.nanoTime();
                    PaymentAllocatorLogic.findBestCombinationExponential(new ArrayList<>(candidates), availableFunds);
                    endTimeExp = System.nanoTime();
                    durationExp = TimeUnit.NANOSECONDS.toMillis(endTimeExp - startTimeExp);
                } catch (Exception e) {
                    durationExp = -2; // Indicates an error during execution
                }
            } else {
                durationExp = -999; // Indicates skipped due to size
            }

            long durationKnapsack;
            try {
                long startTimeKnapsack = System.nanoTime();
                PaymentAllocatorLogic.findBestCombinationKnapsack(new ArrayList<>(candidates), availableFunds);
                long endTimeKnapsack = System.nanoTime();
                durationKnapsack = TimeUnit.NANOSECONDS.toMillis(endTimeKnapsack - startTimeKnapsack);
            } catch (Exception e) {
                durationKnapsack = -2; // Indicates an error
            }

            String expResultString;
            if (durationExp == -999) {
                expResultString = "SKIP (>20)";
            } else if (durationExp == -2) {
                expResultString = "ERROR";
            } else if (durationExp == -1 && count <= 20) { // Should not happen if try-catch is used
                 expResultString = "NOT RUN";
            }
            else {
                expResultString = String.valueOf(durationExp);
            }

            String knapsackResultString = (durationKnapsack == -2) ? "ERROR" : String.valueOf(durationKnapsack);

            System.out.printf("%-13d | %-21s | %-16s%n",
                              count,
                              expResultString,
                              knapsackResultString);
        }
        System.out.println("----------------------------------------------------------");
        System.out.println("Benchmark finished.");
    }
}
