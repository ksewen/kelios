package com.github.ksewen.kelios.platform;
import java.util.*;
public final class ListInput {
    private ListInput() {}
    public static void keys(Map<String,String> q,String... allowed) {
        Set<String> keys=new HashSet<>(List.of(allowed));keys.addAll(Set.of("page","size","q"));
        if(!keys.containsAll(q.keySet()))throw BusinessFailure.invalid("Unsupported query parameter");
    }
    public static int page(Map<String,String> q){return integer(q.getOrDefault("page","0"),0,Integer.MAX_VALUE);}
    public static int size(Map<String,String> q){return integer(q.getOrDefault("size","25"),1,100);}
    private static int integer(String s,int min,int max){try{int n=Integer.parseInt(s);if(n<min || n>max)throw new IllegalArgumentException();return n;}catch(IllegalArgumentException ex){throw BusinessFailure.invalid("Invalid pagination");}}
    public static UUID uuid(String value){if(value==null)return null;try{if(!value.matches("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}"))throw new IllegalArgumentException();return UUID.fromString(value);}catch(IllegalArgumentException ex){throw BusinessFailure.invalid("Invalid UUID");}}
    public static String search(Map<String,String> q){String s=q.getOrDefault("q","");if(s.length()>128)throw BusinessFailure.invalid("Search exceeds 128 characters");return "%"+s.replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%";}
    public static boolean bool(String value){if(!Set.of("true","false").contains(value))throw BusinessFailure.invalid("Boolean filter must be true or false");return Boolean.parseBoolean(value);}
}
