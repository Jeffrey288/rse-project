package apron;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gmp.Mpq;

/**
 * Unit test for basic Apron functionality, inspired by
 * https://github.com/antoinemine/apron/blob/cf9017f99655e514b1ba336a5c56c548189ccd64/japron/apron/Test.java
 */
public class ApronTest {

    private static final Logger logger = LoggerFactory.getLogger(ApronTest.class);

    /**
     * Numerical abstract domain to use for analysis: Convex polyhedra
     */
    Manager man = new Polka(true);

    /**
     * Prepare environment with three integer variables x and y
     */
    String[] integer_names = { "x", "y" };
    String[] real_names = {};
    Environment env = new Environment(integer_names, real_names);

    ///////////////////
    // TEST ELEMENTS //
    ///////////////////
    // these three abstract elements are used for all tests in the following

    /**
     * abstract element containing no values
     */
    Abstract1 bottom;

    /**
     * abstract element containing all values
     */
    Abstract1 top;

    /**
     * abstract element containing values {(x,y) | 1 <= x <= 2 and -1 <= y <= 1}
     */
    Abstract1 xy;

    public ApronTest() throws ApronException {
        
        this.bottom = new Abstract1(man, env, true);
        this.top = new Abstract1(man, env);

        // encode 1 <= x <= 2, -1 <= y <= 1
        Interval[] box = { new Interval(1, 2), new Interval(-1, 1) };
        this.xy = new Abstract1(man, env, integer_names, box);
    }

    ////////////////////
    // TOP AND BOTTOM //
    ////////////////////

    @Test
	public void testTop() throws ApronException {
        Assertions.assertEquals("<universal>", top.toString());
    }

    @Test
    public void testBottom() throws ApronException {
        Assertions.assertEquals("<empty>", bottom.toString());
    }

    @Test
    public void testIsBottom() throws ApronException {
        Assertions.assertFalse(top.isBottom(man), "Top is not bottom");
        Assertions.assertTrue(bottom.isBottom(man), "Bottom is bottom");
    }

    @Test
    public void testIsTop() throws ApronException {
        Assertions.assertTrue(top.isTop(man), "Top is top");
        Assertions.assertFalse(bottom.isTop(man), "Bottom is not top");
    }

    ///////////////////////
    // RELATIONS OF SETS //
    ///////////////////////

    @Test
	public void testIsEqual() throws ApronException {
        Assertions.assertTrue(top.isEqual(man, top));
        Assertions.assertTrue(bottom.isEqual(man, bottom));
    }

    @Test
    public void testIsIncluded() throws ApronException {
        Assertions.assertTrue(top.isIncluded(man, top));
        Assertions.assertTrue(bottom.isIncluded(man, bottom));

        Assertions.assertTrue(bottom.isIncluded(man, top));
        Assertions.assertFalse(top.isIncluded(man, bottom));
    }

    ///////////////
    // INTERVALS //
    ///////////////

    @Test
    public void testIntervals() throws ApronException {
        String expected = "{  -1x +2 >= 0;  -1y +1 >= 0;  1y +1 >= 0;  1x -1 >= 0 }";
        Assertions.assertEquals(expected, this.xy.toString());

        Interval x = this.xy.getBound(man, "x");
        Assertions.assertEquals("[1,2]", x.toString());

        Scalar lowerBound = x.inf();
        Scalar upperBound = x.sup();
        Assertions.assertTrue(lowerBound.isEqual(1));
        Assertions.assertTrue(upperBound.isEqual(2));
    }

    /////////////////////////////////
    // EXPRESSIONS and CONSTRAINTS //
    /////////////////////////////////

    @Test
    public void testExpressions() throws ApronException {
        // encode 2
        Coeff two = new MpqScalar(2);
        Texpr1Node twoNode = new Texpr1CstNode(two);
        // encode x
        Texpr1Node x = new Texpr1VarNode("x");
        // encode 2 * x
        Texpr1Node twoX = new Texpr1BinNode(Texpr1BinNode.OP_MUL, Texpr1BinNode.RTYPE_INT, Texpr1BinNode.RDIR_ZERO, twoNode, x);
        
        // encode 2 * x >= 0
        Tcons1 constraint = new Tcons1(env, Tcons1.SUPEQ, twoX);
        Assertions.assertTrue(xy.satisfy(man, constraint));

        // encode -x >= 0
        Texpr1Node minusX = new Texpr1UnNode(Texpr1UnNode.OP_NEG, x);
        Tcons1 constraint2 = new Tcons1(env, Tcons1.SUPEQ, minusX);
        Assertions.assertFalse(xy.satisfy(man, constraint2));

        // encode y >= 0
        Texpr1Node y = new Texpr1VarNode("y");
        Tcons1 constraint3 = new Tcons1(env, Tcons1.SUPEQ, y);
        Assertions.assertFalse(xy.satisfy(man, constraint3)); // constraint may be violated
		
		// meet with constraint
		Abstract1 myBottom = xy.meetCopy(man, constraint2);
		Assertions.assertTrue(myBottom.isEqual(man, this.bottom));

		// TODO: Note that some constraints are handled imprecisely in Apron,
		// but can be expressed differently to improve precision. For example,
		// instead of encoding x - y != 0, it is possible to encode "x - y > 0"
		// and "x - y < 0", and combine these two cases appropriately.
    }

