package com.customizealot;

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.FontType;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup(CustomizeALotConfig.GROUP)
public interface CustomizeALotConfig extends Config
{
	String GROUP = "customize-a-lot";
	String LEGACY_GROUP = "hitsplat-customizer";
	String CONFIG_GROUP_MIGRATION_VERSION_KEY = "configGroupMigrationVersion";
	String LEGACY_LAYOUT_MODE_KEY = "layoutMode";
	String LEGACY_LAYOUT_STYLE_KEY = "layoutStyle";
	String LEGACY_SLOT_SPACING_KEY = "slotSpacing";
	String LEGACY_X_SPACING_KEY = "xSpacing";
	String LEGACY_Y_SPACING_KEY = "ySpacing";
	String PRESET_KEY = "preset";
	String PRESET_WORKFLOW_VERSION_KEY = "presetWorkflowVersion";
	String SECTION_PRESET_VERSION_KEY = "sectionPresetVersion";
	String DISABLE_ENEMY_HITSPLATS_KEY = "disableEnemyHitsplats";
	String DISABLE_ALLY_HITSPLATS_KEY = "disableAllyHitsplats";
	String DISABLE_MY_HITSPLATS_KEY = "disableMyHitsplats";
	String MAX_HITSPLATS_KEY = "maxHitsplats";
	String REUSE_OLD_HITSPLAT_SLOTS_KEY = "reuseOldHitsplatSlots";
	String HITSPLAT_REUSE_INTERVAL_KEY = "hitsplatReuseInterval";
	String LEGACY_OPACITY_KEY = "opacity";
	String OPACITY_PERCENT_KEY = "opacityPercent";
	String ONLY_DISPLAY_MINE_KEY = "onlyDisplayMine";
	String PRIORITIZE_MINE_KEY = "prioritizeMine";
	String HIDE_ZERO_HITSPLATS_KEY = "hideZeroHitsplats";
	String LAYOUT_SHAPE_KEY = "layoutShape";
	String LAYOUT_DIRECTION_KEY = "layoutDirection";
	String LAYOUT_BEHAVIOR_KEY = "layoutBehavior";
	String MIN_RADIUS_KEY = "minRadius";
	String MAX_RADIUS_KEY = "maxRadius";
	String X_SPACING_KEY = "xSpacingPixels";
	String Y_SPACING_KEY = "ySpacingPixels";
	String HITSPLAT_FONT_KEY = "hitsplatFont";
	String HITSPLAT_SCALE_PERCENT_KEY = "hitsplatScalePercent";
	String LEGACY_GLOBAL_X_OFFSET_KEY = "globalXOffset";
	String LEGACY_GLOBAL_Y_OFFSET_KEY = "globalYOffset";
	String LARGE_TARGET_DETECTION_KEY = "largeTargetDetection";
	String LARGE_TARGET_SIZE_KEY = "largeTargetSize";
	String LARGE_TARGET_HEALTH_SCALE_KEY = "largeTargetHealthScale";
	String LARGE_TARGET_X_OFFSET_KEY = "largeTargetXOffset";
	String LARGE_TARGET_Y_OFFSET_KEY = "largeTargetYOffset";
	String FADE_IN_DURATION_KEY = "fadeInDuration";
	String FULL_OPACITY_DURATION_KEY = "fullOpacityDuration";
	String FADE_OUT_DURATION_KEY = "fadeOutDuration";
	String HEALTH_BARS_ENABLED_KEY = "healthBarsEnabled";
	String HEALTH_BAR_PRESET_KEY = "healthBarPreset";
	String HEALTH_BAR_STYLE_KEY = "healthBarStyle";
	String HEALTH_BAR_SCALE_MODE_KEY = "healthBarScaleMode";
	String HEALTH_BAR_SCALE_PERCENT_KEY = "healthBarScalePercent";
	String HEALTH_BAR_SCALE_THRESHOLD_KEY = "healthBarScaleThreshold";
	String HEALTH_BAR_LARGE_SCALE_PERCENT_KEY = "healthBarLargeScalePercent";
	String HEALTH_BAR_LARGE_HEIGHT_SCALE_PERCENT_KEY = "healthBarLargeHeightScalePercent";
	String HEALTH_BAR_SOLID_WIDTH_KEY = "healthBarSolidWidth";
	String HEALTH_BAR_HEIGHT_KEY = "healthBarHeight";
	String HEALTH_BAR_X_OFFSET_KEY = "healthBarXOffset";
	String HEALTH_BAR_Y_OFFSET_KEY = "healthBarYOffset";
	String HEALTH_BAR_FILL_DIRECTION_KEY = "healthBarFillDirection";
	String HEALTH_BAR_GRADIENT_COORDINATES_KEY = "healthBarGradientCoordinates";
	String HEALTH_BAR_FRONT_GRADIENT_COORDINATES_KEY = "healthBarFrontGradientCoordinates";
	String HEALTH_BAR_BACK_GRADIENT_COORDINATES_KEY = "healthBarBackGradientCoordinates";
	String HEALTH_BAR_FRONT_GRADIENT_KEY = "healthBarFrontGradient";
	String HEALTH_BAR_FRONT_COLOR_KEY = "healthBarFrontColor";
	String HEALTH_BAR_FRONT_SECONDARY_COLOR_KEY = "healthBarFrontSecondaryColor";
	String HEALTH_BAR_POISONED_FRONT_COLOR_KEY = "healthBarPoisonedFrontColor";
	String HEALTH_BAR_BACK_GRADIENT_KEY = "healthBarBackGradient";
	String HEALTH_BAR_BACK_COLOR_KEY = "healthBarBackColor";
	String HEALTH_BAR_BACK_SECONDARY_COLOR_KEY = "healthBarBackSecondaryColor";
	String HEALTH_BAR_DAMAGE_TRAIL_ENABLED_KEY = "healthBarDamageTrailEnabled";
	String HEALTH_BAR_DAMAGE_TRAIL_COLOR_KEY = "healthBarDamageTrailColor";
	String HEALTH_BAR_DAMAGE_TRAIL_HOLD_KEY = "healthBarDamageTrailHold";
	String HEALTH_BAR_DAMAGE_TRAIL_DRAIN_KEY = "healthBarDamageTrailDrain";
	String HEALTH_BAR_SEGMENTS_ENABLED_KEY = "healthBarSegmentsEnabled";
	String HEALTH_BAR_PUBLIC_UNITS_PER_SEGMENT_KEY = "healthBarPublicUnitsPerSegment";
	String HEALTH_BAR_SEGMENT_VALUE_MODE_KEY = "healthBarSegmentValueMode";
	String HEALTH_BAR_HITPOINTS_PER_SEGMENT_KEY = "healthBarHitpointsPerSegment";
	String HEALTH_BAR_SEGMENT_COLOR_KEY = "healthBarSegmentColor";
	String HEALTH_BAR_SEGMENT_THICKNESS_KEY = "healthBarSegmentThickness";
	String HEALTH_BAR_BORDER_COLOR_KEY = "healthBarBorderColor";
	String HEALTH_BAR_BORDER_THICKNESS_KEY = "healthBarBorderThickness";
	String HEALTH_BAR_CORNER_RADIUS_KEY = "healthBarCornerRadius";
	String LEGACY_CUSTOMIZE_OVERHEAD_CHAT_KEY = "customizeOverheadChat";
	String OVERHEAD_CHAT_ENABLED_KEY = "overheadChatEnabled";
	String OVERHEAD_CHAT_PRESET_KEY = "overheadChatPreset";
	String SHOW_NPC_OVERHEAD_CHAT_KEY = "showNpcOverheadChat";
	String LEGACY_OVERHEAD_CHAT_FONT_SIZE_KEY = "overheadChatFontSize";
	String OVERHEAD_CHAT_FONT_KEY = "overheadChatFont";
	String OVERHEAD_CHAT_COLOR_KEY = "overheadChatColor";
	String OVERHEAD_CHAT_RELATIONSHIP_COLORS_KEY = "overheadChatRelationshipColors";
	String OVERHEAD_CHAT_FRIEND_COLOR_KEY = "overheadChatFriendColor";
	String OVERHEAD_CHAT_CLAN_COLOR_KEY = "overheadChatClanColor";
	String OVERHEAD_CHAT_GROUP_IRON_COLOR_KEY = "overheadChatGroupIronColor";
	String OVERHEAD_CHAT_EFFECT_KEY = "overheadChatEffect";
	String OVERHEAD_CHAT_SHADOW_KEY = "overheadChatShadow";
	String OVERHEAD_CHAT_SHADOW_COLOR_KEY = "overheadChatShadowColor";
	String OVERHEAD_CHAT_X_OFFSET_KEY = "overheadChatXOffset";
	String OVERHEAD_CHAT_Y_OFFSET_KEY = "overheadChatYOffset";
	String HEAD_ICONS_ENABLED_KEY = "headIconsEnabled";
	String HEAD_ICON_PRESET_KEY = "headIconPreset";
	String SHOW_PRAYER_ICONS_KEY = "showPrayerIcons";
	String SHOW_SKULL_ICONS_KEY = "showSkullIcons";
	String SHOW_NPC_ICONS_KEY = "showNpcIcons";
	String SHOW_HINT_ARROWS_KEY = "showHintArrows";
	String HEAD_ICON_SCALE_PERCENT_KEY = "headIconScalePercent";
	String HEAD_ICON_X_OFFSET_KEY = "headIconXOffset";
	String HEAD_ICON_Y_OFFSET_KEY = "headIconYOffset";
	String HEAD_ICON_SPACING_KEY = "headIconSpacing";
	String SPRITE_SCALING_MODE_KEY = "spriteScalingMode";

