package com.customizealot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.Map;
import net.runelite.client.config.FontType;
import org.junit.Test;

public class CustomizeALotSectionPresetWorkflowTest
{
	@Test
	public void sectionsWithRuinedHeirPresetsSelectThemByDefault()
	{
		CustomizeALotConfig config = new CustomizeALotConfig()
		{
		};

		assertSame(CustomizeALotPreset.RUINED_HEIRS_ONE_TICK, config.preset());
		assertSame(CustomizeALotHealthBarPreset.RUINED_HEIR, config.healthBarPreset());
		assertSame(CustomizeALotOverheadChatPreset.RUINED_HEIR, config.overheadChatPreset());
		assertSame(CustomizeALotHeadIconPreset.RUNESCAPE, config.headIconPreset());
		assertTrue(CustomizeALotPlugin.presetMatchesConfig(config.preset(), config));
		assertTrue(CustomizeALotPlugin.healthBarPresetMatchesConfig(config.healthBarPreset(), config));
		assertTrue(CustomizeALotPlugin.overheadChatPresetMatchesConfig(
			config.overheadChatPreset(),
			config));
	}

	@Test
	public void healthPresetCopiesEveryControlledSettingOnce()
	{
		Map<String, Object> values = new LinkedHashMap<>();
		CustomizeALotPlugin.applyHealthBarPreset(
			CustomizeALotHealthBarPreset.RUINED_HEIR,
			values::put);

		assertEquals(32, values.size());
		assertSame(CustomizeALotHealthBarStyle.SOLID,
			values.get(CustomizeALotConfig.HEALTH_BAR_STYLE_KEY));
		assertSame(CustomizeALotHealthScaleMode.THRESHOLD,
			values.get(CustomizeALotConfig.HEALTH_BAR_SCALE_MODE_KEY));
		assertEquals(150,
			values.get(CustomizeALotConfig.HEALTH_BAR_LARGE_SCALE_PERCENT_KEY));
		assertEquals(100,
			values.get(CustomizeALotConfig.HEALTH_BAR_LARGE_HEIGHT_SCALE_PERCENT_KEY));
		assertEquals(50.0,
			((Number) values.get(CustomizeALotConfig.HEALTH_BAR_SOLID_WIDTH_KEY)).doubleValue(),
			0.0);
		assertEquals(5.0,
			((Number) values.get(CustomizeALotConfig.HEALTH_BAR_HEIGHT_KEY)).doubleValue(),
			0.0);
		assertEquals(2, values.get(CustomizeALotConfig.HEALTH_BAR_Y_OFFSET_KEY));
		assertEquals(new Color(0xFF34F434, true),
			values.get(CustomizeALotConfig.HEALTH_BAR_FRONT_COLOR_KEY));
		assertEquals(new Color(0xFF18E418, true),
			values.get(CustomizeALotConfig.HEALTH_BAR_FRONT_SECONDARY_COLOR_KEY));
		assertEquals(new Color(0xFFC01D1D, true),
			values.get(CustomizeALotConfig.HEALTH_BAR_BACK_COLOR_KEY));
		assertEquals(new Color(0xFF901818, true),
			values.get(CustomizeALotConfig.HEALTH_BAR_BACK_SECONDARY_COLOR_KEY));
		assertEquals(true, values.get(CustomizeALotConfig.HEALTH_BAR_SEGMENTS_ENABLED_KEY));
		assertEquals(new Color(0x3F000000, true),
			values.get(CustomizeALotConfig.HEALTH_BAR_SEGMENT_COLOR_KEY));
		assertEquals(0.6,
			((Number) values.get(CustomizeALotConfig.HEALTH_BAR_SEGMENT_THICKNESS_KEY)).doubleValue(),
			0.0);
		assertEquals(new Color(0xFFFF001F, true),
			values.get(CustomizeALotConfig.HEALTH_BAR_DAMAGE_TRAIL_COLOR_KEY));
		assertEquals(445, values.get(CustomizeALotConfig.HEALTH_BAR_DAMAGE_TRAIL_HOLD_KEY));
		assertEquals(245, values.get(CustomizeALotConfig.HEALTH_BAR_DAMAGE_TRAIL_DRAIN_KEY));
		assertEquals(0.1,
			((Number) values.get(CustomizeALotConfig.HEALTH_BAR_BORDER_THICKNESS_KEY)).doubleValue(),
			0.0);
		assertEquals(0.5,
			((Number) values.get(CustomizeALotConfig.HEALTH_BAR_CORNER_RADIUS_KEY)).doubleValue(),
			0.0);
		assertSame(CustomizeALotHealthBarGradientCoordinates.FULL_BAR,
			values.get(CustomizeALotConfig.HEALTH_BAR_FRONT_GRADIENT_COORDINATES_KEY));
		assertSame(CustomizeALotHealthBarGradientCoordinates.FULL_BAR,
			values.get(CustomizeALotConfig.HEALTH_BAR_BACK_GRADIENT_COORDINATES_KEY));
		assertFalse(values.containsKey(CustomizeALotConfig.HEALTH_BARS_ENABLED_KEY));
		assertFalse(values.containsKey(CustomizeALotConfig.HEALTH_BAR_GRADIENT_COORDINATES_KEY));
		assertFalse(values.containsKey(CustomizeALotConfig.HEALTH_BAR_PUBLIC_UNITS_PER_SEGMENT_KEY));
	}

