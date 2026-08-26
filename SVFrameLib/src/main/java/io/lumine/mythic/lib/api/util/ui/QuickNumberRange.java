package io.lumine.mythic.lib.api.util.ui;

public class QuickNumberRange implements Cloneable {
    private final Double minimumInclusive, maximumInclusive;
    public QuickNumberRange(double value){this(value,value);} public QuickNumberRange(double min,double max){this(Double.valueOf(min),Double.valueOf(max));}
    public QuickNumberRange(Double minimumInclusive,Double maximumInclusive){this.minimumInclusive=minimumInclusive;this.maximumInclusive=maximumInclusive;}
    public Double getMinimumInclusive(){return minimumInclusive;} public Double getMaximumInclusive(){return maximumInclusive;}
    public double getMin(){return minimumInclusive==null?Double.NEGATIVE_INFINITY:minimumInclusive;} public double getMax(){return maximumInclusive==null?Double.POSITIVE_INFINITY:maximumInclusive;}
    public boolean hasMin(){return minimumInclusive!=null;} public boolean hasMax(){return maximumInclusive!=null;}
    public boolean isSimple(){return hasMin()&&hasMax()&&maximumInclusive.equals(minimumInclusive);}
    public double getAsDouble(double fallback){return hasMin()?minimumInclusive:hasMax()?maximumInclusive:fallback;}
    public boolean inRange(double value){return (!hasMin()||value>=minimumInclusive)&&(!hasMax()||value<=maximumInclusive);}
    public static QuickNumberRange getFromString(String input){return getFromString(input,null);} public static QuickNumberRange fromString(String input){return getFromString(input);}
    public static QuickNumberRange getFromString(String input,FriendlyFeedbackProvider feedback){
        if(input==null){FriendlyFeedbackProvider.log(feedback,FriendlyFeedbackCategory.ERROR,"No value provided to parse QuickNumberRange.");return null;}
        String raw=input.trim(); if(raw.equals(".."))return new QuickNumberRange(null,null);
        Double simple=parse(raw); if(simple!=null)return new QuickNumberRange(simple,simple);
        int sep=raw.indexOf(".."); if(sep>=0&&raw.indexOf("..",sep+2)<0){String left=raw.substring(0,sep),right=raw.substring(sep+2);Double min=left.isEmpty()?null:parse(left),max=right.isEmpty()?null:parse(right);if((left.isEmpty()||min!=null)&&(right.isEmpty()||max!=null))return new QuickNumberRange(min,max);}
        FriendlyFeedbackProvider.log(feedback,FriendlyFeedbackCategory.ERROR,"Invalid number range: {0}",raw);return null;
    }
    private static Double parse(String value){try{return Double.parseDouble(value);}catch(RuntimeException ignored){return null;}}
    @Override public String toString(){if(isSimple())return String.valueOf(maximumInclusive);return (hasMin()?minimumInclusive.toString():"")+".."+(hasMax()?maximumInclusive.toString():"");}
    public String toStringColored(){if(isSimple())return "§e"+maximumInclusive;return "§e"+(hasMin()?minimumInclusive:"-?")+"§7..§e"+(hasMax()?maximumInclusive:"?");}
    @Override public QuickNumberRange clone(){return new QuickNumberRange(minimumInclusive,maximumInclusive);}
}