	@ConfigSection(
		name = "General",
		description = "Settings shared by more than one replacement renderer.",
		position = 1
	)
	String GENERAL_SECTION = "general";

	@ConfigSection(
		name = "Hitsplats",
		description = "All replacement hitsplat controls. Preset-controlled settings are listed first; visibility, slot reuse, opacity, and boss-like detection remain independent.",
		position = 2
	)
	String DISPLAY_SECTION = "display";

	@ConfigSection(
		name = "Health bars",
		description = "Health bars reconstructed from public ratio and scale. Special bars and native animations may differ.",
		position = 3,
		closedByDefault = true
	)
	String HEALTH_BARS_SECTION = "healthBars";

	@ConfigSection(
		name = "Overhead chat",
		description = "Replacement overhead-chat visibility, font, RGBA colors, global effect, shadow, and positioning. Native filtering is approximate.",
		position = 4,
		closedByDefault = true
	)
	String OVERHEAD_CHAT_SECTION = "overheadChat";

	@ConfigSection(
		name = "Head icons",
		description = "Current-state prayer, skull, NPC, and actor hint-arrow replacements. Native positioning may differ.",
		position = 5,
		closedByDefault = true
	)
	String HEAD_ICONS_SECTION = "headIcons";

	@ConfigItem(
		keyName = PRESET_KEY,
		name = "Preset",
		description = "Copies the My hits, priority, zero-hit, layout, font, scale, radius, spacing, and fade settings below. Editing one of those settings changes this to Custom.",
		section = DISPLAY_SECTION,
		position = 0
	)
	default CustomizeALotPreset preset()
	{
		return CustomizeALotPreset.DEFAULT;
	}

	@ConfigItem(
		keyName = DISABLE_ENEMY_HITSPLATS_KEY,
		name = "Hide NPC hits",
		description = "Do not draw replacement hitsplats for NPCs. Native actor hitsplats are hidden while the plugin is enabled.",
		section = DISPLAY_SECTION,
		position = 16
	)
	default boolean disableEnemyHitsplats()
	{
		return false;
	}

	@ConfigItem(
		keyName = DISABLE_ALLY_HITSPLATS_KEY,
		name = "Hide other players' hits",
		description = "Do not draw replacement hitsplats for other players. Native actor hitsplats are hidden while the plugin is enabled.",
		section = DISPLAY_SECTION,
		position = 17
	)
	default boolean disableAllyHitsplats()
	{
		return false;
	}

	@ConfigItem(
		keyName = DISABLE_MY_HITSPLATS_KEY,
		name = "Hide hits on my player",
		description = "Do not draw replacement hitsplats on your player. Native actor hitsplats are hidden while the plugin is enabled.",
		section = DISPLAY_SECTION,
		position = 18
	)
	default boolean disableMyHitsplats()
	{
		return false;
	}

