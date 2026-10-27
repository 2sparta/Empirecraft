package ua.empirecraft.feudal;

import java.math.*;
import static ua.empirecraft.feudal.Model.require;
import static ua.empirecraft.feudal.CurrencyService.*;

/** Integral of dR/dq = +/-R/M with fixed total supply, including the treasury.
 * Decimal arithmetic and conservative settlement rounding; no floating-point money. */
final class CurrencyCurve {
    static final MathContext MC=new MathContext(50,RoundingMode.HALF_EVEN);
    static final BigDecimal TWO=BigDecimal.valueOf(2), EPS=new BigDecimal("1e-49");
    static final BigDecimal LN2=new BigDecimal("0.693147180559945309417232121458176568075500134360255254120680");
    static BigDecimal exp(BigDecimal x){
        require(x.abs().compareTo(BigDecimal.ONE)<=0,"Завелика частка обігу.");
        BigDecimal term=BigDecimal.ONE,sum=term;
        for(int n=1;n<512;n++){term=term.multiply(x,MC).divide(BigDecimal.valueOf(n),MC);sum=sum.add(term,MC);if(term.abs().compareTo(EPS)<0)return sum;}
        throw new IllegalStateException("Integral failed to converge");
    }
    static BigDecimal ln(BigDecimal x){
        require(x.signum()>0,"Невизначений курс.");int power=0;
        while(x.compareTo(TWO)>0){x=x.divide(TWO,MC);power++;require(power<512,"Завеликий золотий платіж.");}
        while(x.compareTo(BigDecimal.ONE)<0){x=x.multiply(TWO,MC);power--;require(power> -512,"Замалий золотий платіж.");}
        BigDecimal z=x.subtract(BigDecimal.ONE).divide(x.add(BigDecimal.ONE),MC),square=z.multiply(z,MC),term=z,sum=z;
        for(int n=3;n<1024;n+=2){term=term.multiply(square,MC);BigDecimal part=term.divide(BigDecimal.valueOf(n),MC);sum=sum.add(part,MC);if(part.abs().compareTo(EPS)<0)break;}
        return sum.multiply(TWO,MC).add(LN2.multiply(BigDecimal.valueOf(power),MC),MC);
    }
    static BigDecimal sell(BigDecimal reserve,long supply,long cents){
        require(reserve.signum()>0 && supply>0 && cents>0 && cents<=supply,"Немає золотого курсу або монет.");
        return reserve.multiply(BigDecimal.ONE.subtract(exp(BigDecimal.valueOf(-cents).divide(BigDecimal.valueOf(supply),MC)),MC),MC).setScale(GOLD_SCALE,RoundingMode.DOWN);
    }
    static BigDecimal cost(BigDecimal reserve,long supply,long cents){
        require(reserve.signum()>0 && supply>0 && cents>0 && cents<=supply,"Недостатня ліквідність казни.");
        return reserve.multiply(exp(BigDecimal.valueOf(cents).divide(BigDecimal.valueOf(supply),MC)).subtract(BigDecimal.ONE,MC),MC).setScale(GOLD_SCALE,RoundingMode.CEILING);
    }
    static long buy(BigDecimal reserve,long supply,BigDecimal budget,long stock){
        require(reserve.signum()>0 && supply>0 && budget.signum()>0,"Немає золотого курсу.");
        BigDecimal raw=ln(BigDecimal.ONE.add(budget.divide(reserve,MC),MC)).multiply(BigDecimal.valueOf(supply),MC);
        require(raw.compareTo(BigDecimal.valueOf(stock+1))<0,"Недостатньо монет у казні емітента; обмін не карбує монет.");
        long cents=raw.setScale(0,RoundingMode.DOWN).longValueExact();require(cents>0 && cents<=stock,"Результат менший за 0.01 монети або казна порожня.");
        while(cents>0 && cost(reserve,supply,cents).compareTo(budget)>0)cents--;
        require(cents>0,"Сума замала для точності розрахунку.");return cents;
    }
}
