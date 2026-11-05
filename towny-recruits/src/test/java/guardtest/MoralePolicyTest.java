package guardtest;

import ua.townyrecruits.MoralePolicy;

public final class MoralePolicyTest {
    public static void main(String[] args) {
        double alone = 100, supported = 100;
        for (int lost = 1; lost <= 7; lost++) {
            alone = MoralePolicy.ownLoss(alone,10,false); supported = MoralePolicy.ownLoss(supported,10,true);
            assert supported > alone : "Unharmed allied companies must buffer own losses";
            assert MoralePolicy.breakChance(supported,lost,10,true) <= MoralePolicy.breakChance(alone,lost,10,false);
        }
        assert MoralePolicy.breakChance(alone,7,10,false) > 0;
        assert MoralePolicy.breakChance(100,0,10,false) == 0;
        assert MoralePolicy.breakChance(86,1,10,false) == 0 : "One ordinary casualty cannot break a fresh ten-person squad";
        assert MoralePolicy.breakChance(MoralePolicy.ownLoss(100,2,false),1,2,false) > 0;
        assert MoralePolicy.alliedLoss(100,10) < 100 && MoralePolicy.alliedLoss(100,10) > MoralePolicy.ownLoss(100,10,false);
        assert !MoralePolicy.straggler(10,9,80,2399,0,true);
        assert MoralePolicy.straggler(10,9,80,2400,0,true);
        assert !MoralePolicy.straggler(10,9,80,2400,2.1,true) : "A recruit making progress is not stuck";
        assert !MoralePolicy.straggler(10,5,80,2400,0,true) : "There must be a majority";
        assert !MoralePolicy.straggler(2,1,80,2400,0,true);
        assert !MoralePolicy.straggler(10,9,40,2400,0,true);
        assert !MoralePolicy.straggler(10,9,80,2400,0,false) : "An intentionally held garrison must keep its owner";
        assert !MoralePolicy.escaped(63.99,70,false) && !MoralePolicy.escaped(70,47.99,false) && !MoralePolicy.escaped(70,70,true);
        assert MoralePolicy.escaped(64,48,false);
        System.out.println("PASS casualty morale, nearby allied losses, steady-allied buffering, probabilistic rout boundaries, escape distances and stuck/majority/progress/intent safeguards");
    }
}
