package com.example;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

public class PaymentAllocatorLogicTest {

    // Helper to create a contract
    private Contract createContract(String id, double debt, Installment... installments) {
        return new Contract(id, debt, Arrays.asList(installments));
    }

    // Helper to create an installment
    private Installment inst(String id, double amount) {
        return new Installment(id, amount);
    }

    @Test
    void testSimpleOptimalChoice() {
        List<Contract> contracts = Arrays.asList(
            createContract("C1", 1000, inst("P1_1", 50)), // Cheaper, more debt
            createContract("C2", 500, inst("P2_1", 60))
        );
        double availableFunds = 70;
        List<ContractSavingCandidate> candidates = PaymentAllocatorLogic.preprocessContracts(contracts);
        Optional<CombinationResult> result = PaymentAllocatorLogic.findBestCombinationExponential(candidates, availableFunds);

        assertTrue(result.isPresent(), "Should find a solution.");
        assertEquals(1000, result.get().getTotalDebtBalance(), 0.01, "Should protect C1's debt.");
        assertEquals(50, result.get().getTotalCost(), 0.01, "Should cost 50.");
        assertEquals(1, result.get().getChosenCandidates().size(), "Should choose 1 contract.");
        assertEquals("C1", result.get().getChosenCandidates().get(0).getContractId(), "Should choose C1.");
    }

    @Test
    void testUsersScenario_Fund10() {
        // C1: debt 500, installment 10
        // C2: debt 500, installment 10
        // C3: debt 700, installment 10
        // availableFunds = 10. Expected: C3 (protects 700).
        List<Contract> contracts = Arrays.asList(
            createContract("C1", 500, inst("C1_P1", 10)),
            createContract("C2", 500, inst("C2_P1", 10)),
            createContract("C3", 700, inst("C3_P1", 10))
        );
        double availableFunds = 10;
        List<ContractSavingCandidate> candidates = PaymentAllocatorLogic.preprocessContracts(contracts);
        Optional<CombinationResult> result = PaymentAllocatorLogic.findBestCombinationExponential(candidates, availableFunds);

        assertTrue(result.isPresent());
        assertEquals(700, result.get().getTotalDebtBalance(), 0.01);
        assertEquals(10, result.get().getTotalCost(), 0.01);
        assertEquals("C3", result.get().getChosenCandidates().get(0).getContractId());
    }

    @Test
    void testUsersScenario_Fund19_StillC3() { // Edge case, not enough for C1+C2
        List<Contract> contracts = Arrays.asList(
            createContract("C1", 500, inst("C1_P1", 10)),
            createContract("C2", 500, inst("C2_P1", 10)),
            createContract("C3", 700, inst("C3_P1", 10))
        );
        double availableFunds = 19; // Not enough for C1+C2 (cost 20)
        List<ContractSavingCandidate> candidates = PaymentAllocatorLogic.preprocessContracts(contracts);
        Optional<CombinationResult> result = PaymentAllocatorLogic.findBestCombinationExponential(candidates, availableFunds);

        assertTrue(result.isPresent());
        assertEquals(700, result.get().getTotalDebtBalance(), 0.01); // C3 is still best choice under 19
        assertEquals(10, result.get().getTotalCost(), 0.01);
        assertEquals("C3", result.get().getChosenCandidates().get(0).getContractId());
    }


    @Test
    void testUsersScenario_Fund20() {
        // C1: debt 500, installment 10
        // C2: debt 500, installment 10
        // C3: debt 700, installment 10
        // availableFunds = 20. Expected: C1+C2 (protects 1000).
        List<Contract> contracts = Arrays.asList(
            createContract("C1", 500, inst("C1_P1", 10)),
            createContract("C2", 500, inst("C2_P1", 10)),
            createContract("C3", 700, inst("C3_P1", 10))
        );
        double availableFunds = 20;
        List<ContractSavingCandidate> candidates = PaymentAllocatorLogic.preprocessContracts(contracts);
        Optional<CombinationResult> result = PaymentAllocatorLogic.findBestCombinationExponential(candidates, availableFunds);

        assertTrue(result.isPresent());
             assertEquals(1200, result.get().getTotalDebtBalance(), 0.01, "Should protect C1+C3 debt.");
             assertEquals(20, result.get().getTotalCost(), 0.01, "Should cost 20 for C1+C3.");
        assertEquals(2, result.get().getChosenCandidates().size());
        List<String> chosenIds = result.get().getChosenCandidates().stream()
                                        .map(ContractSavingCandidate::getContractId)
                                        .sorted()
                                        .collect(Collectors.toList());
             assertLinesMatch(Arrays.asList("C1", "C3"), chosenIds, "Should choose C1 and C3");
    }