	@Test
	public void chatAndHeadIconPresetsCopyOnlyTheirSectionSettings()
	{
		Map<String, Object> chat = new LinkedHashMap<>();
		Map<String, Object> icons = new LinkedHashMap<>();
		CustomizeALotPlugin.applyOverheadChatPreset(
			CustomizeALotOverheadChatPreset.RUNESCAPE,
			chat::put);
		CustomizeALotPlugin.applyHeadIconPreset(
			CustomizeALotHeadIconPreset.RUNESCAPE,
			icons::put);

		assertEquals(12, chat.size());
		assertSame(FontType.BOLD, chat.get(CustomizeALotConfig.OVERHEAD_CHAT_FONT_KEY));
		assertEquals(true, chat.get(CustomizeALotConfig.SHOW_NPC_OVERHEAD_CHAT_KEY));
		assertEquals(false,
			chat.get(CustomizeALotConfig.OVERHEAD_CHAT_RELATIONSHIP_COLORS_KEY));
		assertEquals(Color.YELLOW,
			chat.get(CustomizeALotConfig.OVERHEAD_CHAT_FRIEND_COLOR_KEY));
		assertEquals(Color.YELLOW,
			chat.get(CustomizeALotConfig.OVERHEAD_CHAT_CLAN_COLOR_KEY));
		assertEquals(Color.YELLOW,
			chat.get(CustomizeALotConfig.OVERHEAD_CHAT_GROUP_IRON_COLOR_KEY));
		assertFalse(chat.containsKey(CustomizeALotConfig.OVERHEAD_CHAT_ENABLED_KEY));

		assertEquals(8, icons.size());
		assertEquals(100, icons.get(CustomizeALotConfig.HEAD_ICON_SCALE_PERCENT_KEY));
		assertEquals(2, icons.get(CustomizeALotConfig.HEAD_ICON_SPACING_KEY));
		assertFalse(icons.containsKey(CustomizeALotConfig.HEAD_ICONS_ENABLED_KEY));
	}

