package ua.empirecraft.workshop;
public final class CastRules {
    private CastRules(){}
    public static boolean validDiameter(int value){return value>=5&&value<=490&&value%5==0;}
    public static int duration(int metalCost){return Math.max(200,Math.min(12000,metalCost*40));}
    public static boolean numericInput(String value){return value.matches("[0-9]{0,3}");}
}
