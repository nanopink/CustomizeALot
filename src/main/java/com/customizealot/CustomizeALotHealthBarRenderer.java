package com.customizealot;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.HitsplatID;
import net.runelite.api.NPC;
import net.runelite.api.Point;
import net.runelite.api.Skill;
import net.runelite.api.gameval.SpriteID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.game.NPCManager;

final class CustomizeALotHealthBarRenderer
{
	static final int NO_OCCUPIED_TOP = Integer.MAX_VALUE;

	private static final int CACHE_FAILURE_RETRY_CYCLES = 10;
	private static final Color DEFAULT_FRONT_COLOR = new Color(45, 190, 88);
	private static final Color DEFAULT_FRONT_SECONDARY_COLOR = new Color(34, 158, 72);
	private static final Color DEFAULT_POISONED_FRONT_COLOR = new Color(118, 190, 60);
	private static final Color DEFAULT_BACK_COLOR = new Color(184, 60, 60);
	private static final Color DEFAULT_BACK_SECONDARY_COLOR = new Color(151, 45, 45);
	private static final Color DEFAULT_DAMAGE_TRAIL_COLOR = new Color(245, 185, 66, 210);
	private static final Color DEFAULT_SEGMENT_COLOR = new Color(0, 0, 0, 160);
	private static final Color DEFAULT_BORDER_COLOR = new Color(0x1A1A1A);
	private static final Color TRANSPARENT = new Color(0, 0, 0, 0);
	private static final int BASE_PUBLIC_HEALTH_SCALE = 30;
	private static final double MIN_CUSTOM_WIDTH = 10.0;
	private static final double MAX_CUSTOM_WIDTH = 200.0;
	private static final double MIN_HEALTH_BAR_HEIGHT = 2.0;
	private static final double MAX_HEALTH_BAR_HEIGHT = 20.0;
	private static final double MAX_BORDER_THICKNESS = 10.0;
	private static final double MAX_CORNER_RADIUS = 50.0;
	private static final int MAX_DAMAGE_TRAIL_MILLIS = 5000;
	private static final int MAX_DAMAGE_TRAIL_STATES = 512;
	private static final int MAX_PENDING_DAMAGE_EVENTS = 64;
	private static final long DAMAGE_TRAIL_STALE_MILLIS = 10_000L;
	private static final long DAMAGE_TRAIL_PRUNE_INTERVAL_MILLIS = 1_000L;
	private static final int ROUNDED_GEOMETRY_CACHE_LIMIT = 32;
	private static final Map<GeometryKey, RoundedGeometry> ROUNDED_GEOMETRY_CACHE =
		new LinkedHashMap<GeometryKey, RoundedGeometry>(ROUNDED_GEOMETRY_CACHE_LIMIT, 0.75f, true)
		{
			@Override
			protected boolean removeEldestEntry(Map.Entry<GeometryKey, RoundedGeometry> eldest)
			{
				return size() > ROUNDED_GEOMETRY_CACHE_LIMIT;
			}
		};
	private static final int[][] STANDARD_HEALTH_SPRITES = {
		{30, SpriteID.StandardHealth30.FRONT, SpriteID.StandardHealth30.BACK},
		{40, SpriteID.StandardHealth40.FRONT, SpriteID.StandardHealth40.BACK},
		{50, SpriteID.StandardHealth50.FRONT, SpriteID.StandardHealth50.BACK},
		{60, SpriteID.StandardHealth60.FRONT, SpriteID.StandardHealth60.BACK},
		{70, SpriteID.StandardHealth70.FRONT, SpriteID.StandardHealth70.BACK},
		{80, SpriteID.StandardHealth80.FRONT, SpriteID.StandardHealth80.BACK},
		{90, SpriteID.StandardHealth90.FRONT, SpriteID.StandardHealth90.BACK},
		{100, SpriteID.StandardHealth100.FRONT, SpriteID.StandardHealth100.BACK},
		{120, SpriteID.StandardHealth120.FRONT, SpriteID.StandardHealth120.BACK},
		{140, SpriteID.StandardHealth140.FRONT, SpriteID.StandardHealth140.BACK},
		{160, SpriteID.StandardHealth160.FRONT, SpriteID.StandardHealth160.BACK}
	};

	private final Client client;
	private final CustomizeALotConfig config;
	private final NPCManager npcManager;
	private final CustomizeALotRetryCache<Integer, CustomizeALotSprite> sprites =
		new CustomizeALotRetryCache<>(CACHE_FAILURE_RETRY_CYCLES);
	private final Map<Actor, DamageTrailState> damageTrails = new IdentityHashMap<>();
	private long nextDamageTrailPruneMillis;

	@Inject
	CustomizeALotHealthBarRenderer(
		Client client,
		CustomizeALotConfig config,
		NPCManager npcManager)
	{
		this.client = client;
		this.config = config;
		this.npcManager = npcManager;
	}

	CustomizeALotHealthBarRenderer(Client client, CustomizeALotConfig config)
	{
		this(client, config, null);
	}

	int render(Graphics2D graphics, Actor actor, Point anchor, int stackAnchorY)
	{
		if (actor == null || !config.healthBarsEnabled() || anchor == null)
		{
			return NO_OCCUPIED_TOP;
		}

		int ratio = actor.getHealthRatio();
		int healthScale = actor.getHealthScale();
		if (!isRenderableHealthState(true, ratio, healthScale))
		{
			return NO_OCCUPIED_TOP;
		}

		if (usesRuneScapeSprites(config.healthBarPreset()))
		{
			return drawNative(graphics, anchor, stackAnchorY, ratio, healthScale);
		}

		return drawSolid(
			graphics,
			actor,
			anchor,
			stackAnchorY,
			ratio,
			healthScale,
			monotonicMillis());
	}

	boolean hasRenderableHealthBar(Actor actor)
	{
		if (actor == null || !config.healthBarsEnabled())
		{
			return false;
		}

		return isRenderableHealthState(
			true,
			actor.getHealthRatio(),
			actor.getHealthScale());
	}

	static boolean isRenderableHealthState(boolean enabled, int ratio, int scale)
	{
		return enabled && ratio >= 0 && scale > 0;
	}

	void recordDamage(Actor actor, int hitsplatType, int amount)
	{
		if (actor == null
			|| amount <= 0
			|| !isHealthDamageHitsplat(hitsplatType)
			|| !config.healthBarsEnabled()
			|| usesRuneScapeSprites(config.healthBarPreset())
			|| !config.healthBarDamageTrailEnabled()
			|| orDefault(config.healthBarDamageTrailColor(), DEFAULT_DAMAGE_TRAIL_COLOR).getAlpha() == 0)
		{
			return;
		}

		long nowMillis = monotonicMillis();
		Integer maximumHitpoints = exactMaximumHitpoints(actor);
		int observedHealthScale = actor.getHealthScale();
		int observedHealthRatio = actor.getHealthRatio();
		Double observedHealthFraction = observedHealthRatio >= 0 && observedHealthScale > 0
			? healthFraction(observedHealthRatio, observedHealthScale)
			: null;
		if ((maximumHitpoints == null || maximumHitpoints <= 0)
			&& observedHealthFraction == null)
		{
			return;
		}
		damageTrailState(actor, nowMillis).recordUnobservedDamage(
			amount,
			maximumHitpoints,
			observedHealthFraction,
			observedHealthScale,
			nowMillis);
	}

	static boolean usesRuneScapeSprites(CustomizeALotHealthBarPreset preset)
	{
		return preset == null || preset == CustomizeALotHealthBarPreset.RUNESCAPE;
	}