	@ConfigItem(
		keyName = MAX_HITSPLATS_KEY,
		name = "Max hitsplats",
		description = "Maximum replacement hitsplats kept per actor. Set to 0 for no cap.",
		section = DISPLAY_SECTION,
		position = 19
	)
	@Range(
		min = 0,
		max = 512
	)
	default int maxHitsplats()
	{
		return 0;
	}

	@ConfigItem(
		keyName = REUSE_OLD_HITSPLAT_SLOTS_KEY,
		name = "Reuse old slots",
		description = "Allow each new hitsplat to replace the oldest hitsplat from an earlier reuse window, even when an empty slot exists. Hits in the same window still use separate slots.",
		section = DISPLAY_SECTION,
		position = 20
	)
	default boolean reuseOldHitsplatSlots()
	{
		return false;
	}

	@ConfigItem(
		keyName = HITSPLAT_REUSE_INTERVAL_KEY,
		name = "Reuse interval",
		description = "Minimum age before a hitsplat slot can be reused. 550 ms is approximately one server tick with tolerance for timing differences.",
		section = DISPLAY_SECTION,
		position = 21
	)
	@Units(Units.MILLISECONDS)
	@Range(
		min = 50,
		max = 60000
	)
	default int hitsplatReuseInterval()
	{
		return 550;
	}

	@ConfigItem(
		keyName = OPACITY_PERCENT_KEY,
		name = "Opacity",
		description = "Opacity of replacement hitsplats. 100 is fully opaque.",
		section = DISPLAY_SECTION,
		position = 22
	)
	@Range(
		min = 0,
		max = 100
	)
	@Units(Units.PERCENT)
	default int opacityPercent()
	{
		return 100;
	}

	@ConfigItem(
		keyName = ONLY_DISPLAY_MINE_KEY,
		name = "My hits only",
		description = "Only draw hits marked as yours by the game. Ownership is never inferred from XP.",
		section = DISPLAY_SECTION,
		position = 1
	)
	default boolean onlyDisplayMine()
	{
		return CustomizeALotPreset.DEFAULT.isOnlyDisplayMine();
	}

	@ConfigItem(
		keyName = PRIORITIZE_MINE_KEY,
		name = "Prioritize my hits",
		description = "When replacement hitsplats are limited, a new hit marked as yours replaces the oldest hit not marked as yours first.",
		section = DISPLAY_SECTION,
		position = 2
	)
	default boolean prioritizeMine()
	{
		return CustomizeALotPreset.DEFAULT.isPrioritizeMine();
	}

	@ConfigItem(
		keyName = HIDE_ZERO_HITSPLATS_KEY,
		name = "Hide zero hits",
		description = "Do not draw misses or other zero-amount replacement hitsplats.",
		section = DISPLAY_SECTION,
		position = 3
	)
	default boolean hideZeroHitsplats()
	{
		return CustomizeALotPreset.DEFAULT.isHideZeroHitsplats();
	}

	@ConfigItem(
		keyName = LAYOUT_SHAPE_KEY,
		name = "Shape",
		description = "Hexagonal uses six-sided rings. Diamond uses four-corner rings. Grid uses square rings. X uses diagonal arms.",
		section = DISPLAY_SECTION,
		position = 4
	)
	default CustomizeALotLayoutShape layoutShape()
	{
		return CustomizeALotPreset.DEFAULT.getLayoutShape();
	}

	@ConfigItem(
		keyName = LAYOUT_DIRECTION_KEY,
		name = "Direction",
		description = "Clockwise starts at top and moves right. Counterclockwise starts at top and moves left.",
		section = DISPLAY_SECTION,
		position = 5
	)
	default CustomizeALotLayoutDirection layoutDirection()
	{
		return CustomizeALotPreset.DEFAULT.getLayoutDirection();
	}

	@ConfigItem(
		keyName = LAYOUT_BEHAVIOR_KEY,
		name = "Behavior",
		description = "Incremental fills slots in order. Symmetrical alternates opposite slots. Random chooses an open slot from the current radius.",
		section = DISPLAY_SECTION,
		position = 6
	)
	default CustomizeALotLayoutBehavior layoutBehavior()
	{
		return CustomizeALotPreset.DEFAULT.getLayoutBehavior();
	}

	@ConfigItem(
		keyName = MIN_RADIUS_KEY,
		name = "Min radius",
		description = "First layout radius for detected boss-like targets. Smaller targets always start at the center. 0 starts at the center; 1 starts on the first ring.",
		section = DISPLAY_SECTION,
		position = 9
	)
	@Range(
		min = 0,
		max = 64
	)
	default int minRadius()
	{
		return CustomizeALotPreset.DEFAULT.getMinRadius();
	}

	@ConfigItem(
		keyName = MAX_RADIUS_KEY,
		name = "Max radius",
		description = "Maximum layout radius for detected boss-like targets. Smaller targets are uncapped. 0 means no cap; 1 means center only; 2 means center plus the first ring.",
		section = DISPLAY_SECTION,
		position = 10
	)
	@Range(
		min = 0,
		max = 64
	)
	default int maxRadius()
	{
		return CustomizeALotPreset.DEFAULT.getMaxRadius();
	}

	@ConfigItem(
		keyName = X_SPACING_KEY,
		name = "X spacing",
		description = "Extra horizontal pixels between adjacent hitsplat slots. Negative values pull slots closer together.",
		section = DISPLAY_SECTION,
		position = 11
	)
	@Range(
		min = -64,
		max = 64
	)
	@Units(Units.PIXELS)
	default int xSpacing()
	{
		return CustomizeALotPreset.DEFAULT.getXSpacing();
	}

	@ConfigItem(
		keyName = Y_SPACING_KEY,
		name = "Y spacing",
		description = "Extra vertical pixels between adjacent hitsplat slots. Negative values pull slots closer together.",
		section = DISPLAY_SECTION,
		position = 12
	)
	@Range(
		min = -64,
		max = 64
	)
	@Units(Units.PIXELS)
	default int ySpacing()
	{
		return CustomizeALotPreset.DEFAULT.getYSpacing();
	}

