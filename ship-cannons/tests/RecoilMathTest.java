package ua.empirecraft.shipcannons.test;

import org.joml.*;
import java.lang.Math;
import ua.empirecraft.shipcannons.*;

public final class RecoilMathTest {
    private static int checks;
    private static void check(boolean ok,String message) {checks++;if(!ok)throw new AssertionError(message);}
    public static void main(String[] args) {
        var s=RecoilMath.DEFAULT;var up=new Vector3d(0,1,0);
        for(double height:new double[]{-3,0,1,4}) {
            var right=RecoilMath.angularImpulse(new Vector3d(3,height,0),new Vector3d(-1000,0,0),up,s);
            var left=RecoilMath.angularImpulse(new Vector3d(-3,height,0),new Vector3d(1000,0,0),up,s);
            check(right.z>0 && left.z<0,"Broadside heel sign at height "+height);
            check(right.add(left).length()<1e-8,"Opposite broadsides cancel");
        }
        var inertia=new Matrix3d().scaling(100000,200000,500000);
        var impulse=new Vector3d(1000,2000,3000);
        check(RecoilMath.angularVelocity(inertia,impulse).distance(new Vector3d(.01,.01,.006))<1e-10,"Actual inertia tensor");
        check(RecoilMath.angularVelocity(new Matrix3d().zero(),impulse).lengthSquared()==0,"Singular tensor");
        check(RecoilMath.momentum(25,8,4,100000)==16000,"CBC momentum units");
        check(RecoilMath.momentum(Double.NaN,2,4,100000)==0,"NaN rejected");
        for(double current:new double[]{0,.2,.349,.6})for(double kick:new double[]{-.5,-.1,0,.1,.5}) {
            double scale=RecoilMath.rateScale(new Vector3d(0,0,current),new Vector3d(0,0,kick),.35);
            double result=current+scale*kick;
            check(scale>=0 && scale<=1,"Rate scale range");
            check(Math.abs(result)<=Math.max(Math.abs(current),.35)+1e-9,"New recoil cannot increase overspeed");
        }
        for(double roll:new double[]{-.6,-.25,0,.25,.6}) {
            var n=new Quaterniond().rotationZ(roll).transform(new Vector3d(0,1,0));
            var still=DeckMath.contactVelocity(new Vector3d(),n);
            check(DeckMath.tangent(still,n).length()<1e-9,"No downhill drift");
            var input=DeckMath.input(new Vector3d(.2,0,.1),n);
            check(Math.abs(input.dot(n))<1e-9,"Driving follows deck plane");
            check(Math.abs(input.length()-Math.sqrt(.05))<1e-9,"Deck drive preserves speed");
        }
        check(Math.abs(DeckMath.localYaw(new Quaterniond().rotationY(Math.toRadians(-30)),70)-40)<1e-8,"Wrench facing converts the continuous yaw before cardinal rounding");
        for(var v:new Vector3d[]{new Vector3d(0,0,8),new Vector3d(0,8,0),new Vector3d(0,-8,0),new Vector3d(8,0,0)}) {
            var forward=new Vector3d(v).normalize();var mean=new Vector3d();
            for(int i=0;i<25;i++) {
                var pellet=GrapeshotMath.pellet(v,i,25,.25,.7);mean.add(pellet);
                check(pellet.isFinite() && Math.abs(pellet.length()-12)<1e-8,"Finite pellet speed");
                double axial=pellet.dot(forward);var radial=new Vector3d(pellet).fma(-axial,forward);
                check(axial>0 && radial.length()/axial<=.25+1e-9,"Pellet remains in cone");
            }
            check(DeckMath.tangent(mean,forward).length()/mean.dot(forward)<.02,"Balanced spread");
        }
        var outer=RecoilScope.begin();RecoilScope.handled(outer,33);
        check(RecoilScope.replacesNative(33) && !RecoilScope.replacesNative(34),"Native replacement scoped by ship");
        check(!RecoilScope.nativeCbcCall(),"Unrelated force call remains eligible during a CBC shot");
        var inner=RecoilScope.begin();check(!RecoilScope.replacesNative(33),"Nested scope isolated");RecoilScope.end(inner);
        check(RecoilScope.replacesNative(33),"Outer scope restored");RecoilScope.end(outer);
        check(!RecoilScope.replacesNative(33),"Scope cleared after shot");
        System.out.println("RECOIL_MATH_PASS checks="+checks);
    }
}