	static boolean isHealthDamageHitsplat(int hitsplatType)
	{
		return hitsplatType == HitsplatID.DAMAGE_ME
			|| hitsplatType == HitsplatID.DAMAGE_OTHER
			|| hitsplatType == HitsplatID.POISON
			|| hitsplatType == HitsplatID.VENOM
			|| hitsplatType == HitsplatID.DAMAGE_ME_CYAN
			|| hitsplatType == HitsplatID.DAMAGE_OTHER_CYAN
			|| hitsplatType == HitsplatID.DAMAGE_ME_ORANGE
			|| hitsplatType == HitsplatID.DAMAGE_OTHER_ORANGE
			|| hitsplatType == HitsplatID.DAMAGE_ME_YELLOW
			|| hitsplatType == HitsplatID.DAMAGE_OTHER_YELLOW
			|| hitsplatType == HitsplatID.DAMAGE_ME_WHITE
			|| hitsplatType == HitsplatID.DAMAGE_OTHER_WHITE
			|| hitsplatType == HitsplatID.DAMAGE_MAX_ME
			|| hitsplatType == HitsplatID.DAMAGE_MAX_ME_CYAN
			|| hitsplatType == HitsplatID.DAMAGE_MAX_ME_ORANGE
			|| hitsplatType == HitsplatID.DAMAGE_MAX_ME_YELLOW
			|| hitsplatType == HitsplatID.DAMAGE_MAX_ME_WHITE
			|| hitsplatType == HitsplatID.DAMAGE_ME_POISE
			|| hitsplatType == HitsplatID.DAMAGE_OTHER_POISE
			|| hitsplatType == HitsplatID.DAMAGE_MAX_ME_POISE
			|| hitsplatType == HitsplatID.BLEED
			|| hitsplatType == HitsplatID.BURN;
	}

	private int drawNative(
		Graphics2D graphics,
		Point anchor,
		int stackAnchorY,
		int ratio,
		int healthScale)
	{
		int[] spriteIds = healthSpritesFor(healthScale);
		CustomizeALotSprite front = getSprite(spriteIds[1]);
		CustomizeALotSprite back = getSprite(spriteIds[2]);
		if (front == null || back == null)
		{
			return NO_OCCUPIED_TOP;
		}

		int widthPercent = effectiveScalePercent(
			config.healthBarScaleMode(),
			config.healthBarScalePercent(),
			config.healthBarLargeScalePercent(),
			config.healthBarScaleThreshold(),
			healthScale);
		int heightPercent = effectiveScalePercent(
			config.healthBarScaleMode(),
			config.healthBarScalePercent(),
			config.healthBarLargeHeightScalePercent(),
			config.healthBarScaleThreshold(),
			healthScale);
		int width = scaled(back.getWidth(), widthPercent);
		int height = rasterDimension(scaledDimension(
			clampedHealthBarHeight(config.healthBarHeight()),
			heightPercent));
		int x = barLeft(anchor.getX(), width, config.healthBarXOffset());
		int y = barTop(stackAnchorY, height, config.healthBarYOffset());
		int fillWidth = filledWidth(width, ratio, healthScale);
		CustomizeALotSpriteScalingMode scalingMode = config.spriteScalingMode();
		BufferedImage backImage = back.getImageForScaling(scalingMode, width, height);
		BufferedImage frontImage = front.getImageForScaling(scalingMode, width, height);

		Graphics2D spriteGraphics = (Graphics2D) graphics.create();
		try
		{
			spriteGraphics.setRenderingHint(
				RenderingHints.KEY_INTERPOLATION,
				interpolationHint(scalingMode));
			spriteGraphics.drawImage(backImage, x, y, width, height, null);
			spriteGraphics.clipRect(x, y, fillWidth, height);
			spriteGraphics.drawImage(frontImage, x, y, width, height, null);
		}
		finally
		{
			spriteGraphics.dispose();
		}
		return y;
	}

	private int drawSolid(
		Graphics2D graphics,
		Actor actor,
		Point anchor,
		int stackAnchorY,
		int ratio,
		int healthScale,
		long nowMillis)
	{
		int widthPercent = effectiveScalePercent(
			config.healthBarScaleMode(),
			config.healthBarScalePercent(),
			config.healthBarLargeScalePercent(),
			config.healthBarScaleThreshold(),
			healthScale);
		int heightPercent = effectiveScalePercent(
			config.healthBarScaleMode(),
			config.healthBarScalePercent(),
			config.healthBarLargeHeightScalePercent(),
			config.healthBarScaleThreshold(),
			healthScale);
		double width = scaledDimension(
			clampedCustomWidth(config.healthBarSolidWidth()),
			widthPercent);
		double height = scaledDimension(
			clampedHealthBarHeight(config.healthBarHeight()),
			heightPercent);
		double borderThickness = clampBorderThickness(config.healthBarBorderThickness());
		double x = barLeft(anchor.getX(), width, config.healthBarXOffset());
		double y = solidBarTop(
			stackAnchorY,
			height,
			config.healthBarYOffset(),
			borderThickness);
		CustomizeALotHealthBarFillDirection fillDirection = effectiveFillDirection(
			config.healthBarFillDirection());
		double maximumFillExtent = fillDirection.isVertical() ? height : width;
		double fillExtent = filledExtent(
			fillDirection.isVertical() ? height : width,
			ratio,
			healthScale);
		double healthFraction = healthFraction(ratio, healthScale);
		Color damageTrailColor = orDefault(config.healthBarDamageTrailColor(), DEFAULT_DAMAGE_TRAIL_COLOR);
		double damageTrailFraction = healthFraction;
		if (config.healthBarDamageTrailEnabled() && damageTrailColor.getAlpha() > 0)
		{
			damageTrailFraction = damageTrailState(actor, nowMillis).update(
				healthFraction,
				healthScale,
				nowMillis,
				clampDuration(config.healthBarDamageTrailHold()),
				clampDuration(config.healthBarDamageTrailDrain()));
		}
		else
		{
			damageTrails.remove(actor);
		}
		double damageTrailExtent = Math.max(fillExtent, maximumFillExtent * damageTrailFraction);
		Color segmentColor = orDefault(config.healthBarSegmentColor(), DEFAULT_SEGMENT_COLOR);
		boolean drawSegments = config.healthBarSegmentsEnabled()
			&& segmentColor.getAlpha() > 0
			&& config.healthBarSegmentThickness() > 0.0;
		int segmentMaximum = drawSegments
			? healthSegmentMaximum(actor, healthScale, config.healthBarSegmentValueMode())
			: 0;
		boolean localPlayer = actor == client.getLocalPlayer();
		drawCustomBar(
			graphics,
			x,
			y,
			width,
			height,
			fillExtent,
			fillDirection,
			config.healthBarFrontGradient(),
			frontColorFor(
				localPlayer,
				localPlayer ? client.getVarpValue(VarPlayerID.POISON) : 0,
				orDefault(config.healthBarFrontColor(), DEFAULT_FRONT_COLOR),
				orDefault(config.healthBarPoisonedFrontColor(), DEFAULT_POISONED_FRONT_COLOR)),
			orDefault(config.healthBarFrontSecondaryColor(), DEFAULT_FRONT_SECONDARY_COLOR),
			config.healthBarBackGradient(),
			orDefault(config.healthBarBackColor(), DEFAULT_BACK_COLOR),
			orDefault(config.healthBarBackSecondaryColor(), DEFAULT_BACK_SECONDARY_COLOR),
			healthFraction,
			config.healthBarFrontGradientCoordinates(),
			config.healthBarBackGradientCoordinates(),
			damageTrailExtent,
			damageTrailColor,
			segmentMaximum,
			config.healthBarHitpointsPerSegment(),
			segmentColor,
			drawSegments ? config.healthBarSegmentThickness() : 0.0,
			orDefault(config.healthBarBorderColor(), DEFAULT_BORDER_COLOR),
			borderThickness,
			config.healthBarCornerRadius());
		return (int) Math.floor(y - borderThickness);
	}

	static int barLeft(int anchorX, int width, int xOffset)
	{
		return anchorX - width / 2 + xOffset;
	}

	static double barLeft(int anchorX, double width, int xOffset)
	{
		return alignedCenteredCoordinate(anchorX + xOffset, width);
	}

	static int barTop(int anchorY, int height, int yOffset)
	{
		return anchorY - height - 2 - yOffset;
	}