	@Test
	public void ruinedHeirChatPresetKeepsRuneScapeBehaviorWithItsOwnColors()
	{
		Map<String, Object> values = new LinkedHashMap<>();
		CustomizeALotPlugin.applyOverheadChatPreset(
			CustomizeALotOverheadChatPreset.RUINED_HEIR,
			values::put);

		assertEquals(12, values.size());
		assertSame(FontType.BOLD, values.get(CustomizeALotConfig.OVERHEAD_CHAT_FONT_KEY));
		assertSame(CustomizeALotOverheadChatEffect.STATIC,
			values.get(CustomizeALotConfig.OVERHEAD_CHAT_EFFECT_KEY));
		assertEquals(new Color(0xFF, 0xFF, 0x3F, 0xFF),
			values.get(CustomizeALotConfig.OVERHEAD_CHAT_COLOR_KEY));
		assertEquals(new Color(0x80171717, true),
			values.get(CustomizeALotConfig.OVERHEAD_CHAT_SHADOW_COLOR_KEY));
		assertEquals(true,
			values.get(CustomizeALotConfig.OVERHEAD_CHAT_RELATIONSHIP_COLORS_KEY));
		assertEquals(new Color(0xFFA5FF40, true),
			values.get(CustomizeALotConfig.OVERHEAD_CHAT_FRIEND_COLOR_KEY));
		assertEquals(new Color(0xFF40CFFF, true),
			values.get(CustomizeALotConfig.OVERHEAD_CHAT_CLAN_COLOR_KEY));
		assertEquals(new Color(0xFFFF4040, true),
			values.get(CustomizeALotConfig.OVERHEAD_CHAT_GROUP_IRON_COLOR_KEY));
	}

	@Test
	public void selectedPresetsMatchFreshConfigurationDefaults()
	{
		CustomizeALotConfig config = new CustomizeALotConfig()
		{
		};

		assertTrue(CustomizeALotPlugin.healthBarPresetMatchesConfig(
			CustomizeALotHealthBarPreset.RUINED_HEIR, config));
		assertTrue(CustomizeALotPlugin.overheadChatPresetMatchesConfig(
			CustomizeALotOverheadChatPreset.RUINED_HEIR, config));
		assertTrue(CustomizeALotPlugin.headIconPresetMatchesConfig(
			CustomizeALotHeadIconPreset.RUNESCAPE, config));
	}

	@Test
	public void ruinedHeirChatPresetMatchesItsAppliedConfiguration()
	{
		CustomizeALotConfig config = new CustomizeALotConfig()
		{
			@Override
			public CustomizeALotOverheadChatPreset overheadChatPreset()
			{
				return CustomizeALotOverheadChatPreset.RUINED_HEIR;
			}

			@Override
			public Color overheadChatColor()
			{
				return new Color(0xFF, 0xFF, 0x3F, 0xFF);
			}

			@Override
			public Color overheadChatShadowColor()
			{
				return new Color(0x80171717, true);
			}

			@Override
			public boolean overheadChatRelationshipColors()
			{
				return true;
			}

			@Override
			public Color overheadChatFriendColor()
			{
				return new Color(0xFFA5FF40, true);
			}

			@Override
			public Color overheadChatClanColor()
			{
				return new Color(0xFF40CFFF, true);
			}

			@Override
			public Color overheadChatGroupIronColor()
			{
				return new Color(0xFFFF4040, true);
			}
		};

		assertTrue(CustomizeALotPlugin.overheadChatPresetMatchesConfig(
			CustomizeALotOverheadChatPreset.RUINED_HEIR, config));
		assertFalse(CustomizeALotPlugin.overheadChatPresetMatchesConfig(
			CustomizeALotOverheadChatPreset.RUNESCAPE, config));
	}

