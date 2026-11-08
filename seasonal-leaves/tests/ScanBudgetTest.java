package ua.empirecraft.leaves;

import java.util.concurrent.atomic.AtomicLong;

public final class ScanBudgetTest {
  static int checks;

  static void check(boolean value) {
    checks++;
    if (!value) throw new AssertionError("budget check " + checks);
  }

  public static void main(String[] args) {
    var clock = new AtomicLong(1000);
    var budget = new ScanBudget(32768, 32, 128, 1000, clock::get);
    for (int i = 0; i < 128; i++) check(budget.chunk());
    check(!budget.chunk());
    for (int i = 0; i < 32; i++) check(budget.section());
    check(!budget.section());
    for (int i = 0; i < 32768; i++) check(budget.position());
    check(!budget.position());
    budget = new ScanBudget(1000, 32, 128, 1000, clock::get);
    check(budget.position());
    clock.addAndGet(1_000_000);
    check(!budget.chunk());
    check(!budget.section());
    int tail = 0;
    while (budget.position()) tail++;
    check(tail <= 63);
    var reset = new ScanBudget(10, 1, 1, 1000, clock::get);
    check(reset.chunk());
    check(reset.section());
    check(reset.position());
    clock.addAndGet(1_000_000);
    check(!reset.time());
    System.out.println("SCAN_BUDGET_PASS " + checks + " operation, time and fresh-tick checks");
  }
}