    @Test
    void testNoContractAffordable() {
        List<Contract> contracts = Arrays.asList(
            createContract("C1", 1000, inst("P1_1", 100)),
            createContract("C2", 2000, inst("P2_1", 150))
        );
        double availableFunds = 50;
        List<ContractSavingCandidate> candidates = PaymentAllocatorLogic.preprocessContracts(contracts);
        Optional<CombinationResult> result = PaymentAllocatorLogic.findBestCombinationExponential(candidates, availableFunds);

        assertFalse(result.isPresent(), "Should not find any affordable solution.");
    }

    @Test
    void testAllContractsAffordable() {
        List<Contract> contracts = Arrays.asList(
            createContract("C1", 100, inst("P1_1", 10)),
            createContract("C2", 200, inst("P2_1", 20))
        );
        double availableFunds = 100; // Enough for both
        List<ContractSavingCandidate> candidates = PaymentAllocatorLogic.preprocessContracts(contracts);
        Optional<CombinationResult> result = PaymentAllocatorLogic.findBestCombinationExponential(candidates, availableFunds);

        assertTrue(result.isPresent());
        assertEquals(300, result.get().getTotalDebtBalance(), 0.01);
        assertEquals(30, result.get().getTotalCost(), 0.01);
        assertEquals(2, result.get().getChosenCandidates().size());
    }

    @Test
    void testEmptyContractList() {
        List<Contract> contracts = new ArrayList<>();
        double availableFunds = 100;
        List<ContractSavingCandidate> candidates = PaymentAllocatorLogic.preprocessContracts(contracts);
        Optional<CombinationResult> result = PaymentAllocatorLogic.findBestCombinationExponential(candidates, availableFunds);

        assertTrue(result.isPresent(), "Result should be present (empty combination).");
        assertEquals(0, result.get().getTotalDebtBalance(), 0.01, "Debt should be 0 for empty combination.");
        assertEquals(0, result.get().getTotalCost(), 0.01, "Cost should be 0 for empty combination.");
        assertTrue(result.get().getChosenCandidates().isEmpty(), "Chosen candidates should be empty.");
    }

    @Test
    void testAvailableFundsZero() {
        List<Contract> contracts = Arrays.asList(
            createContract("C1", 100, inst("P1_1", 10)),
            createContract("C2", 200, inst("P2_1", 0)) // C2 is free to save
        );
        double availableFunds = 0;
        List<ContractSavingCandidate> candidates = PaymentAllocatorLogic.preprocessContracts(contracts);
        Optional<CombinationResult> result = PaymentAllocatorLogic.findBestCombinationExponential(candidates, availableFunds);

        assertFalse(result.isPresent(), "Result should be Optional.empty() as 0-cost installments are ignored and C1 is unaffordable.");
    }

    @Test
    void testMultipleInstallmentsCheapestSelected() {
       List<Contract> contracts = Arrays.asList(
           createContract("C1", 1000, inst("P1_1", 50), inst("P1_2", 40)), // Cheapest is 40
           createContract("C2", 500, inst("P2_1", 60))
       );
       double availableFunds = 50; // Enough for C1's cheapest (40)
       List<ContractSavingCandidate> candidates = PaymentAllocatorLogic.preprocessContracts(contracts);
       // First, check preprocess part
       assertEquals(40, candidates.stream().filter(c -> c.getContractId().equals("C1")).findFirst().get().getMinInstallmentCost(), 0.01);

       Optional<CombinationResult> result = PaymentAllocatorLogic.findBestCombinationExponential(candidates, availableFunds);

       assertTrue(result.isPresent());
       assertEquals(1000, result.get().getTotalDebtBalance(), 0.01);
       assertEquals(40, result.get().getTotalCost(), 0.01);
       assertEquals("C1", result.get().getChosenCandidates().get(0).getContractId());
   }

    // --- Tests for findBestCombinationKnapsack ---

    @Test
    void testSimpleOptimalChoice_Knapsack() {
        List<Contract> contracts = Arrays.asList(
            createContract("C1", 1000, inst("P1_1", 50)), // Cheaper, more debt
            createContract("C2", 500, inst("P2_1", 60))
        );
        double availableFunds = 70;
        List<ContractSavingCandidate> candidates = PaymentAllocatorLogic.preprocessContracts(contracts);
        Optional<CombinationResult> result = PaymentAllocatorLogic.findBestCombinationKnapsack(candidates, availableFunds);

        assertTrue(result.isPresent(), "Knapsack: Should find a solution.");
        assertEquals(1000, result.get().getTotalDebtBalance(), 0.01, "Knapsack: Should protect C1's debt.");
        assertEquals(50, result.get().getTotalCost(), 0.01, "Knapsack: Should cost 50.");
        assertEquals(1, result.get().getChosenCandidates().size(), "Knapsack: Should choose 1 contract.");
        assertEquals("C1", result.get().getChosenCandidates().get(0).getContractId(), "Knapsack: Should choose C1.");
    }