	@Test
	public void sectionPresetMigrationRefreshesOnlyThePreviousBuiltInValues()
	{
		CustomizeALotConfig previousChat = previousRuinedHeirChatConfig(0);
		CustomizeALotConfig editedWhileDisabled = previousRuinedHeirChatConfig(1);

		assertTrue(CustomizeALotPlugin.overheadChatSettingsMatchConfig(
			CustomizeALotOverheadChatPreset.legacyRuinedHeirSettings(),
			previousChat));
		assertFalse(CustomizeALotPlugin.overheadChatSettingsMatchConfig(
			CustomizeALotOverheadChatPreset.legacyRuinedHeirSettings(),
			editedWhileDisabled));
		assertTrue(CustomizeALotPlugin.shouldRefreshUpdatedSectionPreset(
			true,
			true,
			true));
		assertFalse(CustomizeALotPlugin.shouldRefreshUpdatedSectionPreset(
			true,
			true,
			false));
		assertFalse(CustomizeALotPlugin.shouldRefreshUpdatedSectionPreset(
			false,
			true,
			true));
		assertFalse(CustomizeALotPlugin.shouldRefreshUpdatedSectionPreset(
			true,
			false,
			true));

		Map<String, Object> legacyHealth = CustomizeALotHealthBarPreset.legacyRuinedHeirSettings();
		assertEquals(32, legacyHealth.size());
		assertEquals(49.0, legacyHealth.get(CustomizeALotConfig.HEALTH_BAR_SOLID_WIDTH_KEY));
		assertEquals(5.0, legacyHealth.get(CustomizeALotConfig.HEALTH_BAR_HEIGHT_KEY));
		assertEquals(400, legacyHealth.get(CustomizeALotConfig.HEALTH_BAR_DAMAGE_TRAIL_HOLD_KEY));
		assertEquals(600, legacyHealth.get(CustomizeALotConfig.HEALTH_BAR_DAMAGE_TRAIL_DRAIN_KEY));

		Map<String, Object> previousHealth =
			CustomizeALotHealthBarPreset.previousRuinedHeirSettings();
		assertEquals(32, previousHealth.size());
		assertEquals(50.0, previousHealth.get(CustomizeALotConfig.HEALTH_BAR_SOLID_WIDTH_KEY));
		assertEquals(6.0, previousHealth.get(CustomizeALotConfig.HEALTH_BAR_HEIGHT_KEY));
		assertSame(CustomizeALotHealthScaleMode.FIXED,
			previousHealth.get(CustomizeALotConfig.HEALTH_BAR_SCALE_MODE_KEY));

		Map<String, Object> version2Health =
			CustomizeALotHealthBarPreset.version2RuinedHeirSettings();
		assertEquals(32, version2Health.size());
		assertEquals(50.0, version2Health.get(CustomizeALotConfig.HEALTH_BAR_SOLID_WIDTH_KEY));
		assertEquals(5.0, version2Health.get(CustomizeALotConfig.HEALTH_BAR_HEIGHT_KEY));
		assertEquals(0, version2Health.get(CustomizeALotConfig.HEALTH_BAR_Y_OFFSET_KEY));
		assertSame(CustomizeALotHealthScaleMode.THRESHOLD,
			version2Health.get(CustomizeALotConfig.HEALTH_BAR_SCALE_MODE_KEY));
	}

	@Test
	public void mismatchedExistingValuesAreRecognizedAsCustomWithoutOverwrite()
	{
		CustomizeALotConfig config = new CustomizeALotConfig()
		{
			@Override
			public double healthBarSolidWidth()
			{
				return 73.5;
			}

			@Override
			public int overheadChatXOffset()
			{
				return 11;
			}

			@Override
			public int headIconSpacing()
			{
				return 7;
			}
		};

		assertFalse(CustomizeALotPlugin.healthBarPresetMatchesConfig(
			CustomizeALotHealthBarPreset.RUNESCAPE, config));
		assertFalse(CustomizeALotPlugin.overheadChatPresetMatchesConfig(
			CustomizeALotOverheadChatPreset.RUNESCAPE, config));
		assertFalse(CustomizeALotPlugin.headIconPresetMatchesConfig(
			CustomizeALotHeadIconPreset.RUNESCAPE, config));
	}