    /////////////////
    // ASSIGNMENTS //
    /////////////////

    @Test
    public void testAssign() throws ApronException {
        // encode 2 * x
        Texpr1Node twoNode = new Texpr1CstNode(new MpqScalar(2));
        Texpr1Node x = new Texpr1VarNode("x");
        Texpr1Node twoX = new Texpr1BinNode(Texpr1BinNode.OP_MUL, Texpr1BinNode.RTYPE_INT, Texpr1BinNode.RDIR_ZERO, twoNode, x);
        Texpr1Intern twoXIntern = new Texpr1Intern(env, twoX);

        Abstract1 afterAssignment = this.xy.assignCopy(man, "y", twoXIntern, null);

        // 1 <= x <= 2, y = 2*x
        Assertions.assertEquals("{  -2x +1y = 0;  -1x +2 >= 0;  1x -1 >= 0 }", afterAssignment.toString());
    }

    //////////////////////////
    // MEET, JOIN and WIDEN //
    //////////////////////////

    @Test
    public void testMeet() throws ApronException {
        Abstract1 stillTop = top.meetCopy(man, top);
        Assertions.assertTrue(top.isEqual(man, stillTop));

        Abstract1 nowBottom = top.meetCopy(man, bottom);
        Assertions.assertTrue(bottom.isEqual(man, nowBottom));
    }

    @Test
    public void testJoin() throws ApronException {
        Abstract1 stillBottom = bottom.joinCopy(man, bottom);
        Assertions.assertTrue(bottom.isEqual(man, stillBottom));

        Abstract1 nowTop = bottom.joinCopy(man, top);
        Assertions.assertTrue(top.isEqual(man, nowTop));
    }

    @Test
    public void testWiden() throws ApronException {
        Abstract1 stillBottom = this.widenFixed(bottom, bottom);
        Assertions.assertTrue(bottom.isEqual(man, stillBottom));

        Abstract1 nowTop = this.widenFixed(bottom, top);
        Assertions.assertTrue(top.isEqual(man, nowTop));
    }


    ////////////////////////////////////
    // Adding variable to environment //
    ////////////////////////////////////
    @Test
    public void testAddVariable() throws ApronException{
        // encode 1 <= x <= 2, -1 <= y <= 1
        Interval[] box = { new Interval(1, 2), new Interval(-1, 1) };
        Abstract1 old_abstr = new Abstract1(man, env, integer_names, box);

        String[] integer_new = { "z" };
        String[] real_new = {};
        Environment new_env = this.env.add(integer_new, real_new);
        String expectedEnv = "( i: { x, y, z }, r: { } )";
        Assertions.assertEquals(expectedEnv, new_env.toString());

        Abstract1 new_0 = old_abstr.changeEnvironmentCopy(man, new_env, false); // sets the new variables to  [-oo,+oo] 
        String expected0 = "{  -1x +2 >= 0;  -1y +1 >= 0;  1y +1 >= 0;  1x -1 >= 0 }";
        Assertions.assertEquals(expected0, new_0.toString());

        Abstract1 new_1 = old_abstr.changeEnvironmentCopy(man, new_env, true); // sets the new variables to 0
        String expected1 = "{  1z = 0;  -1x +2 >= 0;  -1y +1 >= 0;  1y +1 >= 0;  1x -1 >= 0 }";
        Assertions.assertEquals(expected1, new_1.toString());

    }


    //////////////////////////////////////////
    // Remove all constraints on a variable //
    //////////////////////////////////////////
    @Test
    public void testForgetVariable() throws ApronException{
        // encode 1 <= x <= 2, -1 <= y <= 1
        Interval[] box = { new Interval(1, 2), new Interval(-1, 1) };
        Abstract1 old_abstr = new Abstract1(man, env, integer_names, box);

        Abstract1 new_1 = old_abstr.forgetCopy(man, "x", false);
        // x is now set to [-oo, +oo], ie we have no information on it
        String expected1 = "{  -1y +1 >= 0;  1y +1 >= 0 }";
        Assertions.assertEquals(expected1, new_1.toString());

    }