	@ConfigItem(
		keyName = HITSPLAT_FONT_KEY,
		name = "Font",
		description = "Font used for hitsplat amounts. The font picker includes RuneLite, custom, and system fonts.",
		section = DISPLAY_SECTION,
		position = 7
	)
	default FontType hitsplatFont()
	{
		return FontType.SMALL;
	}

	@ConfigItem(
		keyName = HITSPLAT_SCALE_PERCENT_KEY,
		name = "Scale",
		description = "Scale the complete replacement hitsplat, including sprites, text, spacing, and layout geometry.",
		section = DISPLAY_SECTION,
		position = 8
	)
	@Units(Units.PERCENT)
	@Range(
		min = 50,
		max = 200
	)
	default int hitsplatScalePercent()
	{
		return CustomizeALotPreset.DEFAULT.getHitsplatScalePercent();
	}

	@ConfigItem(
		keyName = LARGE_TARGET_DETECTION_KEY,
		name = "Boss-like detection",
		description = "Decide which actors receive configured radius limits and target offsets. RuneLite does not expose a reliable boss flag, so Either combines footprint and public health scale.",
		section = DISPLAY_SECTION,
		position = 23
	)
	default CustomizeALotTargetDetection largeTargetDetection()
	{
		return CustomizeALotTargetDetection.EITHER;
	}

	@ConfigItem(
		keyName = LARGE_TARGET_SIZE_KEY,
		name = "Footprint threshold",
		description = "Minimum actor footprint used by boss-like detection. 2 means targets at least 2x2 tiles.",
		section = DISPLAY_SECTION,
		position = 24
	)
	@Range(
		min = 1,
		max = 16
	)
	default int largeTargetSize()
	{
		return 2;
	}

	@ConfigItem(
		keyName = LARGE_TARGET_HEALTH_SCALE_KEY,
		name = "Boss health-scale threshold",
		description = "Minimum public actor health scale used by boss-like detection. This is not actual maximum HP; standard actors commonly use 30.",
		section = DISPLAY_SECTION,
		position = 25
	)
	@Range(
		min = 100,
		max = 255
	)
	default int largeTargetHealthScale()
	{
		return 100;
	}

	@ConfigItem(
		keyName = LARGE_TARGET_X_OFFSET_KEY,
		name = "Boss-like X offset",
		description = "Extra horizontal hitsplat offset for detected boss-like targets.",
		section = DISPLAY_SECTION,
		position = 26
	)
	@Range(
		min = -256,
		max = 256
	)
	@Units(Units.PIXELS)
	default int largeTargetXOffset()
	{
		return 0;
	}

	@ConfigItem(
		keyName = LARGE_TARGET_Y_OFFSET_KEY,
		name = "Boss-like Y offset",
		description = "Extra vertical hitsplat offset for detected boss-like targets. Positive values move hitsplats upward.",
		section = DISPLAY_SECTION,
		position = 27
	)
	@Range(
		min = -256,
		max = 256
	)
	@Units(Units.PIXELS)
	default int largeTargetYOffset()
	{
		return 0;
	}

	@ConfigItem(
		keyName = FADE_IN_DURATION_KEY,
		name = "Fade-in duration",
		description = "How long replacement hitsplats take to appear. Total lifetime is fade-in + full opacity + fade-out.",
		section = DISPLAY_SECTION,
		position = 13
	)
	@Units(Units.MILLISECONDS)
	@Range(
		min = 0,
		max = 60000
	)
	default int fadeInDuration()
	{
		return CustomizeALotPreset.DEFAULT.getFadeInDuration();
	}

	@ConfigItem(
		keyName = FULL_OPACITY_DURATION_KEY,
		name = "Full opacity duration",
		description = "How long replacement hitsplats stay fully visible after fading in.",
		section = DISPLAY_SECTION,
		position = 14
	)
	@Units(Units.MILLISECONDS)
	@Range(
		min = 0,
		max = 60000
	)
	default int fullOpacityDuration()
	{
		return CustomizeALotPreset.DEFAULT.getFullOpacityDuration();
	}

	@ConfigItem(
		keyName = FADE_OUT_DURATION_KEY,
		name = "Fade-out duration",
		description = "How long replacement hitsplats take to disappear after the full-opacity duration.",
		section = DISPLAY_SECTION,
		position = 15
	)
	@Units(Units.MILLISECONDS)
	@Range(
		min = 0,
		max = 60000
	)
	default int fadeOutDuration()
	{
		return CustomizeALotPreset.DEFAULT.getFadeOutDuration();
	}

	@ConfigItem(
		keyName = SPRITE_SCALING_MODE_KEY,
		name = "Sprite scaling",
		description = "Resize hitsplats, RuneScape health bars, and head icons with Nearest, Bilinear, Bicubic, or edge-directed xBR. xBR is used for enlarging sprites and smoothly reduced to fractional target sizes.",
		section = GENERAL_SECTION,
		position = 0
	)
	default CustomizeALotSpriteScalingMode spriteScalingMode()
	{
		return CustomizeALotSpriteScalingMode.XBR;
	}

	@ConfigItem(
		keyName = HEALTH_BARS_ENABLED_KEY,
		name = "Show health bars",
		description = "Draw replacement health bars. Native health bars are hidden while the plugin is enabled.",
		section = HEALTH_BARS_SECTION,
		position = -1
	)
	default boolean healthBarsEnabled()
	{
		return true;
	}

	@ConfigItem(
		keyName = HEALTH_BAR_PRESET_KEY,
		name = "Preset",
		description = "Selects the renderer and copies its health-bar values once. RuneScape recreates the game's sprite bar; Custom uses the configurable values below. Editing a copied value changes this section to Custom.",
		section = HEALTH_BARS_SECTION,
		position = 0
	)
	default CustomizeALotHealthBarPreset healthBarPreset()
	{
		return CustomizeALotHealthBarPreset.DEFAULT;
	}

	@ConfigItem(
		keyName = HEALTH_BAR_STYLE_KEY,
		name = "Legacy style",
		description = "Compatibility value retained for saved configurations from before the health-bar preset selected the renderer.",
		section = HEALTH_BARS_SECTION,
		position = 1,
		hidden = true
	)
	default CustomizeALotHealthBarStyle healthBarStyle()
	{
		return CustomizeALotHealthBarStyle.SOLID;
	}