	@Test
	public void legacyHealthValuesFlowIntoNewKeysAndAreRecognizedAsCustom()
	{
		CustomizeALotConfig legacy = new CustomizeALotConfig()
		{
			@Override
			public CustomizeALotHealthBarGradientCoordinates healthBarGradientCoordinates()
			{
				return CustomizeALotHealthBarGradientCoordinates.FULL_BAR;
			}

			@Override
			public int healthBarPublicUnitsPerSegment()
			{
				return 8;
			}
		};

		assertSame(CustomizeALotHealthBarGradientCoordinates.FULL_BAR,
			legacy.healthBarFrontGradientCoordinates());
		assertSame(CustomizeALotHealthBarGradientCoordinates.FULL_BAR,
			legacy.healthBarBackGradientCoordinates());
		assertEquals(8, legacy.healthBarHitpointsPerSegment());
		assertFalse(CustomizeALotPlugin.healthBarPresetMatchesConfig(
			CustomizeALotHealthBarPreset.RUNESCAPE, legacy));
	}

	@Test
	public void controlledEditsSelectOnlyTheirOwnCustomPreset()
	{
		assertTrue(CustomizeALotPlugin.shouldSwitchHealthBarToCustom(
			false,
			CustomizeALotConfig.HEALTH_BAR_FRONT_COLOR_KEY,
			CustomizeALotHealthBarPreset.RUNESCAPE));
		assertTrue(CustomizeALotPlugin.shouldSwitchOverheadChatToCustom(
			false,
			CustomizeALotConfig.OVERHEAD_CHAT_FONT_KEY,
			CustomizeALotOverheadChatPreset.RUNESCAPE));
		assertTrue(CustomizeALotPlugin.shouldSwitchHeadIconToCustom(
			false,
			CustomizeALotConfig.HEAD_ICON_SPACING_KEY,
			CustomizeALotHeadIconPreset.RUNESCAPE));

		assertFalse(CustomizeALotPlugin.shouldSwitchHealthBarToCustom(
			false,
			CustomizeALotConfig.HEALTH_BARS_ENABLED_KEY,
			CustomizeALotHealthBarPreset.RUNESCAPE));
		assertFalse(CustomizeALotPlugin.shouldSwitchOverheadChatToCustom(
			false,
			CustomizeALotConfig.OVERHEAD_CHAT_ENABLED_KEY,
			CustomizeALotOverheadChatPreset.RUNESCAPE));
		assertFalse(CustomizeALotPlugin.shouldSwitchHeadIconToCustom(
			false,
			CustomizeALotConfig.HEAD_ICONS_ENABLED_KEY,
			CustomizeALotHeadIconPreset.RUNESCAPE));
		assertFalse(CustomizeALotPlugin.shouldSwitchHeadIconToCustom(
			true,
			CustomizeALotConfig.HEAD_ICON_SPACING_KEY,
			CustomizeALotHeadIconPreset.RUNESCAPE));
	}

	@Test
	public void healthBarSizeChangesAdvanceByOnePixelAndOffsetsByTwo()
	{
		assertEquals(51.0, CustomizeALotPlugin.steppedHealthBarPixelValue(
			false,
			CustomizeALotConfig.HEALTH_BAR_SOLID_WIDTH_KEY,
			"50.0",
			"50.1"));
		assertEquals(49.0, CustomizeALotPlugin.steppedHealthBarPixelValue(
			false,
			CustomizeALotConfig.HEALTH_BAR_SOLID_WIDTH_KEY,
			"50.0",
			"49.9"));
		assertEquals(6.0, CustomizeALotPlugin.steppedHealthBarPixelValue(
			false,
			CustomizeALotConfig.HEALTH_BAR_HEIGHT_KEY,
			"5.0",
			"5.1"));
		assertEquals(4.0, CustomizeALotPlugin.steppedHealthBarPixelValue(
			false,
			CustomizeALotConfig.HEALTH_BAR_HEIGHT_KEY,
			"5.0",
			"4.9"));
		assertEquals(2, CustomizeALotPlugin.steppedHealthBarPixelValue(
			false,
			CustomizeALotConfig.HEALTH_BAR_X_OFFSET_KEY,
			"0",
			"1"));
		assertEquals(-2, CustomizeALotPlugin.steppedHealthBarPixelValue(
			false,
			CustomizeALotConfig.HEALTH_BAR_Y_OFFSET_KEY,
			"0",
			"-1"));
	}