    //////////////////////////////////////////
    // Custom Tests //
    //////////////////////////////////////////
    @Test
    public void testForgetVariable2() throws ApronException{
        // encode x + y < 2, x > 1
        // encode -(x + y - 2) > 0, x - 1 > 0
        // expected result after forgetting x: y <= -1
        Texpr1Node x = new Texpr1VarNode("x");
        Texpr1Node y = new Texpr1VarNode("y");
        Texpr1Node xPlusy = new Texpr1BinNode(Texpr1BinNode.OP_ADD, Texpr1BinNode.RTYPE_INT, Texpr1BinNode.RDIR_ZERO, y, x);
        Coeff two = new MpqScalar(2);
        Texpr1Node twoNode = new Texpr1CstNode(two);
        Texpr1Node xPlusyMinusTwo = new Texpr1BinNode(Texpr1BinNode.OP_SUB, Texpr1BinNode.RTYPE_INT, Texpr1BinNode.RDIR_ZERO, xPlusy, twoNode);
        Texpr1Node negxPlusyMinusTwo = new Texpr1UnNode(Texpr1UnNode.OP_NEG, xPlusyMinusTwo);
        Tcons1 constraint1 = new Tcons1(env, Tcons1.SUP, negxPlusyMinusTwo);

        Coeff one = new MpqScalar(1);
        Texpr1Node oneNode = new Texpr1CstNode(one);
        Texpr1Node xMinusOne = new Texpr1BinNode(Texpr1BinNode.OP_SUB, Texpr1BinNode.RTYPE_INT, Texpr1BinNode.RDIR_ZERO, x, oneNode);
        Tcons1 constraint2 = new Tcons1(env, Tcons1.SUP, xMinusOne);

        Abstract1 top = new Abstract1(man, env);
        Abstract1 a1 = top.meetCopy(man, constraint1);
		Abstract1 a2 = a1.meetCopy(man, constraint2);

        Abstract1 a3 = a2.forgetCopy(man, "x", false);
        // throw new RuntimeException(x.toString() + ' ' + y.toString() + ' ' + xPlusy.toString() + ' ' + twoNode.toString() + ' ' + xPlusyMinusTwo + ' ' + negxPlusyMinusTwo + ' ' + constraint1 + ' ' + oneNode + ' ' + xMinusOne +  ' ' + a1.toString() + ' ' + a2.toString() + ' ' + a3.toString());
        
        /**
         * x
         * y
         * y +_i,0 x
         * 2
         * y +_i,0 x -_i,0 2
         * -(y +_i,0 x -_i,0 2)
         * -(y +_i,0 x -_i,0 2) > 0
         * 
         * 1 
         * x -_i,0 1 
         * 
         * {  -1x -1y +1 >= 0 } 
         * {  -1x -1y +1 >= 0;  1x -2 >= 0 } 
         * {  -1y -1 >= 0 }
         */

    }

