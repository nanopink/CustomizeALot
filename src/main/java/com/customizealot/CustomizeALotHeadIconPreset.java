package com.customizealot;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public enum CustomizeALotHeadIconPreset
{
	CUSTOM("Custom", runeScapeSettings()),
	RUNESCAPE("RuneScape", runeScapeSettings());

	static final CustomizeALotHeadIconPreset DEFAULT = RUNESCAPE;

	private final String displayName;
	private final Map<String, Object> settings;

	CustomizeALotHeadIconPreset(String displayName, Map<String, Object> settings)
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
		Map<String, Object> values = new LinkedHashMap<>();
		values.put(CustomizeALotConfig.SHOW_PRAYER_ICONS_KEY, true);
		values.put(CustomizeALotConfig.SHOW_PRAYER_ICON_BACKGROUND_KEY, true);
		values.put(CustomizeALotConfig.SHOW_SKULL_ICONS_KEY, true);
		values.put(CustomizeALotConfig.SHOW_NPC_ICONS_KEY, true);
		values.put(CustomizeALotConfig.SHOW_HINT_ARROWS_KEY, true);
		values.put(CustomizeALotConfig.HEAD_ICON_SCALE_PERCENT_KEY, 100);
		values.put(CustomizeALotConfig.HEAD_ICON_X_OFFSET_KEY, 0);
		values.put(CustomizeALotConfig.HEAD_ICON_Y_OFFSET_KEY, 0);
		values.put(CustomizeALotConfig.HEAD_ICON_SPACING_KEY, 2);
		return Collections.unmodifiableMap(values);
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
