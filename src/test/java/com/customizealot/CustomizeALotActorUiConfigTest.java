package com.customizealot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.awt.Color;
import java.awt.RenderingHints;
import net.runelite.client.config.FontType;
import org.junit.Test;

public class CustomizeALotActorUiConfigTest
{
	private final CustomizeALotConfig config = new CustomizeALotConfig()
	{
	};

	@Test
	public void healthBarDefaultsUseRuinedHeirStyleAndScale()
	{
		assertTrue(config.healthBarsEnabled());
		assertEquals(CustomizeALotHealthBarStyle.SOLID, config.healthBarStyle());
		assertEquals(CustomizeALotHealthScaleMode.THRESHOLD, config.healthBarScaleMode());
		assertEquals(100, config.healthBarScalePercent());
		assertEquals(100, config.healthBarScaleThreshold());
		assertEquals(150, config.healthBarLargeScalePercent());
		assertEquals(100, config.healthBarLargeHeightScalePercent());
		assertEquals(50.0, config.healthBarSolidWidth(), 0.0);
		assertEquals(5.0, config.healthBarHeight(), 0.0);
		assertEquals(0, config.healthBarXOffset());
		assertEquals(2, config.healthBarYOffset());
		assertEquals(
			CustomizeALotHealthBarFillDirection.LEFT_TO_RIGHT,
			config.healthBarFillDirection());
		assertEquals(
			CustomizeALotHealthBarGradientCoordinates.FULL_BAR,
			config.healthBarGradientCoordinates());
		assertEquals(
			CustomizeALotHealthBarGradientCoordinates.FULL_BAR,
			config.healthBarFrontGradientCoordinates());
		assertEquals(
			CustomizeALotHealthBarGradientCoordinates.FULL_BAR,
			config.healthBarBackGradientCoordinates());
		assertEquals(CustomizeALotHealthBarGradient.HORIZONTAL, config.healthBarFrontGradient());
		assertEquals(new Color(0xFF34F434, true), config.healthBarFrontColor());
		assertEquals(new Color(0xFF18E418, true), config.healthBarFrontSecondaryColor());
		assertEquals(new Color(132, 204, 66), config.healthBarPoisonedFrontColor());
		assertEquals(CustomizeALotHealthBarGradient.HORIZONTAL, config.healthBarBackGradient());
		assertEquals(new Color(0xFFC01D1D, true), config.healthBarBackColor());
		assertEquals(new Color(0xFF901818, true), config.healthBarBackSecondaryColor());
		assertTrue(config.healthBarDamageTrailEnabled());
		assertEquals(new Color(0xFFFF001F, true), config.healthBarDamageTrailColor());
		assertEquals(445, config.healthBarDamageTrailHold());
		assertEquals(245, config.healthBarDamageTrailDrain());
		assertTrue(config.healthBarSegmentsEnabled());
		assertEquals(10, config.healthBarPublicUnitsPerSegment());
		assertEquals(
			CustomizeALotHealthBarSegmentValueMode.EXACT_HP_WITH_PUBLIC_FALLBACK,
			config.healthBarSegmentValueMode());
		assertEquals(10, config.healthBarHitpointsPerSegment());
		assertEquals(new Color(0x3F000000, true), config.healthBarSegmentColor());
		assertEquals(0.6, config.healthBarSegmentThickness(), 0.0);
		assertEquals(new Color(25, 25, 25, 230), config.healthBarBorderColor());
		assertEquals(0.1, config.healthBarBorderThickness(), 0.0);
		assertEquals(0.5, config.healthBarCornerRadius(), 0.0);
		assertEquals(CustomizeALotSpriteScalingMode.XBR, config.spriteScalingMode());
	}

	@Test
	public void overheadChatUsesRuinedHeirColorsByDefault()
	{
		assertTrue(config.overheadChatEnabled());
		assertTrue(config.showNpcOverheadChat());
		assertSame(FontType.BOLD, config.overheadChatFont());
		assertEquals(new Color(0xFF, 0xFF, 0x3F, 0xFF), config.overheadChatColor());
		assertTrue(config.overheadChatRelationshipColors());
		assertEquals(new Color(0xFFA5FF40, true), config.overheadChatFriendColor());
		assertEquals(new Color(0xFF40CFFF, true), config.overheadChatClanColor());
		assertEquals(new Color(0xFFFF4040, true), config.overheadChatGroupIronColor());
		assertEquals(CustomizeALotOverheadChatEffect.STATIC, config.overheadChatEffect());
		assertTrue(config.overheadChatShadow());
		assertEquals(new Color(0x80171717, true), config.overheadChatShadowColor());
		assertEquals(0, config.overheadChatXOffset());
		assertEquals(0, config.overheadChatYOffset());
	}