	static double barTop(int anchorY, double height, int yOffset)
	{
		return anchorY - height - 2.0 - yOffset;
	}

	static double solidBarTop(
		int anchorY,
		double height,
		int yOffset,
		double borderThickness)
	{
		return barTop(anchorY, height, yOffset)
			- Math.max(0.0, borderThickness - 2.0);
	}

	static void drawCustomBar(
		Graphics2D graphics,
		int x,
		int y,
		int width,
		int height,
		int fillWidth,
		Color frontColor,
		Color backColor,
		Color borderColor)
	{
		drawCustomBar(
			graphics,
			x,
			y,
			width,
			height,
			fillWidth,
			CustomizeALotHealthBarFillDirection.LEFT_TO_RIGHT,
			CustomizeALotHealthBarGradient.SOLID,
			frontColor,
			frontColor,
			CustomizeALotHealthBarGradient.SOLID,
			backColor,
			backColor,
			1.0,
			borderColor,
			1,
			0);
	}

	static void drawCustomBar(
		Graphics2D graphics,
		double x,
		double y,
		double width,
		double height,
		double fillExtent,
		CustomizeALotHealthBarFillDirection fillDirection,
		CustomizeALotHealthBarGradient frontGradient,
		Color frontColor,
		Color frontSecondaryColor,
		CustomizeALotHealthBarGradient backGradient,
		Color backColor,
		Color backSecondaryColor,
		double healthFraction,
		Color borderColor,
		double borderThickness,
		double cornerRadius)
	{
		drawCustomBar(
			graphics,
			x,
			y,
			width,
			height,
			fillExtent,
			fillDirection,
			frontGradient,
			frontColor,
			frontSecondaryColor,
			backGradient,
			backColor,
			backSecondaryColor,
			healthFraction,
			CustomizeALotHealthBarGradientCoordinates.SEGMENT,
			CustomizeALotHealthBarGradientCoordinates.SEGMENT,
			fillExtent,
			TRANSPARENT,
			0,
			1,
			TRANSPARENT,
			0.0,
			borderColor,
			borderThickness,
			cornerRadius);
	}

	static void drawCustomBar(
		Graphics2D graphics,
		double x,
		double y,
		double width,
		double height,
		double fillExtent,
		CustomizeALotHealthBarFillDirection fillDirection,
		CustomizeALotHealthBarGradient frontGradient,
		Color frontColor,
		Color frontSecondaryColor,
		CustomizeALotHealthBarGradient backGradient,
		Color backColor,
		Color backSecondaryColor,
		double healthFraction,
		CustomizeALotHealthBarGradientCoordinates frontGradientCoordinates,
		CustomizeALotHealthBarGradientCoordinates backGradientCoordinates,
		double damageTrailExtent,
		Color damageTrailColor,
		int segmentMaximumValue,
		int unitsPerSegment,
		Color segmentColor,
		double segmentThickness,
		Color borderColor,
		double borderThickness,
		double cornerRadius)
	{
		double safeWidth = nonnegativeFinite(width);
		double safeHeight = nonnegativeFinite(height);
		if (safeWidth == 0.0 || safeHeight == 0.0)
		{
			return;
		}

		CustomizeALotHealthBarFillDirection effectiveDirection = effectiveFillDirection(fillDirection);
		double maximumFill = effectiveDirection.isVertical() ? safeHeight : safeWidth;
		double safeFillExtent = clampExtent(fillExtent, maximumFill);
		Color effectiveDamageTrailColor = orDefault(damageTrailColor, DEFAULT_DAMAGE_TRAIL_COLOR);
		double safeDamageTrailExtent = effectiveDamageTrailColor.getAlpha() == 0
			? safeFillExtent
			: Math.max(safeFillExtent, clampExtent(damageTrailExtent, maximumFill));
		Rectangle2D.Double frontBounds = frontBounds2D(
			0.0,
			0.0,
			safeWidth,
			safeHeight,
			safeFillExtent,
			effectiveDirection);
		Rectangle2D.Double trailBounds = trailBounds(
			0.0,
			0.0,
			safeWidth,
			safeHeight,
			safeFillExtent,
			safeDamageTrailExtent,
			effectiveDirection);
		Rectangle2D.Double backBounds = backBounds2D(
			0.0,
			0.0,
			safeWidth,
			safeHeight,
			safeDamageTrailExtent,
			effectiveDirection);
		Rectangle2D.Double fullBarBounds = new Rectangle2D.Double(0.0, 0.0, safeWidth, safeHeight);
		double safeCornerRadius = clampCornerRadius(cornerRadius, safeWidth, safeHeight);
		double safeBorderThickness = clampBorderThickness(borderThickness);
		RoundedGeometry roundedGeometry = safeCornerRadius > 0.0
			? roundedGeometry(safeWidth, safeHeight, safeCornerRadius, safeBorderThickness)
			: null;
		Shape innerShape = roundedGeometry == null ? null : roundedGeometry.inner;
		boolean fullBarFrontGradient =
			frontGradientCoordinates == CustomizeALotHealthBarGradientCoordinates.FULL_BAR;
		boolean fullBarBackGradient =
			backGradientCoordinates == CustomizeALotHealthBarGradientCoordinates.FULL_BAR;

		Graphics2D barGraphics = (Graphics2D) graphics.create();
		try
		{
			barGraphics.translate(x, y);
			enableShapeAntialiasing(barGraphics);
			fillSegment(
				barGraphics,
				backBounds,
				innerShape,
				fullBarBackGradient ? fullBarBounds : backBounds,
				backGradient,
				orDefault(backColor, DEFAULT_BACK_COLOR),
				orDefault(backSecondaryColor, orDefault(backColor, DEFAULT_BACK_COLOR)),
				healthFraction);
			fillSegment(
				barGraphics,
				trailBounds,
				innerShape,
				trailBounds,
				CustomizeALotHealthBarGradient.SOLID,
				effectiveDamageTrailColor,
				effectiveDamageTrailColor,
				healthFraction);
			fillSegment(
				barGraphics,
				frontBounds,
				innerShape,
				fullBarFrontGradient ? fullBarBounds : frontBounds,
				frontGradient,
				orDefault(frontColor, DEFAULT_FRONT_COLOR),
				orDefault(frontSecondaryColor, orDefault(frontColor, DEFAULT_FRONT_COLOR)),
				healthFraction);
			drawHealthSegments(
				barGraphics,
				innerShape,
				safeWidth,
				safeHeight,
				effectiveDirection,
				segmentMaximumValue,
				unitsPerSegment,
				orDefault(segmentColor, DEFAULT_SEGMENT_COLOR),
				segmentThickness);
		}
		finally
		{
			barGraphics.dispose();
		}

		drawBorder(
			graphics,
			x,
			y,
			safeWidth,
			safeHeight,
			safeCornerRadius,
			safeBorderThickness,
			orDefault(borderColor, DEFAULT_BORDER_COLOR),
			roundedGeometry);
	}

	static Rectangle frontBounds(
		int x,
		int y,
		int width,
		int height,
		int fillExtent,
		CustomizeALotHealthBarFillDirection direction)
	{
		int safeWidth = Math.max(0, width);
		int safeHeight = Math.max(0, height);
		CustomizeALotHealthBarFillDirection effectiveDirection = effectiveFillDirection(direction);
		int maximumFill = effectiveDirection.isVertical() ? safeHeight : safeWidth;
		int safeFill = Math.max(0, Math.min(maximumFill, fillExtent));
		switch (effectiveDirection)
		{
			case RIGHT_TO_LEFT:
				return new Rectangle(x + safeWidth - safeFill, y, safeFill, safeHeight);
			case TOP_TO_BOTTOM:
				return new Rectangle(x, y, safeWidth, safeFill);
			case BOTTOM_TO_TOP:
				return new Rectangle(x, y + safeHeight - safeFill, safeWidth, safeFill);
			case LEFT_TO_RIGHT:
			default:
				return new Rectangle(x, y, safeFill, safeHeight);
		}
	}