    @Test
    public void MultiplicationTest() throws ApronException{
        // encode x + y <= 2, x + y > =-3
        // Goal: find range of x * y
        // expected results: [2, -infty]
        Texpr1Node x = new Texpr1VarNode("x");
        Texpr1Node y = new Texpr1VarNode("y");
        Texpr1Node xPlusy = new Texpr1BinNode(Texpr1BinNode.OP_ADD, Texpr1BinNode.RTYPE_INT, Texpr1BinNode.RDIR_ZERO, y, x);
        Coeff two = new MpqScalar(2);
        Texpr1Node twoNode = new Texpr1CstNode(two);
        Texpr1Node xPlusyMinusTwo = new Texpr1BinNode(Texpr1BinNode.OP_SUB, Texpr1BinNode.RTYPE_INT, Texpr1BinNode.RDIR_ZERO, xPlusy, twoNode);
        Texpr1Node negxPlusyMinusTwo = new Texpr1UnNode(Texpr1UnNode.OP_NEG, xPlusyMinusTwo);
        Tcons1 constraint1 = new Tcons1(env, Tcons1.SUPEQ, negxPlusyMinusTwo);

        Coeff negThree = new MpqScalar(-3);
        Texpr1Node negThreeNode = new Texpr1CstNode(negThree);
        Texpr1Node xPlusyMinusNegThree = new Texpr1BinNode(Texpr1BinNode.OP_SUB, Texpr1BinNode.RTYPE_INT, Texpr1BinNode.RDIR_ZERO, xPlusy, negThreeNode);
        Tcons1 constraint2 = new Tcons1(env, Tcons1.SUPEQ, xPlusyMinusNegThree);

        Abstract1 top = new Abstract1(man, env);
        Abstract1 a1 = top.meetCopy(man, constraint1);
		Abstract1 a2 = a1.meetCopy(man, constraint2);

        Texpr1Node xTimesy = new Texpr1BinNode(Texpr1BinNode.OP_MUL, Texpr1BinNode.RTYPE_INT, Texpr1BinNode.RDIR_ZERO, y, x);
        Texpr1Intern xTimesyIntern = new Texpr1Intern(env, xTimesy);

        Interval bounds = a2.getBound(man, xTimesyIntern);
        // throw new RuntimeException(a2.toString() + ' ' + bounds.toString());
        // {  -1x -1y +2 >= 0;  1x +1y +3 >= 0 } [-oo,+oo]

        // shows that this doesn't really work

    }

    
    @Test
    public void IntersectionTest() throws ApronException{
        // encode x + y > 2, 2x + y < 3, x - 4y > 20, z in [0 to 20]
        // expected result: empty

        String[] int_names = {"x", "y", "z"};
        Manager man = new Polka(true);
        Environment env = new Environment(int_names, real_names);
        
        // x + y > 2
        Texpr1Node x = new Texpr1VarNode("x");
        Texpr1Node y = new Texpr1VarNode("y");
        Texpr1Node z = new Texpr1VarNode("z");
        Texpr1Node xPlusy = new Texpr1BinNode(Texpr1BinNode.OP_ADD, Texpr1BinNode.RTYPE_INT, Texpr1BinNode.RDIR_ZERO, x, y);
        Coeff two = new MpqScalar(2);
        Texpr1Node twoNode = new Texpr1CstNode(two);
        Texpr1Node xPlusyMinusTwo = new Texpr1BinNode(Texpr1BinNode.OP_SUB, Texpr1BinNode.RTYPE_INT, Texpr1BinNode.RDIR_ZERO, xPlusy, twoNode);
        Tcons1 constraint1 = new Tcons1(env, Tcons1.SUP, xPlusyMinusTwo);

        // 2x + y < 3
        Coeff twoCoeff = new MpqScalar(2);
        Texpr1Node twoX = new Texpr1BinNode(Texpr1BinNode.OP_MUL, Texpr1BinNode.RTYPE_INT, Texpr1BinNode.RDIR_ZERO, new Texpr1CstNode(twoCoeff), x);
        Texpr1Node twoXPlusy = new Texpr1BinNode(Texpr1BinNode.OP_ADD, Texpr1BinNode.RTYPE_INT, Texpr1BinNode.RDIR_ZERO, twoX, y);
        Coeff three = new MpqScalar(3);
        Texpr1Node threeNode = new Texpr1CstNode(three);
        Texpr1Node twoXPlusyMinusThree = new Texpr1BinNode(Texpr1BinNode.OP_SUB, Texpr1BinNode.RTYPE_INT, Texpr1BinNode.RDIR_ZERO, twoXPlusy, threeNode);
        Texpr1Node negTwoXPlusyMinusThree = new Texpr1UnNode(Texpr1UnNode.OP_NEG, twoXPlusyMinusThree);
        Tcons1 constraint2 = new Tcons1(env, Tcons1.SUP, negTwoXPlusyMinusThree);

        // x - 4y > 20
        Coeff fourCoeff = new MpqScalar(4);
        Texpr1Node fourY = new Texpr1BinNode(Texpr1BinNode.OP_MUL, Texpr1BinNode.RTYPE_INT, Texpr1BinNode.RDIR_ZERO, new Texpr1CstNode(fourCoeff), y);
        Texpr1Node xMinusFourY = new Texpr1BinNode(Texpr1BinNode.OP_SUB, Texpr1BinNode.RTYPE_INT, Texpr1BinNode.RDIR_ZERO, x, fourY);
        Coeff twenty = new MpqScalar(20);
        Texpr1Node twentyNode = new Texpr1CstNode(twenty);
        Texpr1Node xMinusFourYMinusTwenty = new Texpr1BinNode(Texpr1BinNode.OP_SUB, Texpr1BinNode.RTYPE_INT, Texpr1BinNode.RDIR_ZERO, xMinusFourY, twentyNode);
        Tcons1 constraint3 = new Tcons1(env, Tcons1.SUP, xMinusFourYMinusTwenty);

        // 0 <= z <= 20
        Coeff zero = new MpqScalar(0);
        Coeff twentyOne = new MpqScalar(20);
        Texpr1Node zeroNode = new Texpr1CstNode(zero);
        Texpr1Node twentyOneNode = new Texpr1CstNode(twentyOne);
        Texpr1Node zMinusZero = new Texpr1BinNode(Texpr1BinNode.OP_SUB, Texpr1BinNode.RTYPE_INT, Texpr1BinNode.RDIR_ZERO, z, zeroNode);
        Texpr1Node twentyOneMinusZ = new Texpr1BinNode(Texpr1BinNode.OP_SUB, Texpr1BinNode.RTYPE_INT, Texpr1BinNode.RDIR_ZERO, twentyOneNode, z);
        Tcons1 constraint4 = new Tcons1(env, Tcons1.SUPEQ, zMinusZero);
        Tcons1 constraint5 = new Tcons1(env, Tcons1.SUPEQ, twentyOneMinusZ);

        Abstract1 top = new Abstract1(man, env);
        Abstract1 a1 = top.meetCopy(man, constraint1);
		Abstract1 a2 = a1.meetCopy(man, constraint2);
		Abstract1 a3 = a2.meetCopy(man, constraint4);
        Abstract1 a4 = a3.meetCopy(man, constraint5);
        Abstract1 a5 = a4.meetCopy(man, constraint3);

        // throw new RuntimeException(a2.toString()); // {  -2x -1y +2 >= 0;  1x +1y -3 >= 0 }
        // throw new RuntimeException(a4.toString()); // -2x -1y +2 >= 0;  -1z +20 >= 0;  1z >= 0;  1x +1y -3 >= 0 
        // throw new RuntimeException(a5.toString()); // empty
        Assertions.assertTrue(a5.toString().equals("<empty>"));

    }

