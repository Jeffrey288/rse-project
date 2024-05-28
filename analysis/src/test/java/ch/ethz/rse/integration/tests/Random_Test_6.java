package ch.ethz.rse.integration.tests;

import ch.ethz.rse.Frog;

// expected results:
// NON_NEGATIVE SAFE
// ITEM_PROFIT UNSAFE (SAFE)
// OVERALL_PROFIT SAFE (since it doesn't terminate)
public class Random_Test_6 {

  public static void m1(int i) {
    while (true) {
      Frog frog = new Frog(10);
      if (i > 10) {
        frog.sell(i++);
        frog.sell(i--);
      } else {
        frog.sell(-1 * (i++ - 20));
        frog.sell(-1 * (i-- - 20));
      }
    }
  }
}