	static Rectangle backBounds(
		int x,
		int y,
		int width,
		int height,
		int fillExtent,
		CustomizeALotHealthBarFillDirection direction)
	{
		int safeWidth = Math.max(0, width);
		int safeHeight = Math.max(0, height);
		CustomizeALotHealthBarFillDirection effectiveDirection = effectiveFillDirection(direction);
		int maximumFill = effectiveDirection.isVertical() ? safeHeight : safeWidth;
		int safeFill = Math.max(0, Math.min(maximumFill, fillExtent));
		switch (effectiveDirection)
		{
			case RIGHT_TO_LEFT:
				return new Rectangle(x, y, safeWidth - safeFill, safeHeight);
			case TOP_TO_BOTTOM:
				return new Rectangle(x, y + safeFill, safeWidth, safeHeight - safeFill);
			case BOTTOM_TO_TOP:
				return new Rectangle(x, y, safeWidth, safeHeight - safeFill);
			case LEFT_TO_RIGHT:
			default:
				return new Rectangle(x + safeFill, y, safeWidth - safeFill, safeHeight);
		}
	}

	static Rectangle2D.Double frontBounds2D(
		double x,
		double y,
		double width,
		double height,
		double fillExtent,
		CustomizeALotHealthBarFillDirection direction)
	{
		double safeWidth = nonnegativeFinite(width);
		double safeHeight = nonnegativeFinite(height);
		CustomizeALotHealthBarFillDirection effectiveDirection = effectiveFillDirection(direction);
		double maximumFill = effectiveDirection.isVertical() ? safeHeight : safeWidth;
		double safeFill = clampExtent(fillExtent, maximumFill);
		switch (effectiveDirection)
		{
			case RIGHT_TO_LEFT:
				return new Rectangle2D.Double(x + safeWidth - safeFill, y, safeFill, safeHeight);
			case TOP_TO_BOTTOM:
				return new Rectangle2D.Double(x, y, safeWidth, safeFill);
			case BOTTOM_TO_TOP:
				return new Rectangle2D.Double(x, y + safeHeight - safeFill, safeWidth, safeFill);
			case LEFT_TO_RIGHT:
			default:
				return new Rectangle2D.Double(x, y, safeFill, safeHeight);
		}
	}

	static Rectangle2D.Double backBounds2D(
		double x,
		double y,
		double width,
		double height,
		double fillExtent,
		CustomizeALotHealthBarFillDirection direction)
	{
		double safeWidth = nonnegativeFinite(width);
		double safeHeight = nonnegativeFinite(height);
		CustomizeALotHealthBarFillDirection effectiveDirection = effectiveFillDirection(direction);
		double maximumFill = effectiveDirection.isVertical() ? safeHeight : safeWidth;
		double safeFill = clampExtent(fillExtent, maximumFill);
		switch (effectiveDirection)
		{
			case RIGHT_TO_LEFT:
				return new Rectangle2D.Double(x, y, safeWidth - safeFill, safeHeight);
			case TOP_TO_BOTTOM:
				return new Rectangle2D.Double(x, y + safeFill, safeWidth, safeHeight - safeFill);
			case BOTTOM_TO_TOP:
				return new Rectangle2D.Double(x, y, safeWidth, safeHeight - safeFill);
			case LEFT_TO_RIGHT:
			default:
				return new Rectangle2D.Double(x + safeFill, y, safeWidth - safeFill, safeHeight);
		}
	}

	static Rectangle2D.Double trailBounds(
		double x,
		double y,
		double width,
		double height,
		double fillExtent,
		double trailExtent,
		CustomizeALotHealthBarFillDirection direction)
	{
		double safeWidth = nonnegativeFinite(width);
		double safeHeight = nonnegativeFinite(height);
		CustomizeALotHealthBarFillDirection effectiveDirection = effectiveFillDirection(direction);
		double maximumFill = effectiveDirection.isVertical() ? safeHeight : safeWidth;
		double safeFill = clampExtent(fillExtent, maximumFill);
		double safeTrail = Math.max(safeFill, clampExtent(trailExtent, maximumFill));
		double extent = safeTrail - safeFill;
		switch (effectiveDirection)
		{
			case RIGHT_TO_LEFT:
				return new Rectangle2D.Double(x + safeWidth - safeTrail, y, extent, safeHeight);
			case TOP_TO_BOTTOM:
				return new Rectangle2D.Double(x, y + safeFill, safeWidth, extent);
			case BOTTOM_TO_TOP:
				return new Rectangle2D.Double(x, y + safeHeight - safeTrail, safeWidth, extent);
			case LEFT_TO_RIGHT:
			default:
				return new Rectangle2D.Double(x + safeFill, y, extent, safeHeight);
		}
	}

	static Color healthBasedColor(Color primary, Color secondary, double healthFraction)
	{
		Color safePrimary = orDefault(primary, DEFAULT_FRONT_COLOR);
		Color safeSecondary = orDefault(secondary, safePrimary);
		double fraction = clampFraction(healthFraction);
		return new Color(
			interpolateChannel(safeSecondary.getRed(), safePrimary.getRed(), fraction),
			interpolateChannel(safeSecondary.getGreen(), safePrimary.getGreen(), fraction),
			interpolateChannel(safeSecondary.getBlue(), safePrimary.getBlue(), fraction),
			interpolateChannel(safeSecondary.getAlpha(), safePrimary.getAlpha(), fraction));
	}

	static double healthFraction(int ratio, int healthScale)
	{
		if (healthScale <= 0)
		{
			return 0.0;
		}
		return clampFraction(ratio / (double) healthScale);
	}

	private static void fillSegment(
		Graphics2D graphics,
		Rectangle2D bounds,
		Shape innerShape,
		Rectangle2D paintBounds,
		CustomizeALotHealthBarGradient gradient,
		Color primary,
		Color secondary,
		double healthFraction)
	{
		if (bounds == null
			|| bounds.isEmpty()
			|| (primary.getAlpha() == 0 && secondary.getAlpha() == 0))
		{
			return;
		}

		graphics.setPaint(segmentPaint(paintBounds, gradient, primary, secondary, healthFraction));
		if (innerShape == null)
		{
			graphics.fill(bounds);
			return;
		}

		Area clippedSegment = new Area(innerShape);
		clippedSegment.intersect(new Area(bounds));
		graphics.fill(clippedSegment);
	}

	private static Paint segmentPaint(
		Rectangle2D bounds,
		CustomizeALotHealthBarGradient gradient,
		Color primary,
		Color secondary,
		double healthFraction)
	{
		CustomizeALotHealthBarGradient effectiveGradient = gradient == null
			? CustomizeALotHealthBarGradient.SOLID
			: gradient;
		switch (effectiveGradient)
		{
			case HORIZONTAL:
				if (bounds.getWidth() > 1.0)
				{
					return new GradientPaint(
						(float) bounds.getMinX(),
						(float) bounds.getMinY(),
						primary,
						(float) (bounds.getMaxX() - 1.0),
						(float) bounds.getMinY(),
						secondary);
				}
				return primary;
			case VERTICAL:
				if (bounds.getHeight() > 1.0)
				{
					return new GradientPaint(
						(float) bounds.getMinX(),
						(float) bounds.getMinY(),
						primary,
						(float) bounds.getMinX(),
						(float) (bounds.getMaxY() - 1.0),
						secondary);
				}
				return primary;
			case HEALTH_BASED:
				return healthBasedColor(primary, secondary, healthFraction);
			case SOLID:
			default:
				return primary;
		}
	}

