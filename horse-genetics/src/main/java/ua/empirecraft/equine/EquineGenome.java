package ua.empirecraft.equine;

import java.util.*;
import java.util.random.RandomGenerator;

/** Adapted from sekelsta's Realistic Horse Genetics (LGPL-3.0-or-later).
 * Gene order, two-allele encoding, crossover linkage and stat formulas remain compatible.
 * DLO owns models, coat rendering, breeds and riding; no RHG entity or renderer is registered.
 */
public final class EquineGenome {
    public enum Gene {
        extension,    // 0 for e (red), 1 for E (can have black)
        agouti,       // 0 for black, 1 for seal brown, 4 for bay
        dun,          // 0 for nd2, 1 for nd1, 2 for dun, 3 for donkeys
        gray,         // 0 for g (non-gray), 1 for G (gray)
        cream,        // 0 for wildtype, 1 for snowdrop, 2 for pearl, 3 for cream, 4 for lighter shade
        liver,        // 0 for liver, 1 for non-liver
        flaxen1,      // 0 for flaxen, 1 for non-flaxen
        flaxen2,
        dapple,       // 0 for non-dappled, 1 for dappled
        sooty1,       // 0 for non-sooty, 1 for sooty
        sooty2,       // same as above
        sooty3,       // 0 for sooty, 1 for non-sooty
        light_belly,  // Assuming same agouti promoter region as in white bellied agouti mice
        mealy1,
        mealy2,
        KIT,
        MITF,
        leopard,
        PATN1,
        PATN2,
        PATN3,
        gray_suppression,
        slow_gray1,
        slow_gray2,
        slow_gray3,
        white_star,
        white_forelegs,
        white_hindlegs,
        gray_melanoma,
        gray_mane1,
        gray_mane2,
        rufous,
        dense,
        champagne,
        cameo,
        ivory,
        donkey_dark,
        cross,
        reduced_points,
        light_legs,
        tyger,
        DEPRECATED_donkey_dun,
        flaxen_boost,
        light_dun,
        marble,
        leopard_suppression,
        leopard_suppression2,
        PATN_boost1,
        PATN_boost2,
        PAX3,
        white_suppression,
        frame,
        silver,
        dark_red,
        liver_boost,
        LCORL,
        HMGA2,
        mushroom,
        speed0,
        speed1,
        speed2,
        speed3,
        speed4,
        speed5,
        speed6,
        speed7,
        speed8,
        speed9,
        speed10,
        speed11,
        athletics0,
        athletics1,
        athletics2,
        athletics3,
        athletics4,
        athletics5,
        athletics6,
        athletics7,
        jump0,
        jump1,
        jump2,
        jump3,
        jump4,
        jump5,
        jump6,
        jump7,
        jump8,
        jump9,
        jump10,
        jump11,
        health0,
        health1,
        health2,
        health3,
        health4,
        health5,
        health6,
        health7,
        health8,
        health9,
        health10,
        health11,
        immune0,
        immune1,
        immune2,
        immune3,
        immune4,
        immune5,
        immune6,
        immune7,
        mhc0,
        mhc1,
        mhc2,
        mhc3,
        mhc4,
        mhc5,
        mhc6,
        mhc7,
        size_minor0,
        size_minor1,
        size_minor2,
        size_minor3,
        size_minor4,
        size_minor5,
        size_minor6,
        size_minor7,
        size0,
        size1,
        size2,
        size3,
        size4,
        size_subtle0,
        size_subtle1,
        size_subtle2,
        size_subtle3,
        size_subtle4,
        size_subtle5,
        size_subtle6,
        size_subtle7,
        double_ovulation,
        donkey_size0,
        donkey_size1,
        donkey_size2,
        donkey_size3,
        donkey_size4,
        donkey_size5,
        donkey_size6,
        color,              // TYR, albino donkeys
        rabicano,
        blue_eye_shade1,      // 0 pale, 1 deep
        blue_eye_shade2,      // 0 deep, 1 pale
        blue_eye_shade3,      // 0 deep, 1 pale
        tiger_eye,
        brown_eye_shade1,     // 0 deep, 1 pale recessive
        brown_eye_shade2,     // 0 deep, 1 pale
        brown_eye_shade3,     // 0 pale, 1 deep
        stripe_width        // 0 wide, 1 narrow dominant
    }