	@Test
	public void healthBarPixelSteppingLeavesPresetsAndLargerTypedChangesAlone()
	{
		assertEquals(null, CustomizeALotPlugin.steppedHealthBarPixelValue(
			true,
			CustomizeALotConfig.HEALTH_BAR_SOLID_WIDTH_KEY,
			"49.0",
			"50.0"));
		assertEquals(null, CustomizeALotPlugin.steppedHealthBarPixelValue(
			true,
			CustomizeALotConfig.HEALTH_BAR_X_OFFSET_KEY,
			"1",
			"2"));
		assertEquals(null, CustomizeALotPlugin.steppedHealthBarPixelValue(
			false,
			CustomizeALotConfig.HEALTH_BAR_SOLID_WIDTH_KEY,
			"50.0",
			"51.0"));
		assertEquals(null, CustomizeALotPlugin.steppedHealthBarPixelValue(
			false,
			CustomizeALotConfig.HEALTH_BAR_FRONT_COLOR_KEY,
			"1",
			"2"));
	}

	private static CustomizeALotConfig previousRuinedHeirChatConfig(int xOffset)
	{
		return new CustomizeALotConfig()
		{
			@Override
			public Color overheadChatColor()
			{
				return new Color(0xFF, 0xFF, 0x3F, 0xFF);
			}

			@Override
			public Color overheadChatShadowColor()
			{
				return new Color(0x17, 0x17, 0x17, 0xFF);
			}

			@Override
			public boolean overheadChatRelationshipColors()
			{
				return false;
			}

			@Override
			public Color overheadChatFriendColor()
			{
				return Color.YELLOW;
			}

			@Override
			public Color overheadChatClanColor()
			{
				return Color.YELLOW;
			}

			@Override
			public Color overheadChatGroupIronColor()
			{
				return Color.YELLOW;
			}

			@Override
			public int overheadChatXOffset()
			{
				return xOffset;
			}
		};
	}

	@Test
	public void sectionPresetParsersAcceptStoredAndDisplayNames()
	{
		assertSame(CustomizeALotHealthBarPreset.RUINED_HEIR,
			CustomizeALotPlugin.healthBarPresetFromValue("RUINED_HEIR"));
		assertSame(CustomizeALotHealthBarPreset.RUINED_HEIR,
			CustomizeALotPlugin.healthBarPresetFromValue("Ruined Heir"));
		assertSame(CustomizeALotOverheadChatPreset.RUNESCAPE,
			CustomizeALotPlugin.overheadChatPresetFromValue("RuneScape"));
		assertSame(CustomizeALotOverheadChatPreset.RUINED_HEIR,
			CustomizeALotPlugin.overheadChatPresetFromValue("RUINED_HEIR"));
		assertSame(CustomizeALotOverheadChatPreset.RUINED_HEIR,
			CustomizeALotPlugin.overheadChatPresetFromValue("Ruined Heir"));
		assertSame(CustomizeALotHeadIconPreset.CUSTOM,
			CustomizeALotPlugin.headIconPresetFromValue("CUSTOM"));
	}

	@Test
	public void pendingCustomSelectionRequiresTheSameNamedSectionPreset()
	{
		assertTrue(CustomizeALotPlugin.shouldApplyPendingCustomSelectionForNamedPreset(
			8, 8, false, true));
		assertFalse(CustomizeALotPlugin.shouldApplyPendingCustomSelectionForNamedPreset(
			8, 9, false, true));
		assertFalse(CustomizeALotPlugin.shouldApplyPendingCustomSelectionForNamedPreset(
			8, 8, true, true));
		assertFalse(CustomizeALotPlugin.shouldApplyPendingCustomSelectionForNamedPreset(
			8, 8, false, false));
	}
}
