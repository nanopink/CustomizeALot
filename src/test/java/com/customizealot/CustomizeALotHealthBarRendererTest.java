package com.customizealot;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import org.junit.Test;

public class CustomizeALotHealthBarRendererTest
{
	@Test
	public void healthPresetAloneSelectsTheRenderer()
	{
		assertTrue(CustomizeALotHealthBarRenderer.usesRuneScapeSprites(
			CustomizeALotHealthBarPreset.RUNESCAPE));
		assertTrue(CustomizeALotHealthBarRenderer.usesRuneScapeSprites(null));
		assertFalse(CustomizeALotHealthBarRenderer.usesRuneScapeSprites(
			CustomizeALotHealthBarPreset.CUSTOM));
		assertFalse(CustomizeALotHealthBarRenderer.usesRuneScapeSprites(
			CustomizeALotHealthBarPreset.RUINED_HEIR));
	}

	@Test
	public void projectsOnlyRenderableHealthStates()
	{
		assertTrue(CustomizeALotHealthBarRenderer.isRenderableHealthState(true, 0, 30));
		assertTrue(CustomizeALotHealthBarRenderer.isRenderableHealthState(true, 15, 30));
		assertFalse(CustomizeALotHealthBarRenderer.isRenderableHealthState(false, 15, 30));
		assertFalse(CustomizeALotHealthBarRenderer.isRenderableHealthState(true, -1, 30));
		assertFalse(CustomizeALotHealthBarRenderer.isRenderableHealthState(true, 15, 0));
		assertFalse(CustomizeALotHealthBarRenderer.isRenderableHealthState(true, 15, -1));
	}

	@Test
	public void computesClampedFillWidth()
	{
		assertEquals(0, CustomizeALotHealthBarRenderer.filledWidth(30, 0, 30));
		assertEquals(1, CustomizeALotHealthBarRenderer.filledWidth(30, 1, 100));
		assertEquals(15, CustomizeALotHealthBarRenderer.filledWidth(30, 15, 30));
		assertEquals(30, CustomizeALotHealthBarRenderer.filledWidth(30, 40, 30));
	}

	@Test
	public void scalesDimensionsWithinSupportedRange()
	{
		assertEquals(15, CustomizeALotHealthBarRenderer.scaled(30, 50));
		assertEquals(30, CustomizeALotHealthBarRenderer.scaled(30, 100));
		assertEquals(60, CustomizeALotHealthBarRenderer.scaled(30, 200));
		assertEquals(60, CustomizeALotHealthBarRenderer.scaled(30, 500));
		assertEquals(15.75, CustomizeALotHealthBarRenderer.scaledDimension(10.5, 150), 0.0);
	}

	@Test
	public void fractionalFillAndConfiguredDimensionClampsAreStable()
	{
		assertEquals(5.25, CustomizeALotHealthBarRenderer.filledExtent(10.5, 1, 2), 0.0);
		assertEquals(1.0, CustomizeALotHealthBarRenderer.filledExtent(10.5, 1, 100), 0.0);
		assertEquals(0.0, CustomizeALotHealthBarRenderer.filledExtent(10.5, 0, 100), 0.0);
		assertEquals(10.0, CustomizeALotHealthBarRenderer.clampedCustomWidth(0.0), 0.0);
		assertEquals(200.0, CustomizeALotHealthBarRenderer.clampedCustomWidth(999.0), 0.0);
		assertEquals(30.0, CustomizeALotHealthBarRenderer.clampedCustomWidth(Double.NaN), 0.0);
		assertEquals(2.0, CustomizeALotHealthBarRenderer.clampedHealthBarHeight(0.0), 0.0);
		assertEquals(20.0, CustomizeALotHealthBarRenderer.clampedHealthBarHeight(999.0), 0.0);
		assertEquals(5.0,
			CustomizeALotHealthBarRenderer.clampedHealthBarHeight(Double.POSITIVE_INFINITY), 0.0);
	}

	@Test
	public void fixedScalePreservesConfiguredPercentage()
	{
		assertEquals(
			100,
			CustomizeALotHealthBarRenderer.effectiveScalePercent(
				CustomizeALotHealthScaleMode.FIXED,
				100,
				150,
				100,
				160));
	}

	@Test
	public void thresholdScaleSwitchesAtPublicHealthScale()
	{
		assertEquals(
			100,
			CustomizeALotHealthBarRenderer.effectiveScalePercent(
				CustomizeALotHealthScaleMode.THRESHOLD,
				100,
				150,
				100,
				99));
		assertEquals(
			150,
			CustomizeALotHealthBarRenderer.effectiveScalePercent(
				CustomizeALotHealthScaleMode.THRESHOLD,
				100,
				150,
				100,
				100));

		assertEquals(
			100,
			CustomizeALotHealthBarRenderer.effectiveScalePercent(
				CustomizeALotHealthScaleMode.THRESHOLD,
				100,
				100,
				100,
				100));
	}

	@Test
	public void wholePixelBarWidthsUseCrispCenteredCoordinates()
	{
		assertEquals(75.0,
			CustomizeALotHealthBarRenderer.alignedCenteredCoordinate(100.0, 50.0),
			0.0);
		assertEquals(63.0,
			CustomizeALotHealthBarRenderer.alignedCenteredCoordinate(100.0, 75.0),
			0.0);
		assertEquals(74.75,
			CustomizeALotHealthBarRenderer.alignedCenteredCoordinate(100.0, 50.5),
			0.0);
	}

	@Test
	public void configuredOffsetsStayConstantAcrossProjectedActorPositions()
	{
		assertHealthBarOffset(210, 380);
		assertHealthBarOffset(470, 125);

		assertEquals(
			10.0,
			CustomizeALotHealthBarRenderer.barLeft(210, 30.5, 10)
				- CustomizeALotHealthBarRenderer.barLeft(210, 30.5, 0),
			0.0);
		assertEquals(
			-10.0,
			CustomizeALotHealthBarRenderer.barTop(380, 5.5, 10)
				- CustomizeALotHealthBarRenderer.barTop(380, 5.5, 0),
			0.0);
	}

