package com.example.finalproject.infrastructure.remote;
import android.os.*;
import com.example.finalproject.domain.callback.RepositoryCallback;
import com.example.finalproject.domain.model.WeatherSnapshot;
import org.json.*;
import java.io.*;import java.net.*;import java.nio.charset.StandardCharsets;import java.util.*;import java.util.concurrent.*;
public final class RemoteWeatherRepository {
    private static final String URL_TEXT="https://api.open-meteo.com/v1/forecast?latitude=11.9404&longitude=108.4583&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m,is_day&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max&timezone=Asia%2FBangkok&forecast_days=3";
    private final ExecutorService executor=Executors.newSingleThreadExecutor();private final Handler main=new Handler(Looper.getMainLooper());
    public void load(RepositoryCallback<WeatherSnapshot> callback){executor.execute(()->{HttpURLConnection c=null;try{c=(HttpURLConnection)new URL(URL_TEXT).openConnection();c.setConnectTimeout(7000);c.setReadTimeout(10000);JSONObject root=new JSONObject(read(c.getInputStream()));JSONObject now=root.getJSONObject("current"),daily=root.getJSONObject("daily");JSONArray dates=daily.getJSONArray("time"),codes=daily.getJSONArray("weather_code"),mins=daily.getJSONArray("temperature_2m_min"),maxs=daily.getJSONArray("temperature_2m_max"),rains=daily.getJSONArray("precipitation_probability_max");List<WeatherSnapshot.Day> days=new ArrayList<>();for(int i=0;i<dates.length();i++)days.add(new WeatherSnapshot.Day(dates.getString(i),codes.getInt(i),mins.getDouble(i),maxs.getDouble(i),rains.optInt(i)));WeatherSnapshot s=new WeatherSnapshot(now.getDouble("temperature_2m"),now.getDouble("apparent_temperature"),now.getInt("relative_humidity_2m"),now.getDouble("wind_speed_10m"),now.getInt("weather_code"),now.getInt("is_day")==1,days);main.post(()->callback.onSuccess(s));}catch(Exception e){main.post(()->callback.onError(e));}finally{if(c!=null)c.disconnect();}});}
    private String read(InputStream in)throws Exception{StringBuilder s=new StringBuilder();try(BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){String l;while((l=r.readLine())!=null)s.append(l);}return s.toString();}
}