    @Test
    void testUsersScenario_Fund10_Knapsack() {
        List<Contract> contracts = Arrays.asList(
            createContract("C1", 500, inst("C1_P1", 10)),
            createContract("C2", 500, inst("C2_P1", 10)),
            createContract("C3", 700, inst("C3_P1", 10))
        );
        double availableFunds = 10;
        List<ContractSavingCandidate> candidates = PaymentAllocatorLogic.preprocessContracts(contracts);
        Optional<CombinationResult> result = PaymentAllocatorLogic.findBestCombinationKnapsack(candidates, availableFunds);

        assertTrue(result.isPresent(), "Knapsack: Result should be present");
        assertEquals(700, result.get().getTotalDebtBalance(), 0.01, "Knapsack: Should protect C3's debt.");
        assertEquals(10, result.get().getTotalCost(), 0.01, "Knapsack: Should cost 10 for C3.");
        assertEquals(1, result.get().getChosenCandidates().size(), "Knapsack: Should choose 1 contract.");
        assertEquals("C3", result.get().getChosenCandidates().get(0).getContractId(), "Knapsack: Should choose C3.");
    }

    @Test
    void testUsersScenario_Fund19_StillC3_Knapsack() {
        List<Contract> contracts = Arrays.asList(
            createContract("C1", 500, inst("C1_P1", 10)),
            createContract("C2", 500, inst("C2_P1", 10)),
            createContract("C3", 700, inst("C3_P1", 10))
        );
        double availableFunds = 19;
        List<ContractSavingCandidate> candidates = PaymentAllocatorLogic.preprocessContracts(contracts);
        Optional<CombinationResult> result = PaymentAllocatorLogic.findBestCombinationKnapsack(candidates, availableFunds);

        assertTrue(result.isPresent(), "Knapsack: Result should be present");
        assertEquals(700, result.get().getTotalDebtBalance(), 0.01, "Knapsack: C3 is still best choice under 19.");
        assertEquals(10, result.get().getTotalCost(), 0.01, "Knapsack: Cost for C3.");
        assertEquals(1, result.get().getChosenCandidates().size(), "Knapsack: Should choose 1 contract.");
        assertEquals("C3", result.get().getChosenCandidates().get(0).getContractId(), "Knapsack: Should choose C3.");
    }

    @Test
    void testUsersScenario_Fund20_Knapsack() {
        List<Contract> contracts = Arrays.asList(
            createContract("C1", 500, inst("C1_P1", 10)),
            createContract("C2", 500, inst("C2_P1", 10)),
            createContract("C3", 700, inst("C3_P1", 10))
        );
        double availableFunds = 20;
        List<ContractSavingCandidate> candidates = PaymentAllocatorLogic.preprocessContracts(contracts);
        Optional<CombinationResult> result = PaymentAllocatorLogic.findBestCombinationKnapsack(candidates, availableFunds);

        assertTrue(result.isPresent(), "Knapsack: Result should be present");
        // With Knapsack, C1+C3 (1200 debt, 20 cost) or C2+C3 (1200 debt, 20 cost) are optimal and better than C1+C2 (1000 debt, 20 cost)
        // The DP table should find 1200.
        assertEquals(1200, result.get().getTotalDebtBalance(), 0.01, "Knapsack: Should protect C1+C3 or C2+C3 debt (1200).");
        assertEquals(20, result.get().getTotalCost(), 0.01, "Knapsack: Should cost 20.");
        assertEquals(2, result.get().getChosenCandidates().size(), "Knapsack: Should choose 2 contracts.");
        List<String> chosenIds = result.get().getChosenCandidates().stream()
                                        .map(ContractSavingCandidate::getContractId)
                                        .sorted()
                                        .collect(Collectors.toList());
        // Either C1,C3 or C2,C3 are valid. The DP reconstruction might pick one.
        // For this test, let's assert that the debt is 1200 and size is 2.
        // Specific IDs depend on tie-breaking in reconstruction if multiple sets give 1200.
        // A common DP reconstruction picks the one involving the last item if it leads to optimum.
        // If items are C1, C2, C3: C3 will be considered. value(C3) + dp[n-1][W-w(C3)] vs dp[n-1][W]
        // 700 + dp[items C1,C2][20-10=10]. dp[items C1,C2][10] can be 500 (either C1 or C2). So 700+500=1200.
        // So {C3, C1} or {C3, C2} is likely.
        assertTrue(chosenIds.equals(Arrays.asList("C1", "C3")) || chosenIds.equals(Arrays.asList("C2", "C3")), "Knapsack: Chosen IDs should be {C1,C3} or {C2,C3}");
    }