	@ConfigItem(
		keyName = HEALTH_BAR_SCALE_MODE_KEY,
		name = "Scale mode",
		description = "Controls boss sizing. Fixed uses Scale for both dimensions; Threshold switches to the boss width and height scales at the configured threshold; Dynamic interpolates each dimension independently. Public health scale is not actual maximum HP.",
		section = HEALTH_BARS_SECTION,
		position = 2
	)
	default CustomizeALotHealthScaleMode healthBarScaleMode()
	{
		return CustomizeALotHealthScaleMode.THRESHOLD;
	}

	@ConfigItem(
		keyName = HEALTH_BAR_SCALE_PERCENT_KEY,
		name = "Scale",
		description = "Fixed health-bar size multiplier, or the starting multiplier used by Threshold and Dynamic modes.",
		section = HEALTH_BARS_SECTION,
		position = 3
	)
	@Units(Units.PERCENT)
	@Range(
		min = 50,
		max = 200
	)
	default int healthBarScalePercent()
	{
		return 100;
	}

	@ConfigItem(
		keyName = HEALTH_BAR_SCALE_THRESHOLD_KEY,
		name = "Boss scale threshold",
		description = "Public actor health scale that activates the boss width and height scales in Threshold mode or reaches them in Dynamic mode. This is not actual maximum HP.",
		section = HEALTH_BARS_SECTION,
		position = 4
	)
	@Range(
		min = 100,
		max = 255
	)
	default int healthBarScaleThreshold()
	{
		return 100;
	}

	@ConfigItem(
		keyName = HEALTH_BAR_LARGE_SCALE_PERCENT_KEY,
		name = "Boss width scale",
		description = "Health-bar width multiplier used at or above the configured public health scale threshold.",
		section = HEALTH_BARS_SECTION,
		position = 5
	)
	@Units(Units.PERCENT)
	@Range(
		min = 50,
		max = 200
	)
	default int healthBarLargeScalePercent()
	{
		return 150;
	}

	@ConfigItem(
		keyName = HEALTH_BAR_LARGE_HEIGHT_SCALE_PERCENT_KEY,
		name = "Boss height scale",
		description = "Health-bar height multiplier used at or above the configured public health scale threshold.",
		section = HEALTH_BARS_SECTION,
		position = 6
	)
	@Units(Units.PERCENT)
	@Range(
		min = 50,
		max = 200
	)
	default int healthBarLargeHeightScalePercent()
	{
		return 100;
	}

	@ConfigItem(
		keyName = HEALTH_BAR_SOLID_WIDTH_KEY,
		name = "Width",
		description = "Width of the custom health bar in pixels. Adjacent spinner changes advance by 1 pixel while the plugin is enabled.",
		section = HEALTH_BARS_SECTION,
		position = 7
	)
	@Range(
		min = 10,
		max = 200
	)
	default double healthBarSolidWidth()
	{
		return 50.0;
	}

	@ConfigItem(
		keyName = HEALTH_BAR_HEIGHT_KEY,
		name = "Height",
		description = "Health-bar height in pixels. Adjacent spinner changes advance by 1 pixel while the plugin is enabled; RuneScape sprite bars use the nearest whole pixel.",
		section = HEALTH_BARS_SECTION,
		position = 8
	)
	@Range(
		min = 2,
		max = 20
	)
	default double healthBarHeight()
	{
		return 5.0;
	}

	@ConfigItem(
		keyName = HEALTH_BAR_X_OFFSET_KEY,
		name = "X offset",
		description = "Horizontal health-bar offset in pixels. Adjacent spinner changes advance by 2 pixels while the plugin is enabled.",
		section = HEALTH_BARS_SECTION,
		position = 9
	)
	@Range(
		min = -256,
		max = 256
	)
	@Units(Units.PIXELS)
	default int healthBarXOffset()
	{
		return 0;
	}

	@ConfigItem(
		keyName = HEALTH_BAR_Y_OFFSET_KEY,
		name = "Y offset",
		description = "Vertical health-bar offset in pixels. Adjacent spinner changes advance by 2 pixels while the plugin is enabled. Positive values move it upward.",
		section = HEALTH_BARS_SECTION,
		position = 10
	)
	@Range(
		min = -256,
		max = 256
	)
	@Units(Units.PIXELS)
	default int healthBarYOffset()
	{
		return 2;
	}

	@ConfigItem(
		keyName = HEALTH_BAR_FILL_DIRECTION_KEY,
		name = "Fill direction",
		description = "Direction in which the filled portion of a custom health bar grows.",
		section = HEALTH_BARS_SECTION,
		position = 11
	)
	default CustomizeALotHealthBarFillDirection healthBarFillDirection()
	{
		return CustomizeALotHealthBarFillDirection.LEFT_TO_RIGHT;
	}

	@ConfigItem(
		keyName = HEALTH_BAR_GRADIENT_COORDINATES_KEY,
		name = "Legacy gradient coordinates",
		description = "Compatibility value used by saved configurations from before front and back gradient coordinates were separated.",
		section = HEALTH_BARS_SECTION,
		position = 12,
		hidden = true
	)
	default CustomizeALotHealthBarGradientCoordinates healthBarGradientCoordinates()
	{
		return CustomizeALotHealthBarGradientCoordinates.FULL_BAR;
	}

	@ConfigItem(
		keyName = HEALTH_BAR_FRONT_GRADIENT_COORDINATES_KEY,
		name = "Front coordinates",
		description = "Relative restarts the front horizontal or vertical blend inside filled health. Absolute anchors it to the full bar so its colors stay fixed as health changes.",
		section = HEALTH_BARS_SECTION,
		position = 12
	)
	default CustomizeALotHealthBarGradientCoordinates healthBarFrontGradientCoordinates()
	{
		return healthBarGradientCoordinates();
	}

	@ConfigItem(
		keyName = HEALTH_BAR_FRONT_GRADIENT_KEY,
		name = "Front gradient",
		description = "Solid uses Front color. Horizontal and Vertical blend toward Front second color. Health based blends from the second color at empty health to the primary color at full health.",
		section = HEALTH_BARS_SECTION,
		position = 13
	)
	default CustomizeALotHealthBarGradient healthBarFrontGradient()
	{
		return CustomizeALotHealthBarGradient.HORIZONTAL;
	}