	private static void drawHealthSegments(
		Graphics2D graphics,
		Shape innerShape,
		double width,
		double height,
		CustomizeALotHealthBarFillDirection fillDirection,
		int segmentMaximumValue,
		int unitsPerSegment,
		Color color,
		double thickness)
	{
		double safeThickness = clampBorderThickness(thickness);
		if (safeThickness <= 0.0 || color.getAlpha() == 0)
		{
			return;
		}

		double segmentAxisLength = fillDirection.isVertical() ? height : width;
		double[] positions = healthSegmentPositions(
			segmentAxisLength,
			fillDirection,
			segmentMaximumValue,
			unitsPerSegment);
		if (positions.length == 0)
		{
			return;
		}

		Path2D.Double paths = new Path2D.Double();
		if (fillDirection.isVertical())
		{
			for (double position : positions)
			{
				paths.moveTo(0.0, position);
				paths.lineTo(width, position);
			}
		}
		else
		{
			for (double position : positions)
			{
				paths.moveTo(position, 0.0);
				paths.lineTo(position, height);
			}
		}

		Shape dividerShape = new BasicStroke(
			(float) safeThickness,
			BasicStroke.CAP_BUTT,
			BasicStroke.JOIN_MITER).createStrokedShape(paths);
		if (innerShape != null)
		{
			Area clippedDividers = new Area(innerShape);
			clippedDividers.intersect(new Area(dividerShape));
			dividerShape = clippedDividers;
		}
		graphics.setColor(color);
		graphics.fill(dividerShape);
	}

	static double[] healthSegmentFractions(int maximumValue, int unitsPerSegment)
	{
		int safeMaximum = Math.max(0, maximumValue);
		int safeUnits = Math.max(1, unitsPerSegment);
		int dividerCount = (safeMaximum - 1) / safeUnits;
		if (dividerCount <= 0)
		{
			return new double[0];
		}

		double[] fractions = new double[dividerCount];
		for (int i = 0; i < dividerCount; i++)
		{
			fractions[i] = (i + 1) * safeUnits / (double) safeMaximum;
		}
		return fractions;
	}

	static double[] healthSegmentPositions(
		double axisLength,
		CustomizeALotHealthBarFillDirection fillDirection,
		int maximumValue,
		int unitsPerSegment)
	{
		double safeLength = nonnegativeFinite(axisLength);
		double[] fractions = healthSegmentFractions(maximumValue, unitsPerSegment);
		boolean reverse = fillDirection == CustomizeALotHealthBarFillDirection.RIGHT_TO_LEFT
			|| fillDirection == CustomizeALotHealthBarFillDirection.BOTTOM_TO_TOP;
		double[] positions = new double[fractions.length];
		for (int i = 0; i < fractions.length; i++)
		{
			positions[i] = safeLength * (reverse ? 1.0 - fractions[i] : fractions[i]);
		}
		return positions;
	}

	private int healthSegmentMaximum(
		Actor actor,
		int publicHealthScale,
		CustomizeALotHealthBarSegmentValueMode mode)
	{
		CustomizeALotHealthBarSegmentValueMode effectiveMode = mode == null
			? CustomizeALotHealthBarSegmentValueMode.EXACT_HP_WITH_PUBLIC_FALLBACK
			: mode;
		if (effectiveMode == CustomizeALotHealthBarSegmentValueMode.PUBLIC_SCALE)
		{
			return Math.max(0, publicHealthScale);
		}

		return segmentMaximumValue(
			effectiveMode,
			exactMaximumHitpoints(actor),
			publicHealthScale);
	}

	private Integer exactMaximumHitpoints(Actor actor)
	{
		if (client != null && actor == client.getLocalPlayer())
		{
			int hitpoints = client.getRealSkillLevel(Skill.HITPOINTS);
			return hitpoints > 0 ? hitpoints : null;
		}
		if (npcManager != null && actor instanceof NPC)
		{
			Integer hitpoints = npcManager.getHealth(((NPC) actor).getId());
			return hitpoints != null && hitpoints > 0 ? hitpoints : null;
		}
		return null;
	}

	static int segmentMaximumValue(
		CustomizeALotHealthBarSegmentValueMode mode,
		Integer exactMaximumHitpoints,
		int publicHealthScale)
	{
		CustomizeALotHealthBarSegmentValueMode effectiveMode = mode == null
			? CustomizeALotHealthBarSegmentValueMode.EXACT_HP_WITH_PUBLIC_FALLBACK
			: mode;
		if (effectiveMode == CustomizeALotHealthBarSegmentValueMode.PUBLIC_SCALE)
		{
			return Math.max(0, publicHealthScale);
		}
		if (exactMaximumHitpoints != null && exactMaximumHitpoints > 0)
		{
			return exactMaximumHitpoints;
		}
		return effectiveMode == CustomizeALotHealthBarSegmentValueMode.EXACT_HP_ONLY
			? 0
			: Math.max(0, publicHealthScale);
	}

	private static void drawBorder(
		Graphics2D graphics,
		double x,
		double y,
		double width,
		double height,
		double cornerRadius,
		double thickness,
		Color color,
		RoundedGeometry cachedGeometry)
	{
		if (thickness <= 0.0 || color.getAlpha() == 0)
		{
			return;
		}

		Graphics2D borderGraphics = (Graphics2D) graphics.create();
		try
		{
			enableShapeAntialiasing(borderGraphics);
			borderGraphics.setColor(color);
			if (cornerRadius <= 0.0)
			{
				borderGraphics.fill(new Rectangle2D.Double(
					x - thickness, y - thickness, width + thickness * 2.0, thickness));
				borderGraphics.fill(new Rectangle2D.Double(
					x - thickness, y + height, width + thickness * 2.0, thickness));
				borderGraphics.fill(new Rectangle2D.Double(
					x - thickness, y, thickness, height));
				borderGraphics.fill(new Rectangle2D.Double(
					x + width, y, thickness, height));
				return;
			}

			RoundedGeometry effectiveGeometry = cachedGeometry == null
				? roundedGeometry(width, height, cornerRadius, thickness)
				: cachedGeometry;
			borderGraphics.translate(x, y);
			borderGraphics.fill(effectiveGeometry.border);
		}
		finally
		{
			borderGraphics.dispose();
		}
	}

	private static Shape barShape(
		double x,
		double y,
		double width,
		double height,
		double cornerRadius)
	{
		if (cornerRadius <= 0.0)
		{
			return new Rectangle2D.Double(x, y, width, height);
		}
		double diameter = cornerRadius * 2.0;
		return new RoundRectangle2D.Double(x, y, width, height, diameter, diameter);
	}

	static RoundedGeometry roundedGeometry(
		double width,
		double height,
		double cornerRadius,
		double borderThickness)
	{
		double safeWidth = Math.max(1.0, nonnegativeFinite(width));
		double safeHeight = Math.max(1.0, nonnegativeFinite(height));
		double safeCornerRadius = clampCornerRadius(cornerRadius, safeWidth, safeHeight);
		double safeBorderThickness = clampBorderThickness(borderThickness);
		GeometryKey key = new GeometryKey(
			safeWidth,
			safeHeight,
			safeCornerRadius,
			safeBorderThickness);
		synchronized (ROUNDED_GEOMETRY_CACHE)
		{
			RoundedGeometry cached = ROUNDED_GEOMETRY_CACHE.get(key);
			if (cached != null)
			{
				return cached;
			}

			RoundedGeometry created = new RoundedGeometry(
				safeWidth,
				safeHeight,
				safeCornerRadius,
				safeBorderThickness);
			ROUNDED_GEOMETRY_CACHE.put(key, created);
			return created;
		}
	}

	static void clearRoundedGeometryCache()
	{
		synchronized (ROUNDED_GEOMETRY_CACHE)
		{
			ROUNDED_GEOMETRY_CACHE.clear();
		}
	}

	static int roundedGeometryCacheSize()
	{
		synchronized (ROUNDED_GEOMETRY_CACHE)
		{
			return ROUNDED_GEOMETRY_CACHE.size();
		}
	}

	static int roundedGeometryCacheLimit()
	{
		return ROUNDED_GEOMETRY_CACHE_LIMIT;
	}

	private static CustomizeALotHealthBarFillDirection effectiveFillDirection(
		CustomizeALotHealthBarFillDirection direction)
	{
		return direction == null
			? CustomizeALotHealthBarFillDirection.LEFT_TO_RIGHT
			: direction;
	}

