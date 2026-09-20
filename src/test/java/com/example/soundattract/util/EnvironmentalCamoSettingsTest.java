package com.example.soundattract.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnvironmentalCamoSettingsTest {
    @Test
    void parsesSamplingModesAndAliases() {
        assertEquals(EnvironmentalCamoSettings.SamplingMode.AVERAGE_AREA,
                EnvironmentalCamoSettings.parseMode("average_area"));
        assertEquals(EnvironmentalCamoSettings.SamplingMode.AVERAGE_AREA,
                EnvironmentalCamoSettings.parseMode("legacy"));
        assertEquals(EnvironmentalCamoSettings.SamplingMode.VIEWER_BACKDROP,
                EnvironmentalCamoSettings.parseMode("backdrop"));
        assertEquals(EnvironmentalCamoSettings.SamplingMode.HYBRID,
                EnvironmentalCamoSettings.parseMode("unknown"));
    }

    @Test
    void appliesTpsHysteresis() {
        assertTrue(EnvironmentalCamoSettings.shouldUseViewerBackdrop(20.0D, true, 17.0D, 18.5D));
        assertFalse(EnvironmentalCamoSettings.shouldUseViewerBackdrop(16.9D, true, 17.0D, 18.5D));
        assertFalse(EnvironmentalCamoSettings.shouldUseViewerBackdrop(18.0D, false, 17.0D, 18.5D));
        assertTrue(EnvironmentalCamoSettings.shouldUseViewerBackdrop(18.5D, false, 17.0D, 18.5D));
    }

    @Test
    void parsesHexColorsWithFallback() {
        assertEquals(0x77ADFF, EnvironmentalCamoSettings.parseHexColor("#77ADFF", 0));
        assertEquals(0x0B1026, EnvironmentalCamoSettings.parseHexColor("0B1026", 0));
        assertEquals(0x123456, EnvironmentalCamoSettings.parseHexColor("not-a-color", 0x123456));
    }
}