	@Test
	public void thickSolidBordersDoNotEnterTheChatLane()
	{
		int chatLaneTop = 120;
		double height = 5.0;
		assertEquals(
			113.0,
			CustomizeALotHealthBarRenderer.solidBarTop(chatLaneTop, height, 0, 0.0),
			0.0);
		assertEquals(
			113.0,
			CustomizeALotHealthBarRenderer.solidBarTop(chatLaneTop, height, 0, 2.0),
			0.0);

		double barTop = CustomizeALotHealthBarRenderer.solidBarTop(
			chatLaneTop,
			height,
			0,
			10.0);
		assertEquals(105.0, barTop, 0.0);
		assertEquals(chatLaneTop, barTop + height + 10.0, 0.0);
	}

	@Test
	public void dynamicScaleInterpolatesAndClampsAtThreshold()
	{
		assertEquals(
			100,
			CustomizeALotHealthBarRenderer.effectiveScalePercent(
				CustomizeALotHealthScaleMode.DYNAMIC,
				100,
				150,
				100,
				30));
		assertEquals(
			125,
			CustomizeALotHealthBarRenderer.effectiveScalePercent(
				CustomizeALotHealthScaleMode.DYNAMIC,
				100,
				150,
				100,
				65));
		assertEquals(
			150,
			CustomizeALotHealthBarRenderer.effectiveScalePercent(
				CustomizeALotHealthScaleMode.DYNAMIC,
				100,
				150,
				100,
				200));
	}

	private static void assertHealthBarOffset(int anchorX, int anchorY)
	{
		assertEquals(
			10,
			CustomizeALotHealthBarRenderer.barLeft(anchorX, 30, 10)
				- CustomizeALotHealthBarRenderer.barLeft(anchorX, 30, 0));
		assertEquals(
			-10,
			CustomizeALotHealthBarRenderer.barTop(anchorY, 5, 10)
				- CustomizeALotHealthBarRenderer.barTop(anchorY, 5, 0));
		assertEquals(
			7,
			anchorY - CustomizeALotHealthBarRenderer.barTop(anchorY, 5, 0));
	}

	@Test
	public void malformedLowThresholdsAreClampedToTheBossMinimum()
	{
		assertEquals(
			100,
			CustomizeALotHealthBarRenderer.effectiveScalePercent(
				CustomizeALotHealthScaleMode.DYNAMIC,
				100,
				150,
				20,
				10));
		assertEquals(
			100,
			CustomizeALotHealthBarRenderer.effectiveScalePercent(
				CustomizeALotHealthScaleMode.DYNAMIC,
				100,
				150,
				20,
				20));
		assertEquals(
			150,
			CustomizeALotHealthBarRenderer.effectiveScalePercent(
				CustomizeALotHealthScaleMode.DYNAMIC,
				100,
				150,
				20,
				100));
	}