	private static double clampBorderThickness(double thickness)
	{
		return clampFinite(thickness, 0.0, MAX_BORDER_THICKNESS, 0.0);
	}

	private static double clampCornerRadius(double radius, double width, double height)
	{
		return Math.min(
			clampFinite(radius, 0.0, MAX_CORNER_RADIUS, 0.0),
			Math.min(width, height) / 2.0);
	}

	private static int interpolateChannel(int start, int end, double fraction)
	{
		return (int) Math.round(start + (end - start) * fraction);
	}

	private static double clampFraction(double fraction)
	{
		if (!Double.isFinite(fraction))
		{
			return 0.0;
		}
		return Math.max(0.0, Math.min(1.0, fraction));
	}

	private static double nonnegativeFinite(double value)
	{
		return Double.isFinite(value) ? Math.max(0.0, value) : 0.0;
	}

	private static double clampFinite(
		double value,
		double minimum,
		double maximum,
		double fallback)
	{
		if (!Double.isFinite(value))
		{
			return fallback;
		}
		return Math.max(minimum, Math.min(maximum, value));
	}

	static double clampedCustomWidth(double configuredWidth)
	{
		return clampFinite(
			configuredWidth,
			MIN_CUSTOM_WIDTH,
			MAX_CUSTOM_WIDTH,
			30.0);
	}

	static double clampedHealthBarHeight(double configuredHeight)
	{
		return clampFinite(
			configuredHeight,
			MIN_HEALTH_BAR_HEIGHT,
			MAX_HEALTH_BAR_HEIGHT,
			5.0);
	}

	private static double clampExtent(double extent, double maximum)
	{
		return Math.max(0.0, Math.min(maximum, nonnegativeFinite(extent)));
	}

	private static int clampDuration(int durationMillis)
	{
		return Math.max(0, Math.min(MAX_DAMAGE_TRAIL_MILLIS, durationMillis));
	}

	private static void enableShapeAntialiasing(Graphics2D graphics)
	{
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
		graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
	}

	private static long monotonicMillis()
	{
		return System.nanoTime() / 1_000_000L;
	}

	static Color frontColorFor(
		boolean localPlayer,
		int poisonValue,
		Color normalColor,
		Color poisonedColor)
	{
		return localPlayer && poisonValue > 0
			? orDefault(poisonedColor, DEFAULT_POISONED_FRONT_COLOR)
			: orDefault(normalColor, DEFAULT_FRONT_COLOR);
	}

	DamageTrailState damageTrailState(Actor actor, long nowMillis)
	{
		pruneDamageTrails(nowMillis);
		DamageTrailState state = damageTrails.get(actor);
		if (state != null)
		{
			return state;
		}

		if (damageTrails.size() >= MAX_DAMAGE_TRAIL_STATES)
		{
			removeOldestDamageTrail();
		}
		DamageTrailState created = new DamageTrailState();
		damageTrails.put(actor, created);
		return created;
	}

	int damageTrailStateCount()
	{
		return damageTrails.size();
	}

	void remove(Actor actor)
	{
		damageTrails.remove(actor);
	}

	private void pruneDamageTrails(long nowMillis)
	{
		if (nowMillis < nextDamageTrailPruneMillis)
		{
			return;
		}

		Iterator<DamageTrailState> iterator = damageTrails.values().iterator();
		while (iterator.hasNext())
		{
			long lastSeenMillis = iterator.next().lastSeenMillis;
			if (nowMillis < lastSeenMillis || nowMillis - lastSeenMillis > DAMAGE_TRAIL_STALE_MILLIS)
			{
				iterator.remove();
			}
		}
		nextDamageTrailPruneMillis = nowMillis + DAMAGE_TRAIL_PRUNE_INTERVAL_MILLIS;
	}

	private void removeOldestDamageTrail()
	{
		Actor oldestActor = null;
		long oldestLastSeen = Long.MAX_VALUE;
		for (Map.Entry<Actor, DamageTrailState> entry : damageTrails.entrySet())
		{
			if (entry.getValue().lastSeenMillis < oldestLastSeen)
			{
				oldestActor = entry.getKey();
				oldestLastSeen = entry.getValue().lastSeenMillis;
			}
		}
		if (oldestActor != null)
		{
			damageTrails.remove(oldestActor);
		}
	}

	private CustomizeALotSprite getSprite(int spriteId)
	{
		return sprites.get(
			spriteId,
			client.getGameCycle(),
			id -> CustomizeALotSprite.load(client, id),
			sprite -> sprite == null);
	}

	void clearCache()
	{
		sprites.clear();
		damageTrails.clear();
		nextDamageTrailPruneMillis = 0L;
		clearRoundedGeometryCache();
	}

	static int filledWidth(int width, int ratio, int healthScale)
	{
		if (width <= 0 || ratio <= 0 || healthScale <= 0)
		{
			return 0;
		}

		return Math.max(1, width * Math.min(ratio, healthScale) / healthScale);
	}

	static double filledExtent(double extent, int ratio, int healthScale)
	{
		double safeExtent = nonnegativeFinite(extent);
		if (safeExtent <= 0.0 || ratio <= 0 || healthScale <= 0)
		{
			return 0.0;
		}

		double proportional = safeExtent * Math.min(ratio, healthScale) / (double) healthScale;
		return Math.min(safeExtent, Math.max(Math.min(1.0, safeExtent), proportional));
	}

	static int scaled(int dimension, int percent)
	{
		return Math.max(1, dimension * clampScalePercent(percent) / 100);
	}

	static double scaledDimension(double dimension, int percent)
	{
		return Math.max(0.1, nonnegativeFinite(dimension) * clampScalePercent(percent) / 100.0);
	}

	static double alignedCenteredCoordinate(double center, double dimension)
	{
		double coordinate = center - dimension / 2.0;
		return Math.abs(dimension - Math.rint(dimension)) < 1.0e-9
			? Math.round(coordinate)
			: coordinate;
	}

	private static int rasterDimension(double dimension)
	{
		return Math.max(1, (int) Math.round(dimension));
	}

	static int effectiveScalePercent(
		CustomizeALotHealthScaleMode mode,
		int basePercent,
		int largePercent,
		int threshold,
		int healthScale)
	{
		int base = clampScalePercent(basePercent);
		int large = clampScalePercent(largePercent);
		if (mode == null || mode == CustomizeALotHealthScaleMode.FIXED)
		{
			return base;
		}

		int safeThreshold = Math.max(100, threshold);
		if (mode == CustomizeALotHealthScaleMode.THRESHOLD)
		{
			return healthScale >= safeThreshold ? large : base;
		}

		if (healthScale >= safeThreshold)
		{
			return large;
		}
		if (healthScale <= BASE_PUBLIC_HEALTH_SCALE || safeThreshold <= BASE_PUBLIC_HEALTH_SCALE)
		{
			return base;
		}

		double progress = (healthScale - BASE_PUBLIC_HEALTH_SCALE)
			/ (double) (safeThreshold - BASE_PUBLIC_HEALTH_SCALE);
		return clampScalePercent((int) Math.round(base + (large - base) * progress));
	}

	private static int clampScalePercent(int percent)
	{
		return Math.max(50, Math.min(200, percent));
	}

	private static Object interpolationHint(CustomizeALotSpriteScalingMode mode)
	{
		return (mode == null ? CustomizeALotSpriteScalingMode.NEAREST : mode).getInterpolationHint();
	}

	private static int[] healthSpritesFor(int healthScale)
	{
		for (int[] sprites : STANDARD_HEALTH_SPRITES)
		{
			if (healthScale <= sprites[0])
			{
				return sprites;
			}
		}

		return STANDARD_HEALTH_SPRITES[STANDARD_HEALTH_SPRITES.length - 1];
	}

	private static Color orDefault(Color color, Color fallback)
	{
		return color == null ? fallback : color;
	}

	static final class RoundedGeometry
	{
		private final Shape inner;
		private final Area border;

