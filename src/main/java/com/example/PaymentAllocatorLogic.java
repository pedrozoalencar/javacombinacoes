package com.example;

import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import java.util.stream.Collectors;

public class PaymentAllocatorLogic {

    /**
     * Preprocesses a list of contracts to find those that can be saved
     * and the minimum cost to save them.
     *
     * @param contracts The list of all contracts.
     * @return A list of ContractSavingCandidate objects, representing contracts
     *         that have open installments and can potentially be saved.
     */
    public static List<ContractSavingCandidate> preprocessContracts(List<Contract> contracts) {
        if (contracts == null) {
            return new ArrayList<>();
        }

        return contracts.stream()
            .map(contract -> {
                Optional<Double> cheapestCostOpt = contract.getCheapestOpenInstallmentCost();
                if (cheapestCostOpt.isPresent()) {
                    return new ContractSavingCandidate(
                        contract.getContractId(),
                        contract.getDebtBalance(),
                        cheapestCostOpt.get()
                    );
                }
                return null; // Will be filtered out by .filter(candidate -> candidate != null)
            })
            .filter(candidate -> candidate != null)
            .collect(Collectors.toList());
    }

    public static List<CombinationResult> generateAllCombinations(List<ContractSavingCandidate> candidates) {
        List<CombinationResult> allCombinations = new ArrayList<>();
        if (candidates == null || candidates.isEmpty()) {
            // Add an empty combination (representing choosing nothing)
            allCombinations.add(new CombinationResult(new ArrayList<>(), 0, 0));
            return allCombinations;
        }

        generateCombinationsRecursive(candidates, 0, new ArrayList<>(), allCombinations);
        return allCombinations;
    }

    private static void generateCombinationsRecursive(
            List<ContractSavingCandidate> candidates,
            int index,
            List<ContractSavingCandidate> currentCombination,
            List<CombinationResult> allCombinations) {

        if (index == candidates.size()) {
            double currentTotalCost = 0;
            double currentTotalDebt = 0;
            for (ContractSavingCandidate candidate : currentCombination) {
                currentTotalCost += candidate.getMinInstallmentCost();
                currentTotalDebt += candidate.getDebtBalance();
            }
            allCombinations.add(new CombinationResult(new ArrayList<>(currentCombination), currentTotalCost, currentTotalDebt));
            return;
        }

        // Decision 1: Include the candidate at the current index
        currentCombination.add(candidates.get(index));
        generateCombinationsRecursive(candidates, index + 1, currentCombination, allCombinations);
        currentCombination.remove(currentCombination.size() - 1); // Backtrack

        // Decision 2: Exclude the candidate at the current index
        generateCombinationsRecursive(candidates, index + 1, currentCombination, allCombinations);
    }

    /**
     * Finds the best combination of contracts to pay off using the exponential (brute-force) approach.
     * It generates all possible combinations, filters them by available funds,
     * and then selects the one that maximizes the total debt balance protected.
     *
     * @param candidates The list of ContractSavingCandidate objects.
     * @param availableFunds The total amount of money available for payments.
     * @return An Optional containing the best CombinationResult if one is found,
     *         otherwise an empty Optional.
     */
    public static Optional<CombinationResult> findBestCombinationExponential(
            List<ContractSavingCandidate> candidates,
            double availableFunds) {

        List<CombinationResult> allCombinations = generateAllCombinations(candidates);

        CombinationResult bestCombination = null;
        double maxDebtProtected = -1.0; // Initialize with a value lower than any possible positive debt

        for (CombinationResult combo : allCombinations) {
            if (combo.getTotalCost() <= availableFunds) {
                if (combo.getTotalDebtBalance() > maxDebtProtected) {
                    maxDebtProtected = combo.getTotalDebtBalance();
                    bestCombination = combo;
                }
                // Tie-breaking: If debt protected is the same, current problem description doesn't specify.
                // This implementation will pick the first one encountered with max debt.
                // If one wanted to prefer lower cost on tie, an additional check would be:
                // else if (combo.getTotalDebtBalance() == maxDebtProtected) {
                //     if (bestCombination == null || combo.getTotalCost() < bestCombination.getTotalCost()) {
                //         bestCombination = combo;
                //     }
                // }
            }
        }

        // If the best combination found is the empty one (cost 0, debt 0),
        // and the original list of candidates was not empty,
        // then it means no actual contract was affordable/chosen.
        // In this specific scenario, conform to tests that expect Optional.empty().
        if (bestCombination != null &&
            bestCombination.getChosenCandidates().isEmpty() &&
            bestCombination.getTotalDebtBalance() == 0 && // ensure it's the "do nothing" option
            !candidates.isEmpty()) {
            return Optional.empty();
        }

        return Optional.ofNullable(bestCombination);
    }