	@Test
	public void headIconsAreEnabledByDefault()
	{
		assertTrue(config.headIconsEnabled());
		assertTrue(config.showPrayerIcons());
		assertTrue(config.showSkullIcons());
		assertTrue(config.showNpcIcons());
		assertTrue(config.showHintArrows());
		assertEquals(100, config.headIconScalePercent());
		assertEquals(0, config.headIconXOffset());
		assertEquals(0, config.headIconYOffset());
		assertEquals(2, config.headIconSpacing());
	}

	@Test
	public void healthBarStylesHaveReadableLabels()
	{
		assertEquals("RuneScape", CustomizeALotHealthBarStyle.NATIVE.toString());
		assertEquals("Custom", CustomizeALotHealthBarStyle.SOLID.toString());
	}

	@Test
	public void customHealthBarModesHaveReadableLabels()
	{
		assertEquals("Solid", CustomizeALotHealthBarGradient.SOLID.toString());
		assertEquals("Horizontal", CustomizeALotHealthBarGradient.HORIZONTAL.toString());
		assertEquals("Vertical", CustomizeALotHealthBarGradient.VERTICAL.toString());
		assertEquals("Health based", CustomizeALotHealthBarGradient.HEALTH_BASED.toString());
		assertEquals(
			"Relative to each segment",
			CustomizeALotHealthBarGradientCoordinates.SEGMENT.toString());
		assertEquals(
			"Absolute across full bar",
			CustomizeALotHealthBarGradientCoordinates.FULL_BAR.toString());
		assertEquals(
			"Exact HP; public fallback",
			CustomizeALotHealthBarSegmentValueMode.EXACT_HP_WITH_PUBLIC_FALLBACK.toString());
		assertEquals(
			"Exact HP only",
			CustomizeALotHealthBarSegmentValueMode.EXACT_HP_ONLY.toString());
		assertEquals(
			"Public scale units",
			CustomizeALotHealthBarSegmentValueMode.PUBLIC_SCALE.toString());
		assertEquals("Left to right", CustomizeALotHealthBarFillDirection.LEFT_TO_RIGHT.toString());
		assertEquals("Right to left", CustomizeALotHealthBarFillDirection.RIGHT_TO_LEFT.toString());
		assertEquals("Top to bottom", CustomizeALotHealthBarFillDirection.TOP_TO_BOTTOM.toString());
		assertEquals("Bottom to top", CustomizeALotHealthBarFillDirection.BOTTOM_TO_TOP.toString());
	}

	@Test
	public void legacyHealthValuesFeedTheNewSplitSettings()
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
				return 25;
			}
		};

		assertEquals(
			CustomizeALotHealthBarGradientCoordinates.FULL_BAR,
			legacy.healthBarFrontGradientCoordinates());
		assertEquals(
			CustomizeALotHealthBarGradientCoordinates.FULL_BAR,
			legacy.healthBarBackGradientCoordinates());
		assertEquals(25, legacy.healthBarHitpointsPerSegment());
	}

	@Test
	public void spriteScalingModesHaveReadableLabels()
	{
		assertEquals("Nearest", CustomizeALotSpriteScalingMode.NEAREST.toString());
		assertEquals("Bilinear", CustomizeALotSpriteScalingMode.BILINEAR.toString());
		assertEquals("Bicubic", CustomizeALotSpriteScalingMode.BICUBIC.toString());
		assertEquals("xBR", CustomizeALotSpriteScalingMode.XBR.toString());
		assertSame(
			RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR,
			CustomizeALotSpriteScalingMode.NEAREST.getInterpolationHint());
		assertSame(
			RenderingHints.VALUE_INTERPOLATION_BILINEAR,
			CustomizeALotSpriteScalingMode.BILINEAR.getInterpolationHint());
		assertSame(
			RenderingHints.VALUE_INTERPOLATION_BICUBIC,
			CustomizeALotSpriteScalingMode.BICUBIC.getInterpolationHint());
		assertSame(
			RenderingHints.VALUE_INTERPOLATION_BICUBIC,
			CustomizeALotSpriteScalingMode.XBR.getInterpolationHint());
	}

}
