package com.example.finalproject.domain.model;
import static org.junit.Assert.assertEquals;
import org.junit.Test;
public class WeatherCodeMapperTest {
    @Test public void mapsImportantWmoWeatherGroups(){
        assertEquals(com.example.finalproject.R.string.weather_clear,WeatherCodeMapper.labelRes(0));
        assertEquals(com.example.finalproject.R.string.weather_rain,WeatherCodeMapper.labelRes(61));
        assertEquals(com.example.finalproject.R.string.weather_storm,WeatherCodeMapper.labelRes(95));
    }
}