	@ConfigItem(
		keyName = HEALTH_BAR_FRONT_COLOR_KEY,
		name = "Front color",
		description = "Primary RGBA color for the filled portion of a custom health bar.",
		section = HEALTH_BARS_SECTION,
		position = 14
	)
	@Alpha
	default Color healthBarFrontColor()
	{
		return new Color(0xFF34F434, true);
	}

	@ConfigItem(
		keyName = HEALTH_BAR_FRONT_SECONDARY_COLOR_KEY,
		name = "Front second color",
		description = "Secondary RGBA color used by non-solid front gradients.",
		section = HEALTH_BARS_SECTION,
		position = 15
	)
	@Alpha
	default Color healthBarFrontSecondaryColor()
	{
		return new Color(0xFF18E418, true);
	}

	@ConfigItem(
		keyName = HEALTH_BAR_POISONED_FRONT_COLOR_KEY,
		name = "Poisoned front color",
		description = "Primary RGBA front color used for your local player's Custom health bar while poisoned or envenomed.",
		section = HEALTH_BARS_SECTION,
		position = 16
	)
	@Alpha
	default Color healthBarPoisonedFrontColor()
	{
		return new Color(132, 204, 66);
	}

	@ConfigItem(
		keyName = HEALTH_BAR_BACK_GRADIENT_COORDINATES_KEY,
		name = "Back coordinates",
		description = "Relative restarts the back horizontal or vertical blend inside missing health. Absolute anchors it to the full bar so its colors stay fixed as health changes.",
		section = HEALTH_BARS_SECTION,
		position = 17
	)
	default CustomizeALotHealthBarGradientCoordinates healthBarBackGradientCoordinates()
	{
		return healthBarGradientCoordinates();
	}

	@ConfigItem(
		keyName = HEALTH_BAR_BACK_GRADIENT_KEY,
		name = "Back gradient",
		description = "Solid uses Back color. Horizontal and Vertical blend toward Back second color. Health based blends from the second color at empty health to the primary color at full health.",
		section = HEALTH_BARS_SECTION,
		position = 18
	)
	default CustomizeALotHealthBarGradient healthBarBackGradient()
	{
		return CustomizeALotHealthBarGradient.HORIZONTAL;
	}

	@ConfigItem(
		keyName = HEALTH_BAR_BACK_COLOR_KEY,
		name = "Back color",
		description = "Primary RGBA color for the missing-health portion of a custom health bar.",
		section = HEALTH_BARS_SECTION,
		position = 19
	)
	@Alpha
	default Color healthBarBackColor()
	{
		return new Color(0xFFC01D1D, true);
	}

	@ConfigItem(
		keyName = HEALTH_BAR_BACK_SECONDARY_COLOR_KEY,
		name = "Back second color",
		description = "Secondary RGBA color used by non-solid back gradients.",
		section = HEALTH_BARS_SECTION,
		position = 20
	)
	@Alpha
	default Color healthBarBackSecondaryColor()
	{
		return new Color(0xFF901818, true);
	}

	@ConfigItem(
		keyName = HEALTH_BAR_DAMAGE_TRAIL_ENABLED_KEY,
		name = "Damage trail",
		description = "Hold recently lost health, then animate the trail down to the current Custom health-bar value. Healing updates immediately.",
		section = HEALTH_BARS_SECTION,
		position = 21
	)
	default boolean healthBarDamageTrailEnabled()
	{
		return true;
	}

	@ConfigItem(
		keyName = HEALTH_BAR_DAMAGE_TRAIL_COLOR_KEY,
		name = "Damage trail color",
		description = "RGBA color for recently lost health in a Custom health bar.",
		section = HEALTH_BARS_SECTION,
		position = 22
	)
	@Alpha
	default Color healthBarDamageTrailColor()
	{
		return new Color(0xFFFF001F, true);
	}

	@ConfigItem(
		keyName = HEALTH_BAR_DAMAGE_TRAIL_HOLD_KEY,
		name = "Damage trail hold",
		description = "How long recently lost health remains stationary before draining.",
		section = HEALTH_BARS_SECTION,
		position = 23
	)
	@Units(Units.MILLISECONDS)
	@Range(
		min = 0,
		max = 5000
	)
	default int healthBarDamageTrailHold()
	{
		return 445;
	}

	@ConfigItem(
		keyName = HEALTH_BAR_DAMAGE_TRAIL_DRAIN_KEY,
		name = "Damage trail drain",
		description = "How long the held damage trail takes to animate down to current health.",
		section = HEALTH_BARS_SECTION,
		position = 24
	)
	@Units(Units.MILLISECONDS)
	@Range(
		min = 0,
		max = 5000
	)
	default int healthBarDamageTrailDrain()
	{
		return 245;
	}

	@ConfigItem(
		keyName = HEALTH_BAR_SEGMENTS_ENABLED_KEY,
		name = "HP segments",
		description = "Draw League-style dividers every configured hitpoints. Exact values are available for your player and NPCs covered by RuneLite's NPC stats; the selected mode controls unknown actors.",
		section = HEALTH_BARS_SECTION,
		position = 25
	)
	default boolean healthBarSegmentsEnabled()
	{
		return true;
	}

	@ConfigItem(
		keyName = HEALTH_BAR_PUBLIC_UNITS_PER_SEGMENT_KEY,
		name = "Legacy public units per segment",
		description = "Compatibility value used by saved configurations from before HP-based segments.",
		section = HEALTH_BARS_SECTION,
		position = 26,
		hidden = true
	)
	@Range(
		min = 1,
		max = 255
	)
	default int healthBarPublicUnitsPerSegment()
	{
		return 10;
	}

	@ConfigItem(
		keyName = HEALTH_BAR_SEGMENT_VALUE_MODE_KEY,
		name = "Segment HP source",
		description = "Exact HP uses your real Hitpoints level or RuneLite NPC stats. Public fallback keeps segments visible for actors whose maximum HP is unknown, but those fallback units are normalized public health-scale units rather than hitpoints.",
		section = HEALTH_BARS_SECTION,
		position = 26
	)
	default CustomizeALotHealthBarSegmentValueMode healthBarSegmentValueMode()
	{
		return CustomizeALotHealthBarSegmentValueMode.EXACT_HP_WITH_PUBLIC_FALLBACK;
	}