	@Test
	public void rgbaCustomBarSegmentsCompositeDirectlyOntoTheScene()
	{
		BufferedImage image = new BufferedImage(8, 6, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		Color front = new Color(255, 0, 0, 128);
		Color back = new Color(0, 0, 255, 96);
		Color border = new Color(0, 0, 0, 64);
		try
		{
			CustomizeALotHealthBarRenderer.drawCustomBar(
				graphics,
				2,
				2,
				4,
				2,
				2,
				front,
				back,
				border);
		}
		finally
		{
			graphics.dispose();
		}

		assertEquals(front.getRGB(), image.getRGB(2, 2));
		assertEquals(back.getRGB(), image.getRGB(4, 2));
		assertEquals(border.getRGB(), image.getRGB(1, 1));
	}

	@Test
	public void computesFrontAndBackBoundsForEveryFillDirection()
	{
		assertEquals(
			new Rectangle(10, 20, 3, 4),
			CustomizeALotHealthBarRenderer.frontBounds(
				10, 20, 8, 4, 3, CustomizeALotHealthBarFillDirection.LEFT_TO_RIGHT));
		assertEquals(
			new Rectangle(13, 20, 5, 4),
			CustomizeALotHealthBarRenderer.backBounds(
				10, 20, 8, 4, 3, CustomizeALotHealthBarFillDirection.LEFT_TO_RIGHT));
		assertEquals(
			new Rectangle(15, 20, 3, 4),
			CustomizeALotHealthBarRenderer.frontBounds(
				10, 20, 8, 4, 3, CustomizeALotHealthBarFillDirection.RIGHT_TO_LEFT));
		assertEquals(
			new Rectangle(10, 20, 5, 4),
			CustomizeALotHealthBarRenderer.backBounds(
				10, 20, 8, 4, 3, CustomizeALotHealthBarFillDirection.RIGHT_TO_LEFT));
		assertEquals(
			new Rectangle(10, 20, 8, 2),
			CustomizeALotHealthBarRenderer.frontBounds(
				10, 20, 8, 4, 2, CustomizeALotHealthBarFillDirection.TOP_TO_BOTTOM));
		assertEquals(
			new Rectangle(10, 22, 8, 2),
			CustomizeALotHealthBarRenderer.backBounds(
				10, 20, 8, 4, 2, CustomizeALotHealthBarFillDirection.TOP_TO_BOTTOM));
		assertEquals(
			new Rectangle(10, 22, 8, 2),
			CustomizeALotHealthBarRenderer.frontBounds(
				10, 20, 8, 4, 2, CustomizeALotHealthBarFillDirection.BOTTOM_TO_TOP));
		assertEquals(
			new Rectangle(10, 20, 8, 2),
			CustomizeALotHealthBarRenderer.backBounds(
				10, 20, 8, 4, 2, CustomizeALotHealthBarFillDirection.BOTTOM_TO_TOP));
	}

	@Test
	public void healthBasedGradientInterpolatesEveryRgbaChannel()
	{
		Color full = new Color(100, 200, 40, 240);
		Color empty = new Color(20, 40, 80, 40);
		assertEquals(empty, CustomizeALotHealthBarRenderer.healthBasedColor(full, empty, 0.0));
		assertEquals(new Color(60, 120, 60, 140),
			CustomizeALotHealthBarRenderer.healthBasedColor(full, empty, 0.5));
		assertEquals(full, CustomizeALotHealthBarRenderer.healthBasedColor(full, empty, 1.0));
		assertEquals(0.0, CustomizeALotHealthBarRenderer.healthFraction(-10, 100), 0.0);
		assertEquals(1.0, CustomizeALotHealthBarRenderer.healthFraction(200, 100), 0.0);
	}

	@Test
	public void customBarSupportsThickBordersAndRoundedCorners()
	{
		BufferedImage square = new BufferedImage(12, 10, BufferedImage.TYPE_INT_ARGB);
		Graphics2D squareGraphics = square.createGraphics();
		try
		{
			CustomizeALotHealthBarRenderer.drawCustomBar(
				squareGraphics,
				4, 3, 4, 3, 2,
				CustomizeALotHealthBarFillDirection.LEFT_TO_RIGHT,
				CustomizeALotHealthBarGradient.SOLID,
				Color.GREEN, Color.RED,
				CustomizeALotHealthBarGradient.SOLID,
				Color.RED, Color.BLACK,
				0.5,
				Color.BLUE,
				2,
				0);
		}
		finally
		{
			squareGraphics.dispose();
		}
		assertEquals(Color.BLUE.getRGB(), square.getRGB(2, 1));
		assertEquals(Color.BLUE.getRGB(), square.getRGB(3, 3));
		assertEquals(Color.GREEN.getRGB(), square.getRGB(4, 3));
		assertEquals(Color.RED.getRGB(), square.getRGB(7, 3));
		assertEquals(0, square.getRGB(1, 1));

		BufferedImage rounded = new BufferedImage(12, 12, BufferedImage.TYPE_INT_ARGB);
		Graphics2D roundedGraphics = rounded.createGraphics();
		try
		{
			CustomizeALotHealthBarRenderer.drawCustomBar(
				roundedGraphics,
				3, 3, 6, 6, 6,
				CustomizeALotHealthBarFillDirection.LEFT_TO_RIGHT,
				CustomizeALotHealthBarGradient.SOLID,
				Color.GREEN, Color.RED,
				CustomizeALotHealthBarGradient.SOLID,
				Color.RED, Color.BLACK,
				1.0,
				new Color(0, 0, 0, 0),
				0,
				3);
		}
		finally
		{
			roundedGraphics.dispose();
		}
		assertTruePartialAlpha(rounded.getRGB(3, 3));
		assertTruePartialAlpha(rounded.getRGB(6, 3));
		assertEquals(Color.GREEN.getRGB(), rounded.getRGB(6, 4));
	}

	@Test
	public void roundedCornersAreAntialiasedAndSymmetric()
	{
		BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		try
		{
			CustomizeALotHealthBarRenderer.drawCustomBar(
				graphics,
				3, 3, 10, 10, 10,
				CustomizeALotHealthBarFillDirection.LEFT_TO_RIGHT,
				CustomizeALotHealthBarGradient.SOLID,
				Color.GREEN, Color.GREEN,
				CustomizeALotHealthBarGradient.SOLID,
				Color.RED, Color.RED,
				1.0,
				new Color(0, 0, 0, 0),
				0,
				5);
		}
		finally
		{
			graphics.dispose();
		}

		boolean foundPartialAlpha = false;
		for (int y = 0; y < 10; y++)
		{
			for (int x = 0; x < 10; x++)
			{
				int alpha = alpha(image.getRGB(3 + x, 3 + y));
				foundPartialAlpha |= alpha > 0 && alpha < 255;
				assertEquals(alpha, alpha(image.getRGB(3 + (9 - x), 3 + y)));
				assertEquals(alpha, alpha(image.getRGB(3 + x, 3 + (9 - y))));
			}
		}
		assertEquals(true, foundPartialAlpha);
	}

	@Test
	public void roundedSubpixelBorderStaysInsideConfiguredBarBounds()
	{
		Color scene = new Color(31, 47, 63);
		BufferedImage image = new BufferedImage(54, 12, BufferedImage.TYPE_INT_ARGB);
		fillImage(image, scene);
		Graphics2D graphics = image.createGraphics();
		try
		{
			CustomizeALotHealthBarRenderer.drawCustomBar(
				graphics,
				2, 3, 50, 6, 25,
				CustomizeALotHealthBarFillDirection.LEFT_TO_RIGHT,
				CustomizeALotHealthBarGradient.SOLID,
				Color.GREEN, Color.GREEN,
				CustomizeALotHealthBarGradient.SOLID,
				Color.RED, Color.RED,
				0.5,
				new Color(25, 25, 25, 230),
				0.1,
				0.5);
		}
		finally
		{
			graphics.dispose();
		}

		assertEquals(scene.getRGB(), image.getRGB(1, 6));
		assertEquals(scene.getRGB(), image.getRGB(52, 6));
		assertEquals(Color.GREEN.getRGB(), image.getRGB(3, 6));
		assertEquals(Color.RED.getRGB(), image.getRGB(50, 6));
	}

	@Test
	public void fractionalCustomDimensionsProduceSubpixelCoverage()
	{
		BufferedImage image = new BufferedImage(12, 8, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		try
		{
			CustomizeALotHealthBarRenderer.drawCustomBar(
				graphics,
				2.0, 2.0, 6.5, 2.5, 6.5,
				CustomizeALotHealthBarFillDirection.LEFT_TO_RIGHT,
				CustomizeALotHealthBarGradient.SOLID,
				Color.GREEN, Color.GREEN,
				CustomizeALotHealthBarGradient.SOLID,
				Color.RED, Color.RED,
				1.0,
				new Color(0, 0, 0, 0),
				0.0,
				0.0);
		}
		finally
		{
			graphics.dispose();
		}

		assertTruePartialAlpha(image.getRGB(8, 3));
		assertTruePartialAlpha(image.getRGB(4, 4));
	}

	@Test
	public void horizontalAndVerticalGradientsVaryOnTheirConfiguredAxes()
	{
		BufferedImage image = new BufferedImage(10, 8, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		try
		{
			CustomizeALotHealthBarRenderer.drawCustomBar(
				graphics,
				1, 1, 4, 4, 4,
				CustomizeALotHealthBarFillDirection.LEFT_TO_RIGHT,
				CustomizeALotHealthBarGradient.HORIZONTAL,
				Color.RED, Color.BLUE,
				CustomizeALotHealthBarGradient.SOLID,
				Color.BLACK, Color.BLACK,
				1.0,
				new Color(0, 0, 0, 0),
				0,
				0);
			CustomizeALotHealthBarRenderer.drawCustomBar(
				graphics,
				6, 1, 3, 4, 3,
				CustomizeALotHealthBarFillDirection.LEFT_TO_RIGHT,
				CustomizeALotHealthBarGradient.VERTICAL,
				Color.RED, Color.BLUE,
				CustomizeALotHealthBarGradient.SOLID,
				Color.BLACK, Color.BLACK,
				1.0,
				new Color(0, 0, 0, 0),
				0,
				0);
		}
		finally
		{
			graphics.dispose();
		}
		assertNotEquals(image.getRGB(1, 2), image.getRGB(4, 2));
		assertEquals(image.getRGB(2, 1), image.getRGB(2, 4));
		assertNotEquals(image.getRGB(7, 1), image.getRGB(7, 4));
		assertEquals(image.getRGB(6, 2), image.getRGB(8, 2));
	}

	@Test
	public void absoluteGradientCoordinatesStayAnchoredToTheFullBar()
	{
		BufferedImage relative = renderCoordinateMode(
			CustomizeALotHealthBarGradientCoordinates.SEGMENT);
		BufferedImage absolute = renderCoordinateMode(
			CustomizeALotHealthBarGradientCoordinates.FULL_BAR);

		assertEquals(Color.BLUE.getRGB(), relative.getRGB(4, 1));
		assertEquals(Color.RED.getRGB(), relative.getRGB(5, 1));
		assertNotEquals(Color.BLUE.getRGB(), absolute.getRGB(4, 1));
		assertNotEquals(Color.RED.getRGB(), absolute.getRGB(5, 1));
		assertEquals(absolute.getRGB(4, 1), absolute.getRGB(4, 2));
	}

	@Test
	public void frontAndBackGradientCoordinatesCanDiffer()
	{
		BufferedImage frontRelative = renderCoordinateMode(
			CustomizeALotHealthBarGradientCoordinates.SEGMENT,
			CustomizeALotHealthBarGradientCoordinates.FULL_BAR);
		BufferedImage backRelative = renderCoordinateMode(
			CustomizeALotHealthBarGradientCoordinates.FULL_BAR,
			CustomizeALotHealthBarGradientCoordinates.SEGMENT);

		assertEquals(Color.BLUE.getRGB(), frontRelative.getRGB(4, 1));
		assertNotEquals(Color.RED.getRGB(), frontRelative.getRGB(5, 1));
		assertNotEquals(Color.BLUE.getRGB(), backRelative.getRGB(4, 1));
		assertEquals(Color.RED.getRGB(), backRelative.getRGB(5, 1));
	}

	@Test
	public void renderedHealthBasedGradientsMapSecondAtLowAndFirstAtHighHealth()
	{
		Color first = new Color(20, 180, 60);
		Color second = new Color(180, 30, 140);
		assertEquals(second.getRGB(), renderHealthBased(true, 0.0, first, second).getRGB(1, 1));
		assertEquals(first.getRGB(), renderHealthBased(true, 1.0, first, second).getRGB(1, 1));
		assertEquals(second.getRGB(), renderHealthBased(false, 0.0, first, second).getRGB(1, 1));
		assertEquals(first.getRGB(), renderHealthBased(false, 1.0, first, second).getRGB(1, 1));
	}

	@Test
	public void damageTrailHoldsThenDrainsAndResetsOnScaleChange()
	{
		CustomizeALotHealthBarRenderer.DamageTrailState state =
			new CustomizeALotHealthBarRenderer.DamageTrailState();
		assertEquals(1.0, state.update(1.0, 30, 0L, 400, 600), 0.0);
		assertEquals(1.0, state.update(0.5, 30, 100L, 400, 600), 0.0);
		assertEquals(1.0, state.update(0.5, 30, 499L, 400, 600), 0.0);
		assertEquals(0.75, state.update(0.5, 30, 800L, 400, 600), 0.000001);
		assertEquals(0.5, state.update(0.5, 30, 1100L, 400, 600), 0.0);

		assertEquals(0.8, state.update(0.8, 30, 1200L, 400, 600), 0.0);
		assertEquals(0.8, state.update(0.6, 30, 1300L, 400, 600), 0.0);
		assertEquals(0.4, state.update(0.4, 60, 1350L, 400, 600), 0.0);
	}

	@Test
	public void exactMaximumPrimesTheFirstTrailFromDamageAmount()
	{
		CustomizeALotHealthBarRenderer.DamageTrailState exact =
			new CustomizeALotHealthBarRenderer.DamageTrailState();
		exact.recordUnobservedDamage(20, 100, 0.6, 30, 100L);
		assertEquals(0.8, exact.update(0.6, 30, 101L, 400, 600), 0.0);

		CustomizeALotHealthBarRenderer.DamageTrailState unknown =
			new CustomizeALotHealthBarRenderer.DamageTrailState();
		unknown.recordUnobservedDamage(20, null, 0.6, 30, 100L);
		assertEquals(0.6, unknown.update(0.6, 30, 101L, 400, 600), 0.0);
		assertEquals(0.6, unknown.update(0.6, 30, 500L, 400, 600), 0.0);
		assertEquals(0.6, unknown.update(0.6, 30, 801L, 400, 600), 0.0);
	}

	@Test
	public void observedPublicHealthCanPrimeAnUnknownActorsFirstHit()
	{
		CustomizeALotHealthBarRenderer.DamageTrailState state =
			new CustomizeALotHealthBarRenderer.DamageTrailState();
		state.recordUnobservedDamage(20, null, 0.8, 30, 100L);

		assertEquals(0.8, state.update(0.6, 30, 101L, 400, 600), 0.0);
		assertEquals(0.8, state.update(0.6, 30, 500L, 400, 600), 0.0);
		assertEquals(0.7, state.update(0.6, 30, 800L, 400, 600), 0.000001);
	}

	@Test
	public void pendingFirstHitUsesItsEventTimeInsteadOfReplayingWhenFirstRendered()
	{
		CustomizeALotHealthBarRenderer.DamageTrailState state =
			new CustomizeALotHealthBarRenderer.DamageTrailState();
		state.recordUnobservedDamage(20, null, 0.8, 30, 100L);

		assertEquals(0.6, state.update(0.6, 30, 1200L, 400, 600), 0.0);
	}

	@Test
	public void pendingHitsKeepTheirOwnTimelinesBeforeTheFirstRender()
	{
		CustomizeALotHealthBarRenderer.DamageTrailState state =
			new CustomizeALotHealthBarRenderer.DamageTrailState();
		state.recordUnobservedDamage(20, null, 0.8, 30, 0L);
		state.recordUnobservedDamage(20, null, 0.6, 30, 900L);

		assertEquals(0.6, state.update(0.4, 30, 901L, 445, 245), 0.000001);
	}

	@Test
	public void overlappingPendingHitsRetainFreshEarlierDamage()
	{
		CustomizeALotHealthBarRenderer.DamageTrailState state =
			new CustomizeALotHealthBarRenderer.DamageTrailState();
		state.recordUnobservedDamage(20, null, 0.8, 30, 0L);
		state.recordUnobservedDamage(20, null, 0.6, 30, 100L);

		assertEquals(0.8, state.update(0.4, 30, 101L, 445, 245), 0.0);
	}

	@Test
	public void offscreenDamageUsesTheHitTimeAfterTheBarWasInitialized()
	{
		CustomizeALotHealthBarRenderer.DamageTrailState state =
			new CustomizeALotHealthBarRenderer.DamageTrailState();
		assertEquals(0.8, state.update(0.8, 30, 0L, 445, 245), 0.0);
		state.recordUnobservedDamage(20, null, 0.8, 30, 100L);

		assertEquals(0.6, state.update(0.6, 30, 1000L, 445, 245), 0.0);
	}

	@Test
	public void recentOffscreenDamageStillShowsAfterTheBarWasInitialized()
	{
		CustomizeALotHealthBarRenderer.DamageTrailState state =
			new CustomizeALotHealthBarRenderer.DamageTrailState();
		assertEquals(0.8, state.update(0.8, 30, 0L, 445, 245), 0.0);
		state.recordUnobservedDamage(20, null, 0.8, 30, 100L);

		assertEquals(0.8, state.update(0.6, 30, 101L, 445, 245), 0.0);
	}

	@Test
	public void postHitPublicRatioFallsBackToTheLastRenderedHealth()
	{
		CustomizeALotHealthBarRenderer.DamageTrailState state =
			new CustomizeALotHealthBarRenderer.DamageTrailState();
		assertEquals(0.8, state.update(0.8, 30, 0L, 445, 245), 0.0);
		state.recordUnobservedDamage(20, null, 0.6, 30, 100L);

		assertEquals(0.8, state.update(0.6, 30, 101L, 445, 245), 0.0);
	}

	@Test
	public void delayedPostHitPublicRatioDoesNotReplayOldDamage()
	{
		CustomizeALotHealthBarRenderer.DamageTrailState state =
			new CustomizeALotHealthBarRenderer.DamageTrailState();
		assertEquals(0.8, state.update(0.8, 30, 0L, 445, 245), 0.0);
		state.recordUnobservedDamage(20, null, 0.6, 30, 100L);

		assertEquals(0.6, state.update(0.6, 30, 1000L, 445, 245), 0.0);
	}

	@Test
	public void consecutivePostHitSamplesKeepTheirIndividualEventTimes()
	{
		CustomizeALotHealthBarRenderer.DamageTrailState state =
			new CustomizeALotHealthBarRenderer.DamageTrailState();
		assertEquals(0.8, state.update(0.8, 30, 0L, 445, 245), 0.0);
		state.recordUnobservedDamage(20, null, 0.6, 30, 100L);
		state.recordUnobservedDamage(20, null, 0.4, 30, 900L);

		assertEquals(0.6, state.update(0.4, 30, 901L, 445, 245), 0.000001);
	}

	@Test
	public void mixedPreAndPostHitSamplesKeepTheFreshHitTimeline()
	{
		CustomizeALotHealthBarRenderer.DamageTrailState state =
			new CustomizeALotHealthBarRenderer.DamageTrailState();
		assertEquals(1.0, state.update(1.0, 30, 0L, 445, 245), 0.0);
		state.recordUnobservedDamage(20, null, 1.0, 30, 1L);
		state.recordUnobservedDamage(20, null, 0.6, 30, 900L);

		assertEquals(0.8, state.update(0.6, 30, 901L, 445, 245), 0.0);
	}

	@Test
	public void firstRenderMixedSamplesDoNotFoldOldDamageIntoTheFreshHit()
	{
		CustomizeALotHealthBarRenderer.DamageTrailState state =
			new CustomizeALotHealthBarRenderer.DamageTrailState();
		state.recordUnobservedDamage(20, null, 1.0, 30, 1L);
		state.recordUnobservedDamage(20, null, 0.6, 30, 900L);

		assertEquals(0.8, state.update(0.6, 30, 901L, 445, 245), 0.0);
	}

	@Test
	public void firstRenderPostHitSamplesKeepTheFreshObservedBaseline()
	{
		CustomizeALotHealthBarRenderer.DamageTrailState state =
			new CustomizeALotHealthBarRenderer.DamageTrailState();
		state.recordUnobservedDamage(20, null, 0.8, 30, 1L);
		state.recordUnobservedDamage(20, null, 0.6, 30, 900L);

		assertEquals(0.8, state.update(0.6, 30, 901L, 445, 245), 0.0);
	}

	@Test
	public void mixedPendingEvidenceUsesTheCurrentPublicHealthScale()
	{
		CustomizeALotHealthBarRenderer.DamageTrailState state =
			new CustomizeALotHealthBarRenderer.DamageTrailState();
		state.recordUnobservedDamage(20, 100, null, 0, 0L);
		state.recordUnobservedDamage(20, null, 0.6, 30, 900L);

		assertEquals(0.6, state.update(0.4, 30, 901L, 445, 245), 0.0);
	}

	@Test
	public void exactDamageKeepsTheLargerPreviouslyRenderedBaseline()
	{
		CustomizeALotHealthBarRenderer.DamageTrailState state =
			new CustomizeALotHealthBarRenderer.DamageTrailState();
		assertEquals(0.8, state.update(0.8, 30, 0L, 445, 245), 0.0);
		state.recordUnobservedDamage(10, 100, 0.8, 30, 100L);

		assertEquals(0.8, state.update(0.6, 30, 101L, 445, 245), 0.0);
	}

	@Test
	public void overlappingHitsDrainContinuouslyAtTheOlderExpirationBoundary()
	{
		CustomizeALotHealthBarRenderer.DamageTrailState state =
			new CustomizeALotHealthBarRenderer.DamageTrailState();
		assertEquals(1.0, state.update(1.0, 30, 0L, 445, 245), 0.0);
		state.recordUnobservedDamage(20, null, 1.0, 30, 100L);
		assertEquals(1.0, state.update(0.8, 30, 101L, 445, 245), 0.0);
		state.recordUnobservedDamage(20, null, 0.8, 30, 200L);
		assertEquals(1.0, state.update(0.6, 30, 201L, 445, 245), 0.0);

		double beforeDrainEnds = state.update(0.6, 30, 889L, 445, 245);
		double whenDrainEnds = state.update(0.6, 30, 890L, 445, 245);
		assertTrue(beforeDrainEnds - whenDrainEnds < 0.01);
		assertEquals(0.6, whenDrainEnds, 0.0);
	}

	@Test
	public void partialHealingClearsUnappliedDamageBeforeTheNextHit()
	{
		CustomizeALotHealthBarRenderer.DamageTrailState state =
			new CustomizeALotHealthBarRenderer.DamageTrailState();
		assertEquals(1.0, state.update(1.0, 30, 0L, 445, 245), 0.0);
		assertEquals(1.0, state.update(0.5, 30, 100L, 445, 245), 0.0);
		state.recordUnobservedDamage(10, 100, 0.5, 30, 150L);
		assertEquals(1.0, state.update(0.7, 30, 200L, 445, 245), 0.0);
		state.recordUnobservedDamage(10, 100, 0.7, 30, 800L);

		assertEquals(0.7, state.update(0.6, 30, 801L, 445, 245), 0.0);
	}

	@Test
	public void firstObservationStartsAtAnAlreadyDamagedActorsCurrentHealth()
	{
		CustomizeALotHealthBarRenderer.DamageTrailState state =
			new CustomizeALotHealthBarRenderer.DamageTrailState();

		assertEquals(15.0 / 36.0, state.update(15.0 / 36.0, 36, 100L, 445, 245), 0.0);
		assertEquals(15.0 / 36.0, state.update(15.0 / 36.0, 36, 600L, 445, 245), 0.0);
	}

	@Test
	public void onlyHealthDamageHitsplatsPrimeTheTrail()
	{
		assertEquals(true,
			CustomizeALotHealthBarRenderer.isHealthDamageHitsplat(net.runelite.api.HitsplatID.DAMAGE_OTHER));
		assertEquals(true,
			CustomizeALotHealthBarRenderer.isHealthDamageHitsplat(net.runelite.api.HitsplatID.POISON));
		assertEquals(true,
			CustomizeALotHealthBarRenderer.isHealthDamageHitsplat(net.runelite.api.HitsplatID.BURN));
		assertEquals(true,
			CustomizeALotHealthBarRenderer.isHealthDamageHitsplat(
				net.runelite.api.HitsplatID.DAMAGE_OTHER_POISE));
		assertEquals(false,
			CustomizeALotHealthBarRenderer.isHealthDamageHitsplat(net.runelite.api.HitsplatID.HEAL));
		assertEquals(false,
			CustomizeALotHealthBarRenderer.isHealthDamageHitsplat(net.runelite.api.HitsplatID.PRAYER_DRAIN));
	}

	@Test
	public void damageTrailOccupiesOnlyRecentlyLostHealth()
	{
		assertEquals(
			new Rectangle2D.Double(3.0, 0.0, 3.0, 4.0),
			CustomizeALotHealthBarRenderer.trailBounds(
				0.0, 0.0, 8.0, 4.0, 3.0, 6.0,
				CustomizeALotHealthBarFillDirection.LEFT_TO_RIGHT));
		assertEquals(
			new Rectangle2D.Double(2.0, 0.0, 3.0, 4.0),
			CustomizeALotHealthBarRenderer.trailBounds(
				0.0, 0.0, 8.0, 4.0, 3.0, 6.0,
				CustomizeALotHealthBarFillDirection.RIGHT_TO_LEFT));
		assertEquals(
			new Rectangle2D.Double(0.0, 2.0, 8.0, 1.0),
			CustomizeALotHealthBarRenderer.trailBounds(
				0.0, 0.0, 8.0, 4.0, 1.0, 2.0,
				CustomizeALotHealthBarFillDirection.BOTTOM_TO_TOP));
	}

	@Test
	public void healthSegmentsAnchorToTheFillOrigin()
	{
		assertArrayEquals(
			new double[]{8.0, 16.0, 24.0},
			CustomizeALotHealthBarRenderer.healthSegmentPositions(
				30.0,
				CustomizeALotHealthBarFillDirection.LEFT_TO_RIGHT,
				30,
				8),
			0.000001);
		assertArrayEquals(
			new double[]{22.0, 14.0, 6.0},
			CustomizeALotHealthBarRenderer.healthSegmentPositions(
				30.0,
				CustomizeALotHealthBarFillDirection.RIGHT_TO_LEFT,
				30,
				8),
			0.000001);
		assertArrayEquals(
			new double[]{22.0, 14.0, 6.0},
			CustomizeALotHealthBarRenderer.healthSegmentPositions(
				30.0,
				CustomizeALotHealthBarFillDirection.BOTTOM_TO_TOP,
				30,
				8),
			0.000001);
	}

	@Test
	public void healthSegmentSourceUsesExactHpAndExplicitFallbackModes()
	{
		assertEquals(
			99,
			CustomizeALotHealthBarRenderer.segmentMaximumValue(
				CustomizeALotHealthBarSegmentValueMode.EXACT_HP_WITH_PUBLIC_FALLBACK,
				99,
				30));
		assertEquals(
			30,
			CustomizeALotHealthBarRenderer.segmentMaximumValue(
				CustomizeALotHealthBarSegmentValueMode.EXACT_HP_WITH_PUBLIC_FALLBACK,
				null,
				30));
		assertEquals(
			0,
			CustomizeALotHealthBarRenderer.segmentMaximumValue(
				CustomizeALotHealthBarSegmentValueMode.EXACT_HP_ONLY,
				null,
				30));
		assertEquals(
			30,
			CustomizeALotHealthBarRenderer.segmentMaximumValue(
				CustomizeALotHealthBarSegmentValueMode.PUBLIC_SCALE,
				99,
				30));
		assertArrayEquals(
			new double[]{10.0, 20.0, 30.0, 40.0, 50.0, 60.0, 70.0, 80.0, 90.0},
			CustomizeALotHealthBarRenderer.healthSegmentPositions(
				99.0,
				CustomizeALotHealthBarFillDirection.LEFT_TO_RIGHT,
				99,
				10),
			0.000001);
	}

	@Test
	public void poisonedColorAppliesOnlyToThePoisonedLocalPlayer()
	{
		Color normal = new Color(10, 20, 30, 140);
		Color poisoned = new Color(50, 60, 70, 180);
		assertSame(normal,
			CustomizeALotHealthBarRenderer.frontColorFor(false, 5, normal, poisoned));
		assertSame(normal,
			CustomizeALotHealthBarRenderer.frontColorFor(true, 0, normal, poisoned));
		assertSame(poisoned,
			CustomizeALotHealthBarRenderer.frontColorFor(true, 1, normal, poisoned));
		assertSame(poisoned,
			CustomizeALotHealthBarRenderer.frontColorFor(true, 1_000_003, normal, poisoned));
	}

	@Test
	public void roundedGeometryCacheReusesMatchingDimensions()
	{
		CustomizeALotHealthBarRenderer.clearRoundedGeometryCache();
		try
		{
			CustomizeALotHealthBarRenderer.RoundedGeometry first =
				CustomizeALotHealthBarRenderer.roundedGeometry(30, 8, 4, 2);
			assertSame(
				first,
				CustomizeALotHealthBarRenderer.roundedGeometry(30, 8, 4, 2));
			assertNotSame(
				first,
				CustomizeALotHealthBarRenderer.roundedGeometry(30, 8, 4, 3));
			assertEquals(2, CustomizeALotHealthBarRenderer.roundedGeometryCacheSize());
		}
		finally
		{
			CustomizeALotHealthBarRenderer.clearRoundedGeometryCache();
		}
	}

	@Test
	public void roundedGeometryCacheStaysBounded()
	{
		CustomizeALotHealthBarRenderer.clearRoundedGeometryCache();
		try
		{
			int limit = CustomizeALotHealthBarRenderer.roundedGeometryCacheLimit();
			for (int i = 0; i < limit + 10; i++)
			{
				CustomizeALotHealthBarRenderer.roundedGeometry(20 + i, 8, 3, 1);
			}

			assertEquals(limit, CustomizeALotHealthBarRenderer.roundedGeometryCacheSize());
		}
		finally
		{
			CustomizeALotHealthBarRenderer.clearRoundedGeometryCache();
		}
	}

	@Test
	public void rendererCacheClearAlsoClearsRoundedGeometry()
	{
		CustomizeALotHealthBarRenderer.clearRoundedGeometryCache();
		CustomizeALotHealthBarRenderer renderer =
			new CustomizeALotHealthBarRenderer(null, null);
		CustomizeALotHealthBarRenderer.roundedGeometry(30, 8, 4, 2);
		renderer.damageTrailState(null, 100L).update(1.0, 30, 100L, 400, 600);
		assertEquals(1, renderer.damageTrailStateCount());

		renderer.clearCache();

		assertEquals(0, CustomizeALotHealthBarRenderer.roundedGeometryCacheSize());
		assertEquals(0, renderer.damageTrailStateCount());
	}

	@Test
	public void actorRemovalClearsItsDamageTrailStateImmediately()
	{
		CustomizeALotHealthBarRenderer renderer =
			new CustomizeALotHealthBarRenderer(null, null);
		renderer.damageTrailState(null, 100L).update(1.0, 30, 100L, 400, 600);
		assertEquals(1, renderer.damageTrailStateCount());

		renderer.remove(null);

		assertEquals(0, renderer.damageTrailStateCount());
	}

	@Test
	public void cachedRelativeRoundedGeometryMatchesAbsoluteRgbaRendering()
	{
		int x = 7;
		int y = 6;
		int width = 13;
		int height = 9;
		int fillWidth = 7;
		int cornerRadius = 4;
		int borderThickness = 2;
		Color front = new Color(210, 35, 20, 143);
		Color back = new Color(20, 55, 220, 91);
		Color border = new Color(15, 210, 90, 117);
		Color scene = new Color(31, 47, 63, 179);
		BufferedImage expected = new BufferedImage(28, 24, BufferedImage.TYPE_INT_ARGB);
		BufferedImage actual = new BufferedImage(28, 24, BufferedImage.TYPE_INT_ARGB);
		fillImage(expected, scene);
		fillImage(actual, scene);

		drawAbsoluteRoundedReference(
			expected,
			x,
			y,
			width,
			height,
			fillWidth,
			cornerRadius,
			borderThickness,
			front,
			back,
			border);

		Graphics2D graphics = actual.createGraphics();
		try
		{
			CustomizeALotHealthBarRenderer.drawCustomBar(
				graphics,
				x, y, width, height, fillWidth,
				CustomizeALotHealthBarFillDirection.LEFT_TO_RIGHT,
				CustomizeALotHealthBarGradient.SOLID,
				front, front,
				CustomizeALotHealthBarGradient.SOLID,
				back, back,
				0.5,
				border,
				borderThickness,
				cornerRadius);
		}
		finally
		{
			graphics.dispose();
		}

		assertArrayEquals(
			expected.getRGB(0, 0, expected.getWidth(), expected.getHeight(), null, 0, expected.getWidth()),
			actual.getRGB(0, 0, actual.getWidth(), actual.getHeight(), null, 0, actual.getWidth()));
	}

	private static void fillImage(BufferedImage image, Color color)
	{
		Graphics2D graphics = image.createGraphics();
		try
		{
			graphics.setColor(color);
			graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
		}
		finally
		{
			graphics.dispose();
		}
	}

	private static BufferedImage renderCoordinateMode(
		CustomizeALotHealthBarGradientCoordinates coordinates)
	{
		return renderCoordinateMode(coordinates, coordinates);
	}

	private static BufferedImage renderCoordinateMode(
		CustomizeALotHealthBarGradientCoordinates frontCoordinates,
		CustomizeALotHealthBarGradientCoordinates backCoordinates)
	{
		BufferedImage image = new BufferedImage(10, 3, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		try
		{
			CustomizeALotHealthBarRenderer.drawCustomBar(
				graphics,
				0.0, 0.0, 10.0, 3.0, 5.0,
				CustomizeALotHealthBarFillDirection.LEFT_TO_RIGHT,
				CustomizeALotHealthBarGradient.HORIZONTAL,
				Color.RED, Color.BLUE,
				CustomizeALotHealthBarGradient.HORIZONTAL,
				Color.RED, Color.BLUE,
				0.5,
				frontCoordinates,
				backCoordinates,
				5.0,
				new Color(0, 0, 0, 0),
				0,
				1,
				new Color(0, 0, 0, 0),
				0.0,
				new Color(0, 0, 0, 0),
				0.0,
				0.0);
		}
		finally
		{
			graphics.dispose();
		}
		return image;
	}

	private static BufferedImage renderHealthBased(
		boolean front,
		double healthFraction,
		Color first,
		Color second)
	{
		BufferedImage image = new BufferedImage(4, 3, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		try
		{
			CustomizeALotHealthBarRenderer.drawCustomBar(
				graphics,
				0.0, 0.0, 4.0, 3.0, front ? 4.0 : 0.0,
				CustomizeALotHealthBarFillDirection.LEFT_TO_RIGHT,
				CustomizeALotHealthBarGradient.HEALTH_BASED,
				first, second,
				CustomizeALotHealthBarGradient.HEALTH_BASED,
				first, second,
				healthFraction,
				CustomizeALotHealthBarGradientCoordinates.FULL_BAR,
				CustomizeALotHealthBarGradientCoordinates.SEGMENT,
				front ? 4.0 : 0.0,
				new Color(0, 0, 0, 0),
				0,
				1,
				new Color(0, 0, 0, 0),
				0.0,
				new Color(0, 0, 0, 0),
				0.0,
				0.0);
		}
		finally
		{
			graphics.dispose();
		}
		return image;
	}

	private static int alpha(int argb)
	{
		return argb >>> 24;
	}

	private static void assertTruePartialAlpha(int argb)
	{
		int alpha = alpha(argb);
		assertEquals(true, alpha > 0 && alpha < 255);
	}

	private static void drawAbsoluteRoundedReference(
		BufferedImage image,
		int x,
		int y,
		int width,
		int height,
		int fillWidth,
		int cornerRadius,
		int borderThickness,
		Color front,
		Color back,
		Color border)
	{
		Shape inner = new RoundRectangle2D.Double(
			x,
			y,
			width,
			height,
			cornerRadius * 2,
			cornerRadius * 2);
		Graphics2D barGraphics = image.createGraphics();
		try
		{
			enableShapeAntialiasing(barGraphics);
			Area backArea = new Area(inner);
			backArea.intersect(new Area(new Rectangle2D.Double(
				x + fillWidth, y, width - fillWidth, height)));
			barGraphics.setColor(back);
			barGraphics.fill(backArea);
			Area frontArea = new Area(inner);
			frontArea.intersect(new Area(new Rectangle2D.Double(x, y, fillWidth, height)));
			barGraphics.setColor(front);
			barGraphics.fill(frontArea);
		}
		finally
		{
			barGraphics.dispose();
		}

		Area borderArea = new Area(inner);
		borderArea.subtract(new Area(new RoundRectangle2D.Double(
			x + borderThickness,
			y + borderThickness,
			width - borderThickness * 2,
			height - borderThickness * 2,
			Math.max(0, cornerRadius - borderThickness) * 2,
			Math.max(0, cornerRadius - borderThickness) * 2)));
		Graphics2D borderGraphics = image.createGraphics();
		try
		{
			enableShapeAntialiasing(borderGraphics);
			borderGraphics.setColor(border);
			borderGraphics.fill(borderArea);
		}
		finally
		{
			borderGraphics.dispose();
		}
	}

	private static void enableShapeAntialiasing(Graphics2D graphics)
	{
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
		graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
	}
}