    @Test
    public void AddTest() throws ApronException{
        try {
            // Initialize the environment with variables x and y
            String[] varNames = {"x", "y"};
            Environment env = new Environment(varNames, new String[]{});

            // Create the initial intervals for x = [1, 2] and y = [2, 3]
            Interval xInterval = new Interval(1, 2);
            Interval yInterval = new Interval(2, 3);

            // Create the abstract state with these intervals
            Abstract1 a1 = new Abstract1(new Box(), env, new String[]{"x", "y"}, new Interval[]{xInterval, yInterval});

            // Update x = x + y
            Texpr1Node x = new Texpr1VarNode("x");
            Texpr1Node y = new Texpr1VarNode("y");
            Texpr1Node xPlusy = new Texpr1BinNode(Texpr1BinNode.OP_ADD, Texpr1BinNode.RTYPE_REAL, Texpr1BinNode.RDIR_ZERO, x, y);
            Texpr1Intern xPlusyIntern = new Texpr1Intern(env, xPlusy);

            // Assign the new value to x
            Abstract1 a2 = a1.assignCopy(new Box(), "x", xPlusyIntern, null);
            // throw new RuntimeException(a1.toString() + ' ' + a2.toString());
            // a1 {  1x -1.0 >= 0;  -1x +2.0 >= 0;  1y -2.0 >= 0;  -1y +3.0 >= 0 } 
            // x: [1, 2], y: [2, 3]
            // a2 {  1x -3.0 >= 0;  -1x +5.0 >= 0;  1y -2.0 >= 0;  -1y +3.0 >= 0 }
            // x: [3, 5], y: [2, 3]

        } catch (ApronException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void EmptyTest() throws ApronException{
        try {

            Interval[] box = { new Interval(1, -1), new Interval(-1, 1) };
            Abstract1 empty = new Abstract1(man, env, integer_names, box);
            Assertions.assertTrue(empty.isBottom(man));

        } catch (ApronException e) {
            e.printStackTrace();
        }
    }


    @Test
    public void NegateIntervalTest() throws ApronException{

        Interval interval = new Interval(-1, 2);
        interval.neg();
        Assertions.assertTrue(interval.toString().equals("[-2,1]"));
        
    }

    @Test
    public void IntervalTest() throws ApronException {

        Interval int1 = new Interval(-1, -1);
        Interval int2 = new Interval(2, 6);
        Interval int3 = new Interval();

        if (int1.isBottom() || int2.isBottom()) {
            int3.setBottom();
        } else if (int1.isTop() || int2.isTop()) {
            int3.setTop();
        // } else if (int1.isScalar()) {
        //     // blah
        // } else if (int2.isScalar()) {
        //     // blah  
        } else {
            int3 = MultiplyIntervals(int1, int2);
        }

        logger.debug(int1.isScalar() ? "int1 is scalar" : "int1 is not scalar");
        logger.debug(int3.toString()); // 15:11:38 [main] DEBUG apron.ApronTest: [-6,-2]

        Interval int4 = new Interval();
        int4.setTop();
        Interval int5 = new Interval();
        int5.setBottom();
        logger.debug(int4.inf().toString() + " " + int4.sup().toString());
        logger.debug(int5.inf().toString() + " " + int5.sup().toString());
        // 14:25:39 [main] DEBUG apron.ApronTest: -Infinity Infinity
        // 14:25:39 [main] DEBUG apron.ApronTest: 1.0 -1.0

        Interval int6 = new Interval(int4.inf(), int4.inf());
        logger.debug(int6.toString());
        // 14:28:31 [main] DEBUG apron.ApronTest: [-Infinity,-Infinity]

        Scalar pinfty = int4.sup();
        Scalar ninfty = int4.inf();

        logger.debug(MultiplyScalars(new MpqScalar(1), new MpqScalar(3)).toString());
        logger.debug(MultiplyScalars(new MpqScalar(-2), new MpqScalar(3)).toString());
        logger.debug(MultiplyScalars(new MpqScalar(-100), new MpqScalar(230)).toString());
        logger.debug(MultiplyScalars(pinfty, new MpqScalar(3)).toString());
        logger.debug(MultiplyScalars(new MpqScalar(1), ninfty).toString());
        logger.debug(MultiplyScalars(new MpqScalar(0), ninfty).toString());
        logger.debug(MultiplyScalars(pinfty, ninfty).toString());
        // 14:57:02 [main] DEBUG apron.ApronTest: 3
        // 14:57:02 [main] DEBUG apron.ApronTest: -6
        // 14:57:02 [main] DEBUG apron.ApronTest: -23000
        // 14:57:02 [main] DEBUG apron.ApronTest: +oo
        // 14:57:02 [main] DEBUG apron.ApronTest: -oo
        // 14:57:02 [main] DEBUG apron.ApronTest: 0
        // 14:57:02 [main] DEBUG apron.ApronTest: -oo

        // java.lang.ClassCastException: apron.DoubleScalar cannot be cast to apron.MpqScalar

        logger.debug(MultiplyIntervals(new Interval(2, 6), new Interval(2, 6)).toString());
        logger.debug(MultiplyIntervals(new Interval(-100, 6), new Interval(2, -230)).toString());
        logger.debug(MultiplyIntervals(new Interval(2, 230), new Interval(-2, 6)).toString());
        logger.debug(MultiplyIntervals(new Interval(-2, 20), new Interval(-2, 106)).toString());
        logger.debug(MultiplyIntervals(new Interval(0, 6), new Interval(0, 10)).toString());
        logger.debug(MultiplyIntervals(new Interval(ninfty, new MpqScalar(0)), new Interval(ninfty, pinfty)).toString());
        logger.debug(MultiplyIntervals(new Interval(ninfty, new MpqScalar(-20)), new Interval(new MpqScalar(-15), pinfty)).toString());
        logger.debug(MultiplyIntervals(new Interval(-20, 20), new Interval(new MpqScalar(-100), pinfty)).toString());
        logger.debug(MultiplyIntervals(new Interval(ninfty, pinfty), new Interval(ninfty, pinfty)).toString());
        logger.debug(MultiplyIntervals(new Interval(ninfty, new MpqScalar(0)), new Interval(new MpqScalar(0), pinfty)).toString());
        logger.debug(MultiplyIntervals(new Interval(ninfty, new MpqScalar(-20)), new Interval(new MpqScalar(10), pinfty)).toString());
        
        // 15:19:12 [main] DEBUG apron.ApronTest: [4,36]
        // 15:19:12 [main] DEBUG apron.ApronTest: [-1380,23000]
        // 15:19:12 [main] DEBUG apron.ApronTest: [-460,1380]
        // 15:19:12 [main] DEBUG apron.ApronTest: [-212,2120]
        // 15:19:12 [main] DEBUG apron.ApronTest: [0,60]
        // 15:19:12 [main] DEBUG apron.ApronTest: [-oo,+oo]
        // 15:19:12 [main] DEBUG apron.ApronTest: [-oo,+oo]
        // 15:19:12 [main] DEBUG apron.ApronTest: [-oo,+oo]
        // 15:19:12 [main] DEBUG apron.ApronTest: [-oo,+oo]
        // 15:19:12 [main] DEBUG apron.ApronTest: [-oo,0]
        // 15:21:36 [main] DEBUG apron.ApronTest: [-oo,-200]

        // Results generated by ChatGPT
        // logger.debug("[4, 36]");           // [2, 6] * [2, 6]
        // logger.debug("[-1380, 23000]");    // [-100, 6] * [2, -230]
        // logger.debug("[-460, 1380]");      // [2, 230] * [-2, 6]
        // logger.debug("[-212, 2120]");      // [-2, 20] * [-2, 106]
        // logger.debug("[0, 60]");           // [0, 6] * [0, 10]
        // logger.debug("[-∞, ∞]");           // (-∞, 0] * (-∞, ∞)
        // logger.debug("[-∞, ∞]");           // (-∞, -20] * [-15, ∞)
        // logger.debug("[-∞, ∞]");           // [-20, 20] * [-100, ∞)
        // logger.debug("[-∞, ∞]");           // (-∞, ∞) * (-∞, ∞)
        // logger.debug("[-∞, 0]");           // (-∞, 0] * [0, ∞)
    }

    public Interval MultiplyIntervals(Interval a, Interval b) {
        // [x₁, x₂] · [y₁, y₂] = [min{x₁y₁, x₁y₂, x₂y₁, x₂y₂}, max{x₁y₁, x₁y₂, x₂y₁, x₂y₂}]
        
        // Calculate the four products
        Scalar prod1 = MultiplyScalars(a.inf(), b.inf());
        Scalar prod2 = MultiplyScalars(a.sup(), b.inf());
        Scalar prod3 = MultiplyScalars(a.inf(), b.sup());
        Scalar prod4 = MultiplyScalars(a.sup(), b.sup());
        
        // Determine the minimum and maximum of the products
        Scalar min = prod1;
        if (prod2.cmp(min) < 0) min = prod2;
        if (prod3.cmp(min) < 0) min = prod3;
        if (prod4.cmp(min) < 0) min = prod4;
        
        Scalar max = prod1;
        if (prod2.cmp(max) > 0) max = prod2;
        if (prod3.cmp(max) > 0) max = prod3;
        if (prod4.cmp(max) > 0) max = prod4;
        
        // Return the resulting interval
        return new Interval(min, max);
    }

    public Scalar MultiplyScalars(Scalar a, Scalar b) {
        Scalar temp = new MpqScalar();
        if (a.isInfty() * b.isInfty() != 0) { // i.e. a and b are both infinity
            temp.setInfty(a.isInfty() * b.isInfty());
        } else if (a.isZero() || b.isZero()) { // anything times 0 is 0, no need to think of limits ;D
            temp.set(0);
        } else if (a.isInfty() != 0 || b.isInfty() != 0) {
            temp.setInfty(a.sgn() * b.sgn());
        } else { // are finite
            Mpq a_mpq = new Mpq();
            ((MpqScalar) a).toMpq(a_mpq, 0);
            Mpq b_mpq = new Mpq();
            ((MpqScalar) b).toMpq(b_mpq, 0);
            a_mpq.mul(b_mpq);
            temp = new MpqScalar(a_mpq);
        }
        return temp;
    }

    @Test
    public void WidenTest() throws ApronException {

        // {  1i4 -1 = 0;  1FROG_OVERALL_PROFIT = 0 }
        // {  -2$i0 +1$i2 +3 = 0;  -2$i0 +1$i1 +2 = 0;  -1$i0 +1i4 = 0;  -89$i0 -1$i3 +1FROG_OVERALL_PROFIT +94 >= 0;  -1$i0 +10 >= 0;  -1$i3 +81 >= 0;  1$i3 -1 >= 0;  3$i0 +1$i3 -1FROG_OVERALL_PROFIT +1434 >= 0;  1553$i0 +9$i3 -9FROG_OVERALL_PROFIT -2350 >= 0 }
        // {  -6865FROG_OVERALL_PROFIT +1178829i4 -1178829 >= 0;  1FROG_OVERALL_PROFIT -85i4 +85 >= 0 }


        // {  1i4 -1 = 0;  1FROG_OVERALL_PROFIT = 0 }
        // {  -2i0 +1i2 +3 = 0;  -2i0 +1i1 +2 = 0;  -1i0 +1i4 = 0;  -9FROG_OVERALL_PROFIT +1553i0 +9i3 -2350 >= 0;  -1FROG_OVERALL_PROFIT +3i0 +1i3 +1434 >= 0;  -1i0 +10 >= 0;  -1i3 +81 >= 0;  1i3 -1 >= 0;  1FROG_OVERALL_PROFIT -89i0 -1i3 +94 >= 0 }
        // {  -6865FROG_OVERALL_PROFIT +1178829i4 -1178829 >= 0;  1FROG_OVERALL_PROFIT -85i4 +85 >= 0 }

        // Initialize the APRON manager
        Manager man = new Polka(true);

        // Define the environment with variables
        String[] realVars = {"i0", "i1", "i2", "i3", "i4", "FROG_OVERALL_PROFIT"};
        Environment env = new Environment(realVars, new String[]{});

        // Create variables
        Texpr1VarNode i0 = new Texpr1VarNode("i0");
        Texpr1VarNode i1 = new Texpr1VarNode("i1");
        Texpr1VarNode i2 = new Texpr1VarNode("i2");
        Texpr1VarNode i3 = new Texpr1VarNode("i3");
        Texpr1VarNode i4 = new Texpr1VarNode("i4");
        Texpr1VarNode FROG_OVERALL_PROFIT = new Texpr1VarNode("FROG_OVERALL_PROFIT");

        // Initialize an array for the constraints
        Tcons1[] constraints = new Tcons1[13];

        // Constraint set 1: { 1i4 -1 = 0;  1FROG_OVERALL_PROFIT = 0 }
        Texpr1Node expr1_1 = new Texpr1BinNode(Texpr1BinNode.OP_SUB, i4, new Texpr1CstNode(new MpqScalar(1)));
        constraints[0] = new Tcons1(env, Tcons1.EQ, expr1_1);

        Texpr1Node expr1_2 = FROG_OVERALL_PROFIT;
        constraints[1] = new Tcons1(env, Tcons1.EQ, expr1_2);

        // Constraint set 2
        Texpr1Node expr2_1 = new Texpr1BinNode(Texpr1BinNode.OP_ADD,
            new Texpr1BinNode(Texpr1BinNode.OP_ADD,
                new Texpr1BinNode(Texpr1BinNode.OP_MUL, new Texpr1CstNode(new MpqScalar(-2)), i0),
                new Texpr1BinNode(Texpr1BinNode.OP_MUL, new Texpr1CstNode(new MpqScalar(1)), i2)
            ),
            new Texpr1CstNode(new MpqScalar(3))
        );
        constraints[2] = new Tcons1(env, Tcons1.EQ, expr2_1);

        Texpr1Node expr2_2 = new Texpr1BinNode(Texpr1BinNode.OP_ADD,
            new Texpr1BinNode(Texpr1BinNode.OP_ADD,
                new Texpr1BinNode(Texpr1BinNode.OP_MUL, new Texpr1CstNode(new MpqScalar(-2)), i0),
                new Texpr1BinNode(Texpr1BinNode.OP_MUL, new Texpr1CstNode(new MpqScalar(1)), i1)
            ),
            new Texpr1CstNode(new MpqScalar(2))
        );
        constraints[3] = new Tcons1(env, Tcons1.EQ, expr2_2);

        Texpr1Node expr2_3 = new Texpr1BinNode(Texpr1BinNode.OP_ADD,
            new Texpr1BinNode(Texpr1BinNode.OP_MUL, new Texpr1CstNode(new MpqScalar(-1)), i0),
            i4
        );
        constraints[4] = new Tcons1(env, Tcons1.EQ, expr2_3);

        Texpr1Node expr2_4 = new Texpr1BinNode(Texpr1BinNode.OP_ADD,
            new Texpr1BinNode(Texpr1BinNode.OP_ADD,
                new Texpr1BinNode(Texpr1BinNode.OP_ADD,
                    new Texpr1BinNode(Texpr1BinNode.OP_MUL, new Texpr1CstNode(new MpqScalar(-89)), i0),
                    new Texpr1BinNode(Texpr1BinNode.OP_MUL, new Texpr1CstNode(new MpqScalar(-1)), i3)
                ),
                new Texpr1BinNode(Texpr1BinNode.OP_MUL, new Texpr1CstNode(new MpqScalar(1)), FROG_OVERALL_PROFIT)
            ),
            new Texpr1CstNode(new MpqScalar(94))
        );
        constraints[5] = new Tcons1(env, Tcons1.SUPEQ, expr2_4);

        Texpr1Node expr2_5 = new Texpr1BinNode(Texpr1BinNode.OP_ADD,
            new Texpr1BinNode(Texpr1BinNode.OP_MUL, new Texpr1CstNode(new MpqScalar(-1)), i0),
            new Texpr1CstNode(new MpqScalar(10))
        );
        constraints[6] = new Tcons1(env, Tcons1.SUPEQ, expr2_5);

        Texpr1Node expr2_6 = new Texpr1BinNode(Texpr1BinNode.OP_ADD,
            new Texpr1BinNode(Texpr1BinNode.OP_MUL, new Texpr1CstNode(new MpqScalar(-1)), i3),
            new Texpr1CstNode(new MpqScalar(81))
        );
        constraints[7] = new Tcons1(env, Tcons1.SUPEQ, expr2_6);

        Texpr1Node expr2_7 = new Texpr1BinNode(Texpr1BinNode.OP_ADD,
            new Texpr1BinNode(Texpr1BinNode.OP_MUL, new Texpr1CstNode(new MpqScalar(1)), i3),
            new Texpr1CstNode(new MpqScalar(-1))
        );
        constraints[8] = new Tcons1(env, Tcons1.SUPEQ, expr2_7);

        Texpr1Node expr2_8 = new Texpr1BinNode(Texpr1BinNode.OP_ADD,
            new Texpr1BinNode(Texpr1BinNode.OP_ADD,
                new Texpr1BinNode(Texpr1BinNode.OP_ADD,
                    new Texpr1BinNode(Texpr1BinNode.OP_MUL, new Texpr1CstNode(new MpqScalar(3)), i0),
                    new Texpr1BinNode(Texpr1BinNode.OP_MUL, new Texpr1CstNode(new MpqScalar(1)), i3)
                ),
                new Texpr1BinNode(Texpr1BinNode.OP_MUL, new Texpr1CstNode(new MpqScalar(-1)), FROG_OVERALL_PROFIT)
            ),
            new Texpr1CstNode(new MpqScalar(1434))
        );
        constraints[9] = new Tcons1(env, Tcons1.SUPEQ, expr2_8);

        Texpr1Node expr2_9 = new Texpr1BinNode(Texpr1BinNode.OP_ADD,
            new Texpr1BinNode(Texpr1BinNode.OP_ADD,
                new Texpr1BinNode(Texpr1BinNode.OP_ADD,
                    new Texpr1BinNode(Texpr1BinNode.OP_MUL, new Texpr1CstNode(new MpqScalar(1553)), i0),
                    new Texpr1BinNode(Texpr1BinNode.OP_MUL, new Texpr1CstNode(new MpqScalar(9)), i3)
                ),
                new Texpr1BinNode(Texpr1BinNode.OP_MUL, new Texpr1CstNode(new MpqScalar(-9)), FROG_OVERALL_PROFIT)
            ),
            new Texpr1CstNode(new MpqScalar(-2350))
        );
        constraints[10] = new Tcons1(env, Tcons1.SUPEQ, expr2_9);

        // Initialize an abstract domain (e.g., Polyhedron) to add constraints
        Abstract1 abs1 = new Abstract1(man, env);
        Abstract1 abs2 = new Abstract1(man, env);

        // Add all constraints to the abstract domain
        for (int i = 0; i <= 1; i++) {
            abs1.meet(man, constraints[i]);
        }

        for (int i = 2; i <= 10; i++) {
            abs2.meet(man, constraints[i]);
        }

        Abstract1 abs3 = widenFixed(abs1, abs2);

        logger.debug(abs1.toString());
        logger.debug(abs2.toString());
        logger.debug(abs3.toString());


    }

    private Abstract1 widenFixed(Abstract1 oldState, Abstract1 newState) throws ApronException {
        // apron requires explicit joining before widening
        Abstract1 joined = newState.joinCopy(man, oldState);
        Abstract1 widened = oldState.widening(man, joined);
        return widened;
    }

}
