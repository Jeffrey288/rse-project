package ch.ethz.rse.integration.tests;

import ch.ethz.rse.Frog;

// expected results:
// NON_NEGATIVE SAFE
// ITEM_PROFIT UNSAFE
// OVERALL_PROFIT UNSAFE
public class Complex_Test_Unsafe {

  public static void m1() {
    int i = 1;
    int j = -2;
    Frog frog_with_hat = new Frog((int) (1+3/4.0+2)*1); // 3
    frog_with_hat.sell(5);
    frog_with_hat.sell(6);

    Frog frog_with_pants = new Frog(2);
    frog_with_pants.sell(2);

    Frog frog_with_big_tongue = new Frog(40);

    for (int k = 0; k + i < 100; k++) {
      i *= -1 * (i + j);
      if (i > 3 || k > 10) {
        frog_with_big_tongue = new Frog(20);
      } else {
        frog_with_big_tongue = new Frog(10);
      }
      frog_with_big_tongue.sell(15);
    }
    if (i < 1) {
      frog_with_hat = frog_with_pants;
    } else if (i > 1 || (i != 3 && i < 6)) {
      frog_with_hat = frog_with_big_tongue;
    }
    frog_with_hat.sell(5);
  }
}