package com.example.finalproject.domain.model;
public final class WeatherCodeMapper {
    private WeatherCodeMapper() {}
    public static String label(int c){if(c==0)return"Trời quang";if(c<=2)return"Ít mây";if(c==3)return"Nhiều mây";if(c==45||c==48)return"Có sương mù";if(c>=51&&c<=57)return"Mưa phùn";if((c>=61&&c<=67)||(c>=80&&c<=82))return"Có mưa";if((c>=71&&c<=77)||(c>=85&&c<=86))return"Tuyết";if(c>=95)return"Có giông";return"Thời tiết thay đổi";}
    public static String icon(int c,boolean day){if(c==0)return day?"☀️":"🌙";if(c<=3)return"⛅";if(c==45||c==48)return"🌫️";if(c>=95)return"⛈️";if(c>=51&&c<=82)return"🌧️";return"🌤️";}
}
