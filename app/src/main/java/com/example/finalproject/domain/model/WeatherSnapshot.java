package com.example.finalproject.domain.model;
import java.util.*;
public final class WeatherSnapshot {
    public static final class Day { public final String date; public final int code; public final double min,max; public final int rain; public Day(String d,int c,double n,double x,int r){date=d;code=c;min=n;max=x;rain=r;} }
    public final double temperature, apparent, wind; public final int humidity, code; public final boolean day; public final List<Day> forecast;
    public WeatherSnapshot(double t,double a,int h,double w,int c,boolean d,List<Day> f){temperature=t;apparent=a;humidity=h;wind=w;code=c;day=d;forecast=Collections.unmodifiableList(f);}
}
