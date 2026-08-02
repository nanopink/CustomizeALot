package com.customizealot;

import java.awt.Color;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public enum CustomizeALotHealthBarPreset
{
	CUSTOM("Custom", runeScapeSettings()),
	RUNESCAPE("RuneScape", runeScapeSettings()),
	RUINED_HEIR("Ruined Heir", ruinedHeirSettings());

	static final CustomizeALotHealthBarPreset DEFAULT = RUINED_HEIR;

	private final String displayName;
	private final Map<String, Object> settings;

	CustomizeALotHealthBarPreset(String displayName, Map<String, Object> settings)
	{
		this.displayName = displayName;
		this.settings = settings;
	}

	Map<String, Object> getSettings()
	{
		return settings;
	}

	private static Map<String, Object> runeScapeSettings()
	{
		Map<String, Object> values = baseSettings();
		values.put(CustomizeALotConfig.HEALTH_BAR_STYLE_KEY,
			CustomizeALotHealthBarStyle.NATIVE);
		return Collections.unmodifiableMap(values);
	}

	private static Map<String, Object> ruinedHeirSettings()
	{
		Map<String, Object> values = new LinkedHashMap<>(version2RuinedHeirSettings());
		values.put(CustomizeALotConfig.HEALTH_BAR_Y_OFFSET_KEY, 2);
		return Collections.unmodifiableMap(values);
	}

	static Map<String, Object> version2RuinedHeirSettings()
	{
		Map<String, Object> values = new LinkedHashMap<>(previousRuinedHeirSettings());
		values.put(CustomizeALotConfig.HEALTH_BAR_SCALE_MODE_KEY,
			CustomizeALotHealthScaleMode.THRESHOLD);
		values.put(CustomizeALotConfig.HEALTH_BAR_LARGE_HEIGHT_SCALE_PERCENT_KEY, 100);
		values.put(CustomizeALotConfig.HEALTH_BAR_HEIGHT_KEY, 5.0);
		return Collections.unmodifiableMap(values);
	}

	static Map<String, Object> previousRuinedHeirSettings()
	{
		Map<String, Object> values = new LinkedHashMap<>(legacyRuinedHeirSettings());
		values.put(CustomizeALotConfig.HEALTH_BAR_SOLID_WIDTH_KEY, 50.0);
		values.put(CustomizeALotConfig.HEALTH_BAR_HEIGHT_KEY, 6.0);
		values.put(CustomizeALotConfig.HEALTH_BAR_FRONT_COLOR_KEY,
			new Color(0xFF34F434, true));
		values.put(CustomizeALotConfig.HEALTH_BAR_FRONT_SECONDARY_COLOR_KEY,
			new Color(0xFF18E418, true));
		values.put(CustomizeALotConfig.HEALTH_BAR_BACK_COLOR_KEY,
			new Color(0xFFC01D1D, true));
		values.put(CustomizeALotConfig.HEALTH_BAR_BACK_SECONDARY_COLOR_KEY,
			new Color(0xFF901818, true));
		values.put(CustomizeALotConfig.HEALTH_BAR_DAMAGE_TRAIL_COLOR_KEY,
			new Color(0xFFFF001F, true));
		values.put(CustomizeALotConfig.HEALTH_BAR_DAMAGE_TRAIL_HOLD_KEY, 445);
		values.put(CustomizeALotConfig.HEALTH_BAR_DAMAGE_TRAIL_DRAIN_KEY, 245);
		values.put(CustomizeALotConfig.HEALTH_BAR_SEGMENT_COLOR_KEY,
			new Color(0x3F000000, true));
		values.put(CustomizeALotConfig.HEALTH_BAR_SEGMENT_THICKNESS_KEY, 0.6);
		values.put(CustomizeALotConfig.HEALTH_BAR_BORDER_THICKNESS_KEY, 0.1);
		values.put(CustomizeALotConfig.HEALTH_BAR_CORNER_RADIUS_KEY, 0.5);
		return Collections.unmodifiableMap(values);
	}

	static Map<String, Object> legacyRuinedHeirSettings()
	{
		Map<String, Object> values = baseSettings();
		values.put(CustomizeALotConfig.HEALTH_BAR_STYLE_KEY,
			CustomizeALotHealthBarStyle.SOLID);
		values.put(CustomizeALotConfig.HEALTH_BAR_SOLID_WIDTH_KEY, 49.0);
		values.put(CustomizeALotConfig.HEALTH_BAR_HEIGHT_KEY, 5.0);
		values.put(CustomizeALotConfig.HEALTH_BAR_FRONT_GRADIENT_COORDINATES_KEY,
			CustomizeALotHealthBarGradientCoordinates.FULL_BAR);
		values.put(CustomizeALotConfig.HEALTH_BAR_BACK_GRADIENT_COORDINATES_KEY,
			CustomizeALotHealthBarGradientCoordinates.FULL_BAR);
		values.put(CustomizeALotConfig.HEALTH_BAR_FRONT_GRADIENT_KEY,
			CustomizeALotHealthBarGradient.HORIZONTAL);
		values.put(CustomizeALotConfig.HEALTH_BAR_FRONT_COLOR_KEY,
			new Color(68, 202, 103));
		values.put(CustomizeALotConfig.HEALTH_BAR_FRONT_SECONDARY_COLOR_KEY,
			new Color(35, 157, 76));
		values.put(CustomizeALotConfig.HEALTH_BAR_POISONED_FRONT_COLOR_KEY,
			new Color(132, 204, 66));
		values.put(CustomizeALotConfig.HEALTH_BAR_BACK_GRADIENT_KEY,
			CustomizeALotHealthBarGradient.HORIZONTAL);
		values.put(CustomizeALotConfig.HEALTH_BAR_BACK_COLOR_KEY,
			new Color(174, 55, 55));
		values.put(CustomizeALotConfig.HEALTH_BAR_BACK_SECONDARY_COLOR_KEY,
			new Color(116, 34, 34));
		values.put(CustomizeALotConfig.HEALTH_BAR_DAMAGE_TRAIL_COLOR_KEY,
			Color.RED);
		values.put(CustomizeALotConfig.HEALTH_BAR_SEGMENTS_ENABLED_KEY, true);
		values.put(CustomizeALotConfig.HEALTH_BAR_SEGMENT_COLOR_KEY,
			new Color(0x3F000000, true));
		values.put(CustomizeALotConfig.HEALTH_BAR_SEGMENT_THICKNESS_KEY, 0.5);
		values.put(CustomizeALotConfig.HEALTH_BAR_BORDER_COLOR_KEY,
			new Color(25, 25, 25, 230));
		values.put(CustomizeALotConfig.HEALTH_BAR_BORDER_THICKNESS_KEY, 0.5);
		values.put(CustomizeALotConfig.HEALTH_BAR_CORNER_RADIUS_KEY, 0.5);
		return Collections.unmodifiableMap(values);
	}

	private static Map<String, Object> baseSettings()
	{
		Map<String, Object> values = new LinkedHashMap<>();
		values.put(CustomizeALotConfig.HEALTH_BAR_SCALE_MODE_KEY,
			CustomizeALotHealthScaleMode.FIXED);
		values.put(CustomizeALotConfig.HEALTH_BAR_SCALE_PERCENT_KEY, 100);
		values.put(CustomizeALotConfig.HEALTH_BAR_SCALE_THRESHOLD_KEY, 100);
		values.put(CustomizeALotConfig.HEALTH_BAR_LARGE_SCALE_PERCENT_KEY, 150);
		values.put(CustomizeALotConfig.HEALTH_BAR_LARGE_HEIGHT_SCALE_PERCENT_KEY, 100);
		values.put(CustomizeALotConfig.HEALTH_BAR_SOLID_WIDTH_KEY, 30.0);
		values.put(CustomizeALotConfig.HEALTH_BAR_HEIGHT_KEY, 5.0);
		values.put(CustomizeALotConfig.HEALTH_BAR_X_OFFSET_KEY, 0);
		values.put(CustomizeALotConfig.HEALTH_BAR_Y_OFFSET_KEY, 0);
		values.put(CustomizeALotConfig.HEALTH_BAR_FILL_DIRECTION_KEY,
			CustomizeALotHealthBarFillDirection.LEFT_TO_RIGHT);
		values.put(CustomizeALotConfig.HEALTH_BAR_FRONT_GRADIENT_COORDINATES_KEY,
			CustomizeALotHealthBarGradientCoordinates.SEGMENT);
		values.put(CustomizeALotConfig.HEALTH_BAR_BACK_GRADIENT_COORDINATES_KEY,
			CustomizeALotHealthBarGradientCoordinates.SEGMENT);
		values.put(CustomizeALotConfig.HEALTH_BAR_FRONT_GRADIENT_KEY,
			CustomizeALotHealthBarGradient.SOLID);
		values.put(CustomizeALotConfig.HEALTH_BAR_FRONT_COLOR_KEY,
			new Color(45, 190, 88));
		values.put(CustomizeALotConfig.HEALTH_BAR_FRONT_SECONDARY_COLOR_KEY,
			new Color(34, 158, 72));
		values.put(CustomizeALotConfig.HEALTH_BAR_POISONED_FRONT_COLOR_KEY,
			new Color(118, 190, 60));
		values.put(CustomizeALotConfig.HEALTH_BAR_BACK_GRADIENT_KEY,
			CustomizeALotHealthBarGradient.SOLID);
		values.put(CustomizeALotConfig.HEALTH_BAR_BACK_COLOR_KEY,
			new Color(184, 60, 60));
		values.put(CustomizeALotConfig.HEALTH_BAR_BACK_SECONDARY_COLOR_KEY,
			new Color(151, 45, 45));
		values.put(CustomizeALotConfig.HEALTH_BAR_DAMAGE_TRAIL_ENABLED_KEY, true);
		values.put(CustomizeALotConfig.HEALTH_BAR_DAMAGE_TRAIL_COLOR_KEY,
			new Color(245, 185, 66, 210));
		values.put(CustomizeALotConfig.HEALTH_BAR_DAMAGE_TRAIL_HOLD_KEY, 400);
		values.put(CustomizeALotConfig.HEALTH_BAR_DAMAGE_TRAIL_DRAIN_KEY, 600);
		values.put(CustomizeALotConfig.HEALTH_BAR_SEGMENTS_ENABLED_KEY, false);
		values.put(CustomizeALotConfig.HEALTH_BAR_SEGMENT_VALUE_MODE_KEY,
			CustomizeALotHealthBarSegmentValueMode.EXACT_HP_WITH_PUBLIC_FALLBACK);
		values.put(CustomizeALotConfig.HEALTH_BAR_HITPOINTS_PER_SEGMENT_KEY, 10);
		values.put(CustomizeALotConfig.HEALTH_BAR_SEGMENT_COLOR_KEY,
			new Color(0, 0, 0, 160));
		values.put(CustomizeALotConfig.HEALTH_BAR_SEGMENT_THICKNESS_KEY, 1.0);
		values.put(CustomizeALotConfig.HEALTH_BAR_BORDER_COLOR_KEY, Color.BLACK);
		values.put(CustomizeALotConfig.HEALTH_BAR_BORDER_THICKNESS_KEY, 1.0);
		values.put(CustomizeALotConfig.HEALTH_BAR_CORNER_RADIUS_KEY, 0.0);
		return values;
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