		private RoundedGeometry(
			double width,
			double height,
			double cornerRadius,
			double borderThickness)
		{
			inner = barShape(0.0, 0.0, width, height, cornerRadius);
			Area borderArea = new Area(inner);
			double inset = Math.min(borderThickness, Math.min(width, height) / 2.0);
			if (inset > 0.0 && width > inset * 2.0 && height > inset * 2.0)
			{
				borderArea.subtract(new Area(barShape(
					inset,
					inset,
					width - inset * 2.0,
					height - inset * 2.0,
					Math.max(0.0, cornerRadius - inset))));
			}
			border = borderArea;
		}
	}

	static final class DamageTrailState
	{
		private static final double HEALTH_EPSILON = 1.0e-9;

		private boolean initialized;
		private int publicHealthScale;
		private double lastHealthFraction;
		private double trailStartFraction;
		private double trailTargetFraction;
		private long drainStartMillis;
		private long lastSeenMillis;
		private final List<PendingDamageEvent> pendingDamageEvents = new ArrayList<>();

		void recordUnobservedDamage(
			int amount,
			Integer maximumHitpoints,
			Double observedHealthFraction,
			int observedHealthScale,
			long nowMillis)
		{
			if (amount <= 0)
			{
				return;
			}

			double damageFraction = Double.NaN;
			if (maximumHitpoints != null && maximumHitpoints > 0)
			{
				damageFraction = amount / (double) maximumHitpoints;
			}
			double observedFraction = Double.NaN;
			int safeObservedScale = 0;
			if (observedHealthFraction != null
				&& Double.isFinite(observedHealthFraction)
				&& observedHealthScale > 0)
			{
				observedFraction = clampFraction(observedHealthFraction);
				safeObservedScale = observedHealthScale;
			}
			if (!Double.isFinite(damageFraction) && !Double.isFinite(observedFraction))
			{
				return;
			}

			if (pendingDamageEvents.size() >= MAX_PENDING_DAMAGE_EVENTS)
			{
				pendingDamageEvents.remove(0);
			}
			pendingDamageEvents.add(new PendingDamageEvent(
				amount,
				damageFraction,
				observedFraction,
				safeObservedScale,
				nowMillis));
			lastSeenMillis = nowMillis;
		}

		double update(
			double healthFraction,
			int currentPublicHealthScale,
			long nowMillis,
			int holdMillis,
			int drainMillis)
		{
			double currentHealth = clampFraction(healthFraction);
			int safeHealthScale = Math.max(1, currentPublicHealthScale);
			int safeHold = clampDuration(holdMillis);
			int safeDrain = clampDuration(drainMillis);
			if (nowMillis < lastSeenMillis)
			{
				initialize(currentHealth, safeHealthScale, nowMillis);
				pendingDamageEvents.clear();
				return currentHealth;
			}
			if (!initialized)
			{
				if (pendingDamageEvents.isEmpty())
				{
					initialize(currentHealth, safeHealthScale, nowMillis);
					return currentHealth;
				}
				replayPendingDamageEvents(
					currentHealth,
					safeHealthScale,
					nowMillis,
					safeHold,
					safeDrain);
				return Math.max(currentHealth, trailAt(nowMillis, safeDrain));
			}
			else if (publicHealthScale != safeHealthScale)
			{
				initialize(currentHealth, safeHealthScale, nowMillis);
				pendingDamageEvents.clear();
				return currentHealth;
			}

			double existingTrail = trailAt(nowMillis, safeDrain);
			if (currentHealth < lastHealthFraction - HEALTH_EPSILON)
			{
				if (!pendingDamageEvents.isEmpty())
				{
					replayPendingDamageEvents(
						currentHealth,
						safeHealthScale,
						nowMillis,
						safeHold,
						safeDrain);
				}
				else
				{
					startTrail(
						Math.max(existingTrail, lastHealthFraction),
						currentHealth,
						nowMillis,
						safeHold);
				}
			}
			else if (currentHealth > lastHealthFraction + HEALTH_EPSILON)
			{
				pendingDamageEvents.clear();
				if (currentHealth >= existingTrail)
				{
					initialize(currentHealth, safeHealthScale, nowMillis);
				}
			}
			else if (!pendingDamageEvents.isEmpty())
			{
				discardExpiredDamageEvents(nowMillis, safeHold, safeDrain);
			}

			lastHealthFraction = currentHealth;
			lastSeenMillis = nowMillis;
			return Math.max(currentHealth, trailAt(nowMillis, safeDrain));
		}

		private void initialize(double healthFraction, int healthScale, long nowMillis)
		{
			initialized = true;
			publicHealthScale = healthScale;
			lastHealthFraction = healthFraction;
			trailStartFraction = healthFraction;
			trailTargetFraction = healthFraction;
			drainStartMillis = nowMillis;
			lastSeenMillis = nowMillis;
		}

		private void replayPendingDamageEvents(
			double currentHealth,
			int currentHealthScale,
			long nowMillis,
			int holdMillis,
			int drainMillis)
		{
			List<DamageTransition> transitions = damageTransitions(
				currentHealth,
				currentHealthScale);
			if (transitions.isEmpty() && initialized
				&& lastHealthFraction > currentHealth + HEALTH_EPSILON)
			{
				transitions.add(new DamageTransition(
					lastHealthFraction,
					currentHealth,
					pendingDamageEvents.get(0).eventMillis));
			}

			if (!initialized)
			{
				double initialHealth = transitions.isEmpty()
					? currentHealth
					: transitions.get(0).startFraction;
				long initialMillis = transitions.isEmpty()
					? nowMillis
					: transitions.get(0).eventMillis;
				initialize(initialHealth, currentHealthScale, initialMillis);
			}

			for (DamageTransition transition : transitions)
			{
				double existingTrail = trailAt(transition.eventMillis, drainMillis);
				startTrail(
					Math.max(existingTrail, transition.startFraction),
					transition.targetFraction,
					transition.eventMillis,
					holdMillis);
			}

			pendingDamageEvents.clear();
			lastHealthFraction = currentHealth;
			lastSeenMillis = nowMillis;
		}

		private List<DamageTransition> damageTransitions(
			double currentHealth,
			int currentHealthScale)
		{
			if (initialized && lastHealthFraction > currentHealth + HEALTH_EPSILON)
			{
				return allocatedDamageTransitions(lastHealthFraction, currentHealth);
			}

			boolean allObserved = true;
			for (PendingDamageEvent event : pendingDamageEvents)
			{
				if (event.observedHealthScale != currentHealthScale
					|| !Double.isFinite(event.observedHealthFraction))
				{
					allObserved = false;
					break;
				}
			}

			return allObserved
				? observedDamageTransitions(currentHealth)
				: reverseDamageTransitions(currentHealth, currentHealthScale);
		}

		private List<DamageTransition> allocatedDamageTransitions(
			double initialHealth,
			double currentHealth)
		{
			List<DamageTransition> transitions = new ArrayList<>();
			long totalDamage = 0L;
			for (PendingDamageEvent event : pendingDamageEvents)
			{
				totalDamage += event.damageAmount;
			}
			if (totalDamage <= 0L)
			{
				return transitions;
			}

			double totalHealthLoss = initialHealth - currentHealth;
			double runningHealth = initialHealth;
			long allocatedDamage = 0L;
			for (int index = 0; index < pendingDamageEvents.size(); index++)
			{
				PendingDamageEvent event = pendingDamageEvents.get(index);
				allocatedDamage += event.damageAmount;
				double targetHealth = index + 1 == pendingDamageEvents.size()
					? currentHealth
					: initialHealth
						- totalHealthLoss * allocatedDamage / (double) totalDamage;
				addTransition(
					transitions,
					runningHealth,
					targetHealth,
					event.eventMillis);
				runningHealth = targetHealth;
			}
			return transitions;
		}

