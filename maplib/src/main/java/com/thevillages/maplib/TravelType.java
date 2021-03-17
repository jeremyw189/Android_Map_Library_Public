package com.thevillages.maplib;

import java.util.HashMap;
import java.util.Map;

public enum  TravelType {
    CAR(0), GOLF_CART(1);
    private final int value;
    private final static Map<Integer, TravelType> map = new HashMap<Integer, TravelType>();

    TravelType(int value){
        this.value = value;
    }

    static {
        for (TravelType travelType : TravelType.values()){
            map.put(travelType.value, travelType);
        }
    }
    public static TravelType valueOf(int travelType){
        return map.get(travelType);
    }
    public int getValue()
    {
        return value;
    }
}