    private static final String ALPHABET="ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    private final int[] genes;
    public EquineGenome(int[] genes) {
        if(genes.length!=Gene.values().length*2)throw new IllegalArgumentException("Wrong genome length");
        this.genes=genes.clone();for(int allele:genes)if(allele<0||allele>63)throw new IllegalArgumentException("Invalid allele");
    }
    public static EquineGenome decode(String value) {
        if(value.length()>Gene.values().length*2)throw new IllegalArgumentException("Unsupported newer genome");
        int[] genes=new int[Gene.values().length*2];
        for(int i=0;i<value.length();i++){int v=ALPHABET.indexOf(value.charAt(i));if(v<0)throw new IllegalArgumentException("Invalid gene encoding");genes[i]=v;}
        return new EquineGenome(genes);
    }
    public String encode(){StringBuilder b=new StringBuilder();for(int g:genes)b.append(ALPHABET.charAt(g));return b.toString();}
    public int allele(Gene gene,int copy){if(copy<0||copy>1)throw new IllegalArgumentException();return genes[gene.ordinal()*2+copy];}
    private double sum(String prefix,int count){double s=0;for(int i=0;i<count;i++){Gene g=Gene.valueOf(prefix+i);s+=allele(g,0)+allele(g,1);}return s;}
    public double speed(){return .1125+(sum("speed",12)+sum("athletics",8)/2)*(.225/32);}
    public double jump(){return .4+(sum("jump",12)+sum("athletics",8)/2)*(.6/32);}
    public double health(){
        int heterozygous=0;for(int i=0;i<8;i++)for(String prefix:List.of("immune","mhc")){Gene g=Gene.valueOf(prefix+i);if(allele(g,0)!=allele(g,1))heterozygous++;}
        double immune=heterozygous/12.;if(immune>1)immune=1+.25*(immune-1);
        return 15+(sum("health",12)+Math.min(7,7*immune))*.5;
    }
    public static EquineGenome founder(RandomGenerator random) {
        int[] genes=new int[Gene.values().length*2];
        for(Gene gene:Gene.values())for(int copy=0;copy<2;copy++)genes[gene.ordinal()*2+copy]=random.nextInt(gene.name().startsWith("immune")||gene.name().startsWith("mhc")?16:2);
        return new EquineGenome(genes);
    }
    public static EquineGenome inherit(EquineGenome mother,EquineGenome father,RandomGenerator random,double mutation) {
        if(!Double.isFinite(mutation)||mutation<0||mutation>1)throw new IllegalArgumentException("Invalid mutation chance");
        int[] child=new int[Gene.values().length*2];
        var links=new ArrayList<Gene>(List.of(Gene.values()));
        links.add(Gene.extension);links.add(Gene.KIT);links.add(Gene.agouti);links.add(Gene.light_belly);
        for(int i=0;i<8;i++)links.add(Gene.valueOf("mhc"+i));
        int maternal=random.nextInt(2),paternal=random.nextInt(2);
        for(Gene gene:links) {
            child[gene.ordinal()*2]=mother.allele(gene,maternal);child[gene.ordinal()*2+1]=father.allele(gene,paternal);
            double crossover=gene==Gene.extension?.03:gene==Gene.agouti?0:gene.name().startsWith("mhc")&&gene!=Gene.mhc7?.2:.5;
            if(random.nextDouble()<crossover)maternal=1-maternal;if(random.nextDouble()<crossover)paternal=1-paternal;
        }
        for(Gene gene:Gene.values())for(int copy=0;copy<2;copy++)if(random.nextDouble()<mutation) {
            int bound=gene.name().startsWith("immune")||gene.name().startsWith("mhc")?16:Math.max(2,1+Math.max(Math.max(mother.allele(gene,0),mother.allele(gene,1)),Math.max(father.allele(gene,0),father.allele(gene,1))));
            child[gene.ordinal()*2+copy]=random.nextInt(bound);
        }
        return new EquineGenome(child);
    }
}