	@ConfigItem(
		keyName = HEALTH_BAR_HITPOINTS_PER_SEGMENT_KEY,
		name = "HP per segment",
		description = "Draw one divider for each multiple of this many hitpoints. In Public scale mode or when using its fallback, the same number is measured in normalized public health-scale units.",
		section = HEALTH_BARS_SECTION,
		position = 27
	)
	@Range(
		min = 1,
		max = 10000
	)
	default int healthBarHitpointsPerSegment()
	{
		return healthBarPublicUnitsPerSegment();
	}

	@ConfigItem(
		keyName = HEALTH_BAR_SEGMENT_COLOR_KEY,
		name = "Segment color",
		description = "RGBA color for health-segment dividers.",
		section = HEALTH_BARS_SECTION,
		position = 28
	)
	@Alpha
	default Color healthBarSegmentColor()
	{
		return new Color(0x3F000000, true);
	}

	@ConfigItem(
		keyName = HEALTH_BAR_SEGMENT_THICKNESS_KEY,
		name = "Segment thickness",
		description = "Thickness of Custom health-bar segment dividers in pixels. Decimal values allow sub-pixel lines.",
		section = HEALTH_BARS_SECTION,
		position = 29
	)
	@Range(
		min = 0,
		max = 10
	)
	default double healthBarSegmentThickness()
	{
		return 0.6;
	}

	@ConfigItem(
		keyName = HEALTH_BAR_BORDER_COLOR_KEY,
		name = "Border color",
		description = "RGBA color for the border around a custom health bar.",
		section = HEALTH_BARS_SECTION,
		position = 30
	)
	@Alpha
	default Color healthBarBorderColor()
	{
		return new Color(25, 25, 25, 230);
	}

	@ConfigItem(
		keyName = HEALTH_BAR_BORDER_THICKNESS_KEY,
		name = "Border thickness",
		description = "Custom health-bar border thickness in pixels. Decimal values allow sub-pixel borders; set to 0 for no border.",
		section = HEALTH_BARS_SECTION,
		position = 31
	)
	@Range(
		min = 0,
		max = 10
	)
	default double healthBarBorderThickness()
	{
		return 0.1;
	}

	@ConfigItem(
		keyName = HEALTH_BAR_CORNER_RADIUS_KEY,
		name = "Corner radius",
		description = "Antialiased Custom health-bar corner radius in pixels, limited by half the bar's smaller dimension. Decimal values allow sub-pixel curves.",
		section = HEALTH_BARS_SECTION,
		position = 32
	)
	@Range(
		min = 0,
		max = 50
	)
	default double healthBarCornerRadius()
	{
		return 0.5;
	}

	@ConfigItem(
		keyName = OVERHEAD_CHAT_ENABLED_KEY,
		name = "Show overhead chat",
		description = "Draw replacement overhead chat. Native actor UI, including overhead chat, is hidden while the plugin is enabled.",
		warning = "Matching color/effect prefixes typed in this client, including rainbow and pattern, are reconstructed locally. Other senders' native colors/effects cannot be inherited; remote appearance, native filtering, and collision behavior are approximate.",
		section = OVERHEAD_CHAT_SECTION,
		position = -1
	)
	default boolean overheadChatEnabled()
	{
		return true;
	}

	@ConfigItem(
		keyName = OVERHEAD_CHAT_PRESET_KEY,
		name = "Preset",
		description = "Copies the overhead-chat appearance and NPC visibility settings below once. Editing a copied setting changes this section to Custom.",
		section = OVERHEAD_CHAT_SECTION,
		position = 0
	)
	default CustomizeALotOverheadChatPreset overheadChatPreset()
	{
		return CustomizeALotOverheadChatPreset.DEFAULT;
	}

	@ConfigItem(
		keyName = SHOW_NPC_OVERHEAD_CHAT_KEY,
		name = "NPC overhead text",
		description = "Draw replacement overhead text for NPCs. Player overhead chat remains controlled by Show overhead chat.",
		section = OVERHEAD_CHAT_SECTION,
		position = 1
	)
	default boolean showNpcOverheadChat()
	{
		return true;
	}

	@ConfigItem(
		keyName = OVERHEAD_CHAT_FONT_KEY,
		name = "Font",
		description = "Font used for overhead chat. The font picker includes RuneLite, custom, and system fonts.",
		section = OVERHEAD_CHAT_SECTION,
		position = 2
	)
	default FontType overheadChatFont()
	{
		return FontType.BOLD;
	}

	@ConfigItem(
		keyName = OVERHEAD_CHAT_COLOR_KEY,
		name = "Color",
		description = "Fallback RGBA color for replacement overhead chat. Recognized local color prefixes override RGB per message or glyph while retaining this alpha.",
		section = OVERHEAD_CHAT_SECTION,
		position = 3
	)
	@Alpha
	default Color overheadChatColor()
	{
		return new Color(0xFF, 0xFF, 0x3F, 0xFF);
	}

	@ConfigItem(
		keyName = OVERHEAD_CHAT_RELATIONSHIP_COLORS_KEY,
		name = "Relationship colors",
		description = "Use separate default overhead-chat colors for group iron teammates, friends, and clan members. Your own typed color prefixes still take priority.",
		section = OVERHEAD_CHAT_SECTION,
		position = 4
	)
	default boolean overheadChatRelationshipColors()
	{
		return true;
	}

	@ConfigItem(
		keyName = OVERHEAD_CHAT_FRIEND_COLOR_KEY,
		name = "Friend color",
		description = "Default RGBA overhead-chat color for players on your friends list when relationship colors are enabled.",
		section = OVERHEAD_CHAT_SECTION,
		position = 5
	)
	@Alpha
	default Color overheadChatFriendColor()
	{
		return new Color(0xFFA5FF40, true);
	}

	@ConfigItem(
		keyName = OVERHEAD_CHAT_CLAN_COLOR_KEY,
		name = "Clan color",
		description = "Default RGBA overhead-chat color for members of your clan when relationship colors are enabled.",
		section = OVERHEAD_CHAT_SECTION,
		position = 6
	)
	@Alpha
	default Color overheadChatClanColor()
	{
		return new Color(0xFF40CFFF, true);
	}