    @Test
    void testNoContractAffordable_Knapsack() {
        List<Contract> contracts = Arrays.asList(
            createContract("C1", 1000, inst("P1_1", 100)),
            createContract("C2", 2000, inst("P2_1", 150))
        );
        double availableFunds = 50;
        List<ContractSavingCandidate> candidates = PaymentAllocatorLogic.preprocessContracts(contracts);
        Optional<CombinationResult> result = PaymentAllocatorLogic.findBestCombinationKnapsack(candidates, availableFunds);

        // Knapsack logic was updated to return Optional.empty() in this case
        assertFalse(result.isPresent(), "Knapsack: Should not find any affordable solution.");
    }

    @Test
    void testAllContractsAffordable_Knapsack() {
        List<Contract> contracts = Arrays.asList(
            createContract("C1", 100, inst("P1_1", 10)),
            createContract("C2", 200, inst("P2_1", 20))
        );
        double availableFunds = 100; // Enough for both
        List<ContractSavingCandidate> candidates = PaymentAllocatorLogic.preprocessContracts(contracts);
        Optional<CombinationResult> result = PaymentAllocatorLogic.findBestCombinationKnapsack(candidates, availableFunds);

        assertTrue(result.isPresent(), "Knapsack: Result should be present.");
        assertEquals(300, result.get().getTotalDebtBalance(), 0.01, "Knapsack: Total debt should be 300.");
        assertEquals(30, result.get().getTotalCost(), 0.01, "Knapsack: Total cost should be 30.");
        assertEquals(2, result.get().getChosenCandidates().size(), "Knapsack: Should choose 2 contracts.");
    }

    @Test
    void testEmptyContractList_Knapsack() {
        List<Contract> contracts = new ArrayList<>();
        double availableFunds = 100;
        List<ContractSavingCandidate> candidates = PaymentAllocatorLogic.preprocessContracts(contracts);
        Optional<CombinationResult> result = PaymentAllocatorLogic.findBestCombinationKnapsack(candidates, availableFunds);

        assertTrue(result.isPresent(), "Knapsack: Result should be present (empty combination).");
        assertEquals(0, result.get().getTotalDebtBalance(), 0.01, "Knapsack: Debt should be 0 for empty combination.");
        assertEquals(0, result.get().getTotalCost(), 0.01, "Knapsack: Cost should be 0 for empty combination.");
        assertTrue(result.get().getChosenCandidates().isEmpty(), "Knapsack: Chosen candidates should be empty.");
    }

    @Test
    void testAvailableFundsZero_Knapsack() {
        List<Contract> contracts = Arrays.asList(
            createContract("C1", 100, inst("P1_1", 10)),
            createContract("C2", 200, inst("P2_1", 0)) // C2 is free to save
        );
        double availableFunds = 0;
        List<ContractSavingCandidate> candidates = PaymentAllocatorLogic.preprocessContracts(contracts);
        Optional<CombinationResult> result = PaymentAllocatorLogic.findBestCombinationKnapsack(candidates, availableFunds);

        assertTrue(result.isPresent(), "Knapsack: Result should be present (empty combination).");
        assertEquals(0, result.get().getTotalDebtBalance(), 0.01, "Knapsack: Debt should be 0 as 0-cost installments are ignored.");
        assertEquals(0, result.get().getTotalCost(), 0.01, "Knapsack: Cost should be 0.");
        assertTrue(result.get().getChosenCandidates().isEmpty(), "Knapsack: Chosen candidates should be empty.");
    }

    @Test
    void testMultipleInstallmentsCheapestSelected_Knapsack() {
       List<Contract> contracts = Arrays.asList(
           createContract("C1", 1000, inst("P1_1", 50), inst("P1_2", 40)), // Cheapest is 40
           createContract("C2", 500, inst("P2_1", 60))
       );
       double availableFunds = 50;
       List<ContractSavingCandidate> candidates = PaymentAllocatorLogic.preprocessContracts(contracts);
       assertEquals(40, candidates.stream().filter(c -> c.getContractId().equals("C1")).findFirst().get().getMinInstallmentCost(), 0.01);

       Optional<CombinationResult> result = PaymentAllocatorLogic.findBestCombinationKnapsack(candidates, availableFunds);

       assertTrue(result.isPresent(), "Knapsack: Result should be present.");
       assertEquals(1000, result.get().getTotalDebtBalance(), 0.01, "Knapsack: Should protect C1's debt.");
       assertEquals(40, result.get().getTotalCost(), 0.01, "Knapsack: Cost should be 40 for C1.");
       assertEquals(1, result.get().getChosenCandidates().size(), "Knapsack: Should choose 1 contract.");
       assertEquals("C1", result.get().getChosenCandidates().get(0).getContractId(), "Knapsack: Should choose C1.");
   }
}
