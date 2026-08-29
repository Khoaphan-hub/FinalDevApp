package com.example.finalproject.domain.model;
import static org.junit.Assert.assertEquals;
import org.junit.Test;
public class WeatherCodeMapperTest {
    @Test public void mapsImportantWmoWeatherGroups(){
        assertEquals("Trời quang",WeatherCodeMapper.label(0));
        assertEquals("Có mưa",WeatherCodeMapper.label(61));
        assertEquals("Có giông",WeatherCodeMapper.label(95));
    }
}
