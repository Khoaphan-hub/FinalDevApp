package com.example.finalproject.presentation;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.w3c.dom.NodeList;
import java.io.File;
import java.util.Locale;
import javax.xml.parsers.DocumentBuilderFactory;

/** Checks formatting against the actual argument types passed by the merged screens. */
public class IntegrationStringContractTest {
    private String resource(String directory, String key) throws Exception {
        File file = new File("src/main/res/" + directory + "/strings.xml");
        NodeList strings = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(file).getElementsByTagName("string");
        for (int i = 0; i < strings.getLength(); i++) {
            if (key.equals(strings.item(i).getAttributes().getNamedItem("name").getNodeValue())) {
                return strings.item(i).getTextContent();
            }
        }
        throw new AssertionError("Missing resource: " + directory + "/" + key);
    }

    @Test public void distantLocationAcceptsRoundedIntegerInBothLanguages() throws Exception {
        for (String dir : new String[]{"values", "values-en"}) {
            String formatted = String.format(Locale.US, resource(dir, "location_far_message"), 137);
            assertTrue(formatted.contains("137 km"));
            assertFalse(formatted.contains("%"));
        }
    }

    @Test public void temperatureAcceptsIntegerInBothLanguages() throws Exception {
        for (String dir : new String[]{"values", "values-en"}) {
            assertTrue(String.format(Locale.US, resource(dir, "weather_temperature"), 18).contains("18"));
        }
    }

    @Test public void savedTitlePreservesBothFieldsInBothLanguages() throws Exception {
        for (String dir : new String[]{"values", "values-en"}) {
            String formatted = String.format(Locale.US, resource(dir, "saved_title_with_highlight"), "Trip", "Lake");
            assertTrue(formatted.contains("Trip"));
            assertTrue(formatted.contains("Lake"));
        }
    }
}
