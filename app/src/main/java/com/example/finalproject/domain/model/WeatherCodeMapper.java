package com.example.finalproject.domain.model;
import com.example.finalproject.R;
public final class WeatherCodeMapper {
    private WeatherCodeMapper() {}
    public static int labelRes(int c){if(c==0)return R.string.weather_clear;if(c<=2)return R.string.weather_partly_cloudy;if(c==3)return R.string.weather_cloudy;if(c==45||c==48)return R.string.weather_fog;if(c>=51&&c<=57)return R.string.weather_drizzle;if((c>=61&&c<=67)||(c>=80&&c<=82))return R.string.weather_rain;if((c>=71&&c<=77)||(c>=85&&c<=86))return R.string.weather_snow;if(c>=95)return R.string.weather_storm;return R.string.weather_variable;}
    public static String icon(int c,boolean day){if(c==0)return day?"☀️":"🌙";if(c<=3)return"⛅";if(c==45||c==48)return"🌫️";if(c>=95)return"⛈️";if(c>=51&&c<=82)return"🌧️";return"🌤️";}
}