    // --- Knapsack DP Approach ---

    private static class KnapsackItem {
        String id;
        double originalCost;
        int scaledWeight;
        double value;        // Debt balance
        ContractSavingCandidate originalCandidate;

        KnapsackItem(ContractSavingCandidate candidate) {
            this.id = candidate.getContractId();
            this.originalCost = candidate.getMinInstallmentCost();
            // Ensure non-negative weights, handle potential rounding issues if cost is extremely small
            this.scaledWeight = Math.max(0, (int) Math.round(candidate.getMinInstallmentCost() * 100.0));
            this.value = candidate.getDebtBalance();
            this.originalCandidate = candidate;
        }
    }

    public static Optional<CombinationResult> findBestCombinationKnapsack(
            List<ContractSavingCandidate> candidates,
            double availableFunds) {

        if (candidates == null || candidates.isEmpty()) {
            if (availableFunds >= 0) {
                return Optional.of(new CombinationResult(new ArrayList<>(), 0, 0));
            } else {
                // Consistent with refined exponential logic: if funds < 0, and no candidates, empty is appropriate.
                // Or rather, if funds are negative, no combination (even empty) is valid unless cost is also negative.
                return Optional.empty();
            }
        }

        if (availableFunds < 0) return Optional.empty(); // Cannot afford anything with negative funds.
        int scaledCapacity = (int) Math.round(availableFunds * 100.0);
        // if (scaledCapacity < 0) scaledCapacity = 0; // Math.round can make it 0 if availableFunds is tiny, ensure non-negative.
                                                 // This is covered by availableFunds < 0 check already for negative.
                                                 // For tiny positive funds, scaledCapacity could be 0.

        List<KnapsackItem> items = new ArrayList<>();
        for (ContractSavingCandidate candidate : candidates) {
            // Only consider items with non-negative scaled weight and non-negative value.
            // A cost of 0 is okay (scaledWeight 0). A debt balance of 0 might be okay if it enables something else or is free.
            // Let's assume debt balance (value) must be positive to be worth "saving".
            // Or, if an item is free (cost 0) and has 0 debt, it's harmless to include.
            // For 0/1 knapsack, items with weight 0 can be tricky if not handled well.
            // If scaledWeight is 0 and value is positive, it's infinitely good.
            // Let's filter out items that have 0 scaled weight but positive value here, handle them separately.
            // For now, stick to items that have scaledWeight > 0 or (scaledWeight == 0 && value == 0)

            double candidateScaledCost = candidate.getMinInstallmentCost() * 100.0;
            if (candidateScaledCost >= -1e-9) { // Effectively non-negative costs
                KnapsackItem newItem = new KnapsackItem(candidate);
                // Standard knapsack assumes positive weights. If weight is 0:
                // - if value is also 0, it's trivial, can be ignored or added without affecting capacity.
                // - if value is positive, it's an edge case (free benefit). Add to solution and reduce problem.
                // For simplicity here, let's assume standard knapsack items (positive weight usually).
                // The provided KnapsackItem constructor uses Math.max(0, ...) for scaledWeight.
                // If an item has scaledWeight 0 and positive value, it should be picked.
                // The current DP doesn't explicitly handle this "always pick" scenario well if many such items.
                // For now, we'll let them go into DP table. If scaledWeight is 0, they'll be chosen if value > 0.
                items.add(newItem);
            }
        }

        // If, after filtering, no items remain, or if initial candidates list was empty (already handled).
        if (items.isEmpty()) {
            if (availableFunds >= 0) { // Still return empty set if funds are okay
                return Optional.of(new CombinationResult(new ArrayList<>(), 0, 0));
            } else { // Should have been caught by availableFunds < 0 earlier
                return Optional.empty();
            }
        }

        double[][] dp = new double[items.size() + 1][scaledCapacity + 1];

        for (int i = 0; i <= items.size(); i++) {
            for (int w = 0; w <= scaledCapacity; w++) {
                if (i == 0 || w == 0) {
                    dp[i][w] = 0;
                } else {
                    KnapsackItem currentItem = items.get(i-1);
                    if (currentItem.scaledWeight <= w) {
                        // If currentItem.scaledWeight is 0, w - currentItem.scaledWeight is w.
                        // This means dp[i-1][w] vs currentItem.value + dp[i-1][w].
                        // If value > 0, it will always pick currentItem if scaledWeight is 0.
                        dp[i][w] = Math.max(dp[i-1][w],
                                            currentItem.value + dp[i-1][w - currentItem.scaledWeight]);
                    } else {
                        dp[i][w] = dp[i-1][w];
                    }
                }
            }
        }

        double maxTotalDebt = dp[items.size()][scaledCapacity];

        // Logic for when to return Optional.empty() vs. an empty CombinationResult:
        // 1. If maxTotalDebt is effectively zero:
        //    AND original candidates list was not empty (implies "items" list might also have been non-empty before DP)
        //    AND availableFunds were positive (i.e. we had money to spend)
        //    AND there was at least one candidate/item with positive debt balance (value)
        //    THEN it means no valuable item could be afforded/chosen.
        double maxDebt = dp[items.size()][scaledCapacity];

        List<ContractSavingCandidate> chosen_candidates = new ArrayList<>();
        double total_actual_cost = 0;
        int current_w = scaledCapacity;

        for (int i = items.size(); i > 0; i--) {
            KnapsackItem currentItem = items.get(i-1);
            double val_without_item = dp[i-1][current_w];
            double val_with_item = -1.0; // Sentinel if item cannot fit or not considered

            if (current_w >= currentItem.scaledWeight) {
                val_with_item = dp[i-1][current_w - currentItem.scaledWeight] + currentItem.value;
            }

            // Check if taking the item is the reason dp[i][current_w] has its value
            // This means dp[i][current_w] should be approximately val_with_item
            // and val_with_item should be greater or equal to val_without_item (within tolerance)
            if (current_w >= currentItem.scaledWeight &&
                Math.abs(dp[i][current_w] - val_with_item) < 1e-9 &&
                val_with_item >= val_without_item - 1e-9) {

                chosen_candidates.add(currentItem.originalCandidate);
                total_actual_cost += currentItem.originalCost;
                current_w -= currentItem.scaledWeight;
            }
            // Else, the item was not taken (or taking it was not better/equal), so dp[i][current_w] came from dp[i-1][current_w]
        }
        java.util.Collections.reverse(chosen_candidates);

        // IMPORTANT: finalMaxDebtProtected is directly from the DP table.
        double finalMaxDebtProtected = dp[items.size()][scaledCapacity];

        // System.err.println("Knapsack: finalMaxDebtProtected from DP table = " + finalMaxDebtProtected + " for capacity " + scaledCapacity + " with items " + items.size());

        if (finalMaxDebtProtected < 1e-9) { // Max possible debt from DP table is effectively zero
            // Logic consistent with the refined exponential version for when to return Optional.empty()
            // vs. an empty CombinationResult (0 debt, 0 cost).
            if (!candidates.isEmpty() && availableFunds > 1e-9) {
                boolean anyPositiveDebtOriginalCandidate = false;
                for (ContractSavingCandidate cand : candidates) { // Check original candidates passed to method
                    if (cand.getDebtBalance() > 1e-9) {
                        anyPositiveDebtOriginalCandidate = true;
                        break;
                    }
                }
                if (anyPositiveDebtOriginalCandidate) {
                    // There were positive-debt candidates and funds, but DP result is 0.
                    // This implies no valuable items were chosen.
                    return Optional.empty();
                }
            }
            // Otherwise, 0 debt is the correct optimal (e.g., no candidates, or no positive-debt candidates)
            // chosen_candidates should be empty if finalMaxDebtProtected is 0, ensure this consistency.
            return Optional.of(new CombinationResult(new ArrayList<>(), 0, 0));
        } else {
            // finalMaxDebtProtected > 0.
            // The chosen_candidates list and total_actual_cost should reflect this.
            // If chosen_candidates is empty at this point AND finalMaxDebtProtected > 0,
            // it indicates a fundamental flaw in the reconstruction logic for this case.
            // The tests will fail if total_actual_cost or chosen_candidates are inconsistent with finalMaxDebtProtected.
            // For now, we pass the reconstructed list and cost.
            return Optional.of(new CombinationResult(chosen_candidates, total_actual_cost, finalMaxDebtProtected));
        }
    }
}
