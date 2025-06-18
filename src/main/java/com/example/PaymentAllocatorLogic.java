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
        boolean hasPositiveValueItem = false;
        for(KnapsackItem item : items) if(item.value > 1e-9) hasPositiveValueItem = true;

        if (maxTotalDebt < 1e-9 && !candidates.isEmpty() && availableFunds > 1e-9 && hasPositiveValueItem) {
            return Optional.empty();
        }

        // If maxTotalDebt is 0 (and it passed the above check, e.g. no positive value items existed or funds were 0),
        // then we proceed to reconstruct, which will result in an empty list of chosen_candidates.

        List<ContractSavingCandidate> chosen_candidates_list = new ArrayList<>();
        double actual_total_cost = 0;
        int current_scaled_capacity = scaledCapacity;

        for (int i = items.size(); i > 0 && current_scaled_capacity >=0 ; i--) {
            // Check if item i was chosen.
            // It was chosen if dp[i][current_scaled_capacity] is different from dp[i-1][current_scaled_capacity]
            // Need to be careful with floating point comparisons.
            // A chosen item means dp[i][current_scaled_capacity] == items.get(i-1).value + dp[i-1][current_scaled_capacity - items.get(i-1).scaledWeight]
            KnapsackItem currentItem = items.get(i-1);
            double valueIfChosen = currentItem.value + dp[i-1][Math.max(0,current_scaled_capacity - currentItem.scaledWeight)];
            // Math.abs(dp[i][current_scaled_capacity] - valueIfChosen) < 1e-9
            // A simpler check: if dp[i][w] > dp[i-1][w], it means item i made a difference.
            if (dp[i][current_scaled_capacity] > dp[i-1][current_scaled_capacity] + 1e-9) { // Check if item i contributed
                 // Ensure that this path is only taken if item was affordable
                if (currentItem.scaledWeight <= current_scaled_capacity) {
                    chosen_candidates_list.add(currentItem.originalCandidate);
                    actual_total_cost += currentItem.originalCost;
                    current_scaled_capacity -= currentItem.scaledWeight;
                }
            }
        }
        java.util.Collections.reverse(chosen_candidates_list);

        // If chosen_candidates_list is empty after reconstruction, it means the optimal solution is to pick nothing.
        // This covers cases like: no items fit, all valuable items were too expensive, or all items had 0 value.
        // The maxTotalDebt would be 0 in such cases.
        // We need to return CombinationResult(emptyList, 0, 0) in this case, unless Optional.empty() was already returned.
        return Optional.of(new CombinationResult(chosen_candidates_list, actual_total_cost, dp[items.size()][scaledCapacity]));
    }
}