		private List<DamageTransition> observedDamageTransitions(double currentHealth)
		{
			List<DamageTransition> transitions = new ArrayList<>();
			PendingDamageEvent firstEvent = pendingDamageEvents.get(0);
			PendingDamageEvent lastEvent = pendingDamageEvents.get(pendingDamageEvents.size() - 1);
			boolean postHitSamples = Math.abs(lastEvent.observedHealthFraction - currentHealth)
				<= HEALTH_EPSILON;
			long laterDamage = 0L;
			for (int index = 1; index < pendingDamageEvents.size(); index++)
			{
				laterDamage += pendingDamageEvents.get(index).damageAmount;
			}
			double inferredPostHitInitialHealth = laterDamage <= 0L
				? firstEvent.observedHealthFraction
				: firstEvent.observedHealthFraction
					+ (firstEvent.observedHealthFraction - currentHealth)
						* firstEvent.damageAmount / (double) laterDamage;
			// An inferred value above full health means at least one sample cannot be post-hit.
			// Distribute the known loss by hit size so older damage keeps its older timestamp.
			if (postHitSamples
				&& pendingDamageEvents.size() > 1
				&& inferredPostHitInitialHealth > 1.0 + HEALTH_EPSILON)
			{
				double inferredInitialHealth = currentHealth;
				double exactDamageFraction = 0.0;
				for (PendingDamageEvent event : pendingDamageEvents)
				{
					inferredInitialHealth = Math.max(
						inferredInitialHealth,
						event.observedHealthFraction);
					if (Double.isFinite(event.damageFraction) && event.damageFraction > 0.0)
					{
						exactDamageFraction += event.damageFraction;
					}
				}
				inferredInitialHealth = Math.max(
					inferredInitialHealth,
					clampFraction(currentHealth + exactDamageFraction));
				return allocatedDamageTransitions(inferredInitialHealth, currentHealth);
			}

			if (postHitSamples)
			{
				double runningHealth = initialized ? lastHealthFraction : Double.NaN;
				for (PendingDamageEvent event : pendingDamageEvents)
				{
					double targetHealth = clampFraction(event.observedHealthFraction);
					double previousHealth = runningHealth;
					if (!Double.isFinite(previousHealth)
						&& Double.isFinite(event.damageFraction)
						&& event.damageFraction > 0.0)
					{
						previousHealth = clampFraction(targetHealth + event.damageFraction);
					}
					addTransition(transitions, previousHealth, targetHealth, event.eventMillis);
					runningHealth = targetHealth;
				}
				if (Double.isFinite(runningHealth)
					&& runningHealth > currentHealth + HEALTH_EPSILON)
				{
					addTransition(
						transitions,
						runningHealth,
						currentHealth,
						lastEvent.eventMillis);
				}
				return transitions;
			}

			for (int index = 0; index < pendingDamageEvents.size(); index++)
			{
				PendingDamageEvent event = pendingDamageEvents.get(index);
				double targetHealth = index + 1 < pendingDamageEvents.size()
					? pendingDamageEvents.get(index + 1).observedHealthFraction
					: currentHealth;
				double previousHealth = Math.max(event.observedHealthFraction, targetHealth);
				addTransition(transitions, previousHealth, targetHealth, event.eventMillis);
			}
			return transitions;
		}

		private List<DamageTransition> reverseDamageTransitions(
			double currentHealth,
			int currentHealthScale)
		{
			DamageTransition[] reversed = new DamageTransition[pendingDamageEvents.size()];
			double runningHealth = currentHealth;
			for (int index = pendingDamageEvents.size() - 1; index >= 0; index--)
			{
				PendingDamageEvent event = pendingDamageEvents.get(index);
				double previousHealth = runningHealth;
				if (Double.isFinite(event.damageFraction) && event.damageFraction > 0.0)
				{
					previousHealth = clampFraction(runningHealth + event.damageFraction);
				}
				if (event.observedHealthScale == currentHealthScale
					&& Double.isFinite(event.observedHealthFraction))
				{
					previousHealth = Math.max(previousHealth, event.observedHealthFraction);
				}
				reversed[index] = new DamageTransition(
					previousHealth,
					runningHealth,
					event.eventMillis);
				runningHealth = previousHealth;
			}
			List<DamageTransition> transitions = new ArrayList<>();
			for (DamageTransition transition : reversed)
			{
				addTransition(
					transitions,
					transition.startFraction,
					transition.targetFraction,
					transition.eventMillis);
			}
			return transitions;
		}

		private static void addTransition(
			List<DamageTransition> transitions,
			double startFraction,
			double targetFraction,
			long eventMillis)
		{
			if (Double.isFinite(startFraction)
				&& startFraction > targetFraction + HEALTH_EPSILON)
			{
				transitions.add(new DamageTransition(
					clampFraction(startFraction),
					clampFraction(targetFraction),
					eventMillis));
			}
		}

		private void startTrail(
			double startFraction,
			double targetFraction,
			long eventMillis,
			int holdMillis)
		{
			trailStartFraction = clampFraction(startFraction);
			trailTargetFraction = clampFraction(targetFraction);
			drainStartMillis = eventMillis + holdMillis;
		}

		private void discardExpiredDamageEvents(long nowMillis, int holdMillis, int drainMillis)
		{
			Iterator<PendingDamageEvent> iterator = pendingDamageEvents.iterator();
			while (iterator.hasNext())
			{
				PendingDamageEvent event = iterator.next();
				if (event.isExpired(nowMillis, holdMillis, drainMillis))
				{
					iterator.remove();
				}
			}
		}

		private static final class PendingDamageEvent
		{
			private final int damageAmount;
			private final double damageFraction;
			private final double observedHealthFraction;
			private final int observedHealthScale;
			private final long eventMillis;

			private PendingDamageEvent(
				int damageAmount,
				double damageFraction,
				double observedHealthFraction,
				int observedHealthScale,
				long eventMillis)
			{
				this.damageAmount = damageAmount;
				this.damageFraction = damageFraction;
				this.observedHealthFraction = observedHealthFraction;
				this.observedHealthScale = observedHealthScale;
				this.eventMillis = eventMillis;
			}

			private boolean isExpired(long nowMillis, int holdMillis, int drainMillis)
			{
				return nowMillis >= eventMillis
					&& nowMillis - eventMillis >= (long) holdMillis + drainMillis;
			}
		}

		private static final class DamageTransition
		{
			private final double startFraction;
			private final double targetFraction;
			private final long eventMillis;

			private DamageTransition(double startFraction, double targetFraction, long eventMillis)
			{
				this.startFraction = startFraction;
				this.targetFraction = targetFraction;
				this.eventMillis = eventMillis;
			}
		}

		private double trailAt(long nowMillis, int drainMillis)
		{
			if (nowMillis <= drainStartMillis)
			{
				return trailStartFraction;
			}
			if (drainMillis <= 0)
			{
				return trailTargetFraction;
			}

			double progress = clampFraction((nowMillis - drainStartMillis) / (double) drainMillis);
			return trailStartFraction
				+ (trailTargetFraction - trailStartFraction) * progress;
		}
	}

	private static final class GeometryKey
	{
		private final double width;
		private final double height;
		private final double cornerRadius;
		private final double borderThickness;

		private GeometryKey(
			double width,
			double height,
			double cornerRadius,
			double borderThickness)
		{
			this.width = width;
			this.height = height;
			this.cornerRadius = cornerRadius;
			this.borderThickness = borderThickness;
		}

		@Override
		public boolean equals(Object other)
		{
			if (this == other)
			{
				return true;
			}
			if (!(other instanceof GeometryKey))
			{
				return false;
			}
			GeometryKey key = (GeometryKey) other;
			return Double.compare(width, key.width) == 0
				&& Double.compare(height, key.height) == 0
				&& Double.compare(cornerRadius, key.cornerRadius) == 0
				&& Double.compare(borderThickness, key.borderThickness) == 0;
		}

		@Override
		public int hashCode()
		{
			int result = hashDouble(width);
			result = 31 * result + hashDouble(height);
			result = 31 * result + hashDouble(cornerRadius);
			return 31 * result + hashDouble(borderThickness);
		}

		private static int hashDouble(double value)
		{
			long bits = Double.doubleToLongBits(value);
			return (int) (bits ^ (bits >>> 32));
		}
	}
}