	@ConfigItem(
		keyName = OVERHEAD_CHAT_GROUP_IRON_COLOR_KEY,
		name = "Group iron color",
		description = "Default RGBA overhead-chat color for members of your group iron team when relationship colors are enabled.",
		section = OVERHEAD_CHAT_SECTION,
		position = 7
	)
	@Alpha
	default Color overheadChatGroupIronColor()
	{
		return new Color(0xFFFF4040, true);
	}

	@ConfigItem(
		keyName = OVERHEAD_CHAT_EFFECT_KEY,
		name = "Default Text Effect",
		description = "Fallback animation for replacement overhead chat. Static draws unanimated text. A matching effect prefix typed by you overrides it for that local message; other senders' native effects cannot be inherited.",
		section = OVERHEAD_CHAT_SECTION,
		position = 8
	)
	default CustomizeALotOverheadChatEffect overheadChatEffect()
	{
		return CustomizeALotOverheadChatEffect.STATIC;
	}

	@ConfigItem(
		keyName = OVERHEAD_CHAT_SHADOW_KEY,
		name = "Text shadow",
		description = "Draw a configurable shadow behind replacement overhead chat.",
		section = OVERHEAD_CHAT_SECTION,
		position = 9
	)
	default boolean overheadChatShadow()
	{
		return true;
	}

	@ConfigItem(
		keyName = OVERHEAD_CHAT_SHADOW_COLOR_KEY,
		name = "Shadow color",
		description = "RGBA color used for the replacement overhead-chat shadow.",
		section = OVERHEAD_CHAT_SECTION,
		position = 10
	)
	@Alpha
	default Color overheadChatShadowColor()
	{
		return new Color(0x80171717, true);
	}

	@ConfigItem(
		keyName = OVERHEAD_CHAT_X_OFFSET_KEY,
		name = "X offset",
		description = "Horizontal overhead-chat offset in whole pixels.",
		section = OVERHEAD_CHAT_SECTION,
		position = 11
	)
	@Range(
		min = -256,
		max = 256
	)
	@Units(Units.PIXELS)
	default int overheadChatXOffset()
	{
		return 0;
	}

	@ConfigItem(
		keyName = OVERHEAD_CHAT_Y_OFFSET_KEY,
		name = "Y offset",
		description = "Vertical overhead-chat offset in whole pixels. Positive values move it upward.",
		section = OVERHEAD_CHAT_SECTION,
		position = 12
	)
	@Range(
		min = -256,
		max = 256
	)
	@Units(Units.PIXELS)
	default int overheadChatYOffset()
	{
		return 0;
	}

	@ConfigItem(
		keyName = HEAD_ICONS_ENABLED_KEY,
		name = "Show head icons",
		description = "Draw replacement head icons. Native head icons are hidden while the plugin is enabled.",
		section = HEAD_ICONS_SECTION,
		position = -1
	)
	default boolean headIconsEnabled()
	{
		return true;
	}

	@ConfigItem(
		keyName = HEAD_ICON_PRESET_KEY,
		name = "Preset",
		description = "Copies the head-icon visibility, scale, offsets, and spacing settings below once. Editing a copied setting changes this section to Custom.",
		section = HEAD_ICONS_SECTION,
		position = 0
	)
	default CustomizeALotHeadIconPreset headIconPreset()
	{
		return CustomizeALotHeadIconPreset.DEFAULT;
	}

	@ConfigItem(
		keyName = SHOW_PRAYER_ICONS_KEY,
		name = "Prayer icons",
		description = "Show player overhead-prayer icons.",
		section = HEAD_ICONS_SECTION,
		position = 1
	)
	default boolean showPrayerIcons()
	{
		return true;
	}

	@ConfigItem(
		keyName = SHOW_SKULL_ICONS_KEY,
		name = "Skull icons",
		description = "Show player skull icons.",
		section = HEAD_ICONS_SECTION,
		position = 2
	)
	default boolean showSkullIcons()
	{
		return true;
	}

	@ConfigItem(
		keyName = SHOW_NPC_ICONS_KEY,
		name = "NPC icons",
		description = "Show NPC overhead icons.",
		section = HEAD_ICONS_SECTION,
		position = 3
	)
	default boolean showNpcIcons()
	{
		return true;
	}

	@ConfigItem(
		keyName = SHOW_HINT_ARROWS_KEY,
		name = "Hint arrows",
		description = "Show actor hint arrows.",
		section = HEAD_ICONS_SECTION,
		position = 4
	)
	default boolean showHintArrows()
	{
		return true;
	}

	@ConfigItem(
		keyName = HEAD_ICON_SCALE_PERCENT_KEY,
		name = "Scale",
		description = "Head-icon scale as a percentage of normal size.",
		section = HEAD_ICONS_SECTION,
		position = 5
	)
	@Units(Units.PERCENT)
	@Range(
		min = 50,
		max = 200
	)
	default int headIconScalePercent()
	{
		return 100;
	}

	@ConfigItem(
		keyName = HEAD_ICON_X_OFFSET_KEY,
		name = "X offset",
		description = "Horizontal head-icon offset in pixels.",
		section = HEAD_ICONS_SECTION,
		position = 6
	)
	@Range(
		min = -256,
		max = 256
	)
	@Units(Units.PIXELS)
	default int headIconXOffset()
	{
		return 0;
	}

	@ConfigItem(
		keyName = HEAD_ICON_Y_OFFSET_KEY,
		name = "Y offset",
		description = "Vertical head-icon offset in pixels. Positive values move icons upward.",
		section = HEAD_ICONS_SECTION,
		position = 7
	)
	@Range(
		min = -256,
		max = 256
	)
	@Units(Units.PIXELS)
	default int headIconYOffset()
	{
		return 0;
	}

	@ConfigItem(
		keyName = HEAD_ICON_SPACING_KEY,
		name = "Spacing",
		description = "Gap below the first head icon and between each stacked icon, in pixels.",
		section = HEAD_ICONS_SECTION,
		position = 8
	)
	@Range(
		min = 0,
		max = 20
	)
	@Units(Units.PIXELS)
	default int headIconSpacing()
	{
		return 2;
	}
}
