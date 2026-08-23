package com.customizealot;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.HeadIcon;
import net.runelite.api.NPC;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.Projection;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.gameval.SpriteID;

final class CustomizeALotHeadIconRenderer
{
	private static final int NPC_HINT_SPRITE_INDEX = 0;
	private static final int PLAYER_HINT_SPRITE_INDEX = 1;
	private static final float MIN_PROJECTION_DEPTH = 50.0f;
	private static final double REFERENCE_MAGNIFICATION = 0.375d;
	private static final int MIN_RENDER_SCALE_PERCENT = 10;
	private static final int MAX_RENDER_SCALE_PERCENT = 400;
	private static final int[] RESOURCE_PACK_PRAYER_SPRITE_IDS =
	{
		SpriteID.Prayeron.PROTECT_FROM_MELEE,
		SpriteID.Prayeron.PROTECT_FROM_MISSILES,
		SpriteID.Prayeron.PROTECT_FROM_MAGIC,
		SpriteID.Prayeron.RETRIBUTION,
		SpriteID.Prayeron.SMITE,
		SpriteID.Prayeron.REDEMPTION
	};

	private final Client client;
	private final CustomizeALotConfig config;
	private final CustomizeALotActorUiSpriteCache sprites;
	private final List<CustomizeALotSprite> iconBuffer = new ArrayList<>(4);
	private final float[] projectionBuffer = new float[3];
	private Actor preparedActor;

	@Inject
	CustomizeALotHeadIconRenderer(
		Client client,
		CustomizeALotConfig config,
		CustomizeALotActorUiSpriteCache sprites)
	{
		this.client = client;
		this.config = config;
		this.sprites = sprites;
	}

	boolean prepare(Actor actor)
	{
		clearPrepared();
		if (!config.headIconsEnabled() || actor == null)
		{
			return false;
		}

		if (actor instanceof Player)
		{
			addPlayerIcons(iconBuffer, (Player) actor);
		}
		else if (actor instanceof NPC)
		{
			addNpcIcons(iconBuffer, (NPC) actor);
		}

		addHintArrow(iconBuffer, actor);
		if (iconBuffer.isEmpty())
		{
			return false;
		}

		preparedActor = actor;
		return true;
	}

	int renderPrepared(
		Graphics2D graphics,
		Actor actor,
		int projectionHeight,
		Point anchor,
		int stackAnchorY,
		int occupiedTopY)
	{
		if (actor != preparedActor || anchor == null || iconBuffer.isEmpty())
		{
			clearPrepared();
			return occupiedTopY;
		}

		boolean scaleWithZoom = config.headIconScaleWithZoom();
		int percent = effectiveScalePercent(
			config.headIconScalePercent(),
			scaleWithZoom,
			client.getScale(),
			scaleWithZoom ? projectionDepth(actor, projectionHeight) : Float.NaN);
		int spacing = Math.max(0, Math.min(20, config.headIconSpacing()));
		int cursorBottomY = stackBottomY(
			stackAnchorY,
			occupiedTopY,
			config.headIconYOffset(),
			spacing);

		int occupiedTop = occupiedTopY;
		CustomizeALotSpriteScalingMode scalingMode = config.spriteScalingMode();
		Graphics2D iconGraphics = (Graphics2D) graphics.create();
		try
		{
			iconGraphics.setRenderingHint(
				RenderingHints.KEY_INTERPOLATION,
				(scalingMode == null
					? CustomizeALotSpriteScalingMode.NEAREST
					: scalingMode).getInterpolationHint());
			for (CustomizeALotSprite icon : iconBuffer)
			{
				int canvasWidth = scaled(icon.getMaxWidth(), percent);
				int canvasHeight = scaled(icon.getMaxHeight(), percent);
				int imageWidth = scaledSpan(icon.getOffsetX(), icon.getWidth(), percent);
				int imageHeight = scaledSpan(icon.getOffsetY(), icon.getHeight(), percent);
				int canvasX = iconLeft(
					anchor.getX(),
					canvasWidth,
					config.headIconXOffset());
				int canvasY = cursorBottomY - canvasHeight;
				int imageX = canvasX + scaledEdge(icon.getOffsetX(), percent);
				int imageY = canvasY + scaledEdge(icon.getOffsetY(), percent);
				BufferedImage renderImage = icon.getImageForScaling(
					scalingMode,
					imageWidth,
					imageHeight);
				iconGraphics.drawImage(renderImage, imageX, imageY, imageWidth, imageHeight, null);
				occupiedTop = Math.min(occupiedTop, canvasY);
				cursorBottomY = nextIconBottomY(canvasY, spacing);
			}
		}
		finally
		{
			iconGraphics.dispose();
			clearPrepared();
		}

		return occupiedTop;
	}

	private void addPlayerIcons(List<CustomizeALotSprite> icons, Player player)
	{
		if (config.showSkullIcons())
		{
			add(icons, sprites.get(SpriteID.HEADICONS_PK, player.getSkullIcon()));
		}

		if (config.showPrayerIcons())
		{
			HeadIcon prayer = player.getOverheadIcon();
			if (prayer != null)
			{
				add(icons, getPrayerIcon(prayer.ordinal()));
			}
		}
	}

	private void addNpcIcons(List<CustomizeALotSprite> icons, NPC npc)
	{
		if (!config.showNpcIcons())
		{
			return;
		}

		int[] archiveIds = npc.getOverheadArchiveIds();
		short[] spriteIds = npc.getOverheadSpriteIds();
		if (archiveIds == null || spriteIds == null)
		{
			return;
		}

		int count = Math.min(archiveIds.length, spriteIds.length);
		for (int i = 0; i < count; i++)
		{
			int spriteId = spriteIds[i];
			if (archiveIds[i] >= 0 && spriteId >= 0)
			{
				add(icons, archiveIds[i] == SpriteID.HEADICONS_PRAYER
					? getPrayerIcon(spriteId)
					: sprites.get(archiveIds[i], spriteId));
			}
		}
	}

	private CustomizeALotSprite getPrayerIcon(int prayerIndex)
	{
		return sprites.getPrayerIcon(
			prayerIndex,
			resourcePackPrayerSpriteId(prayerIndex),
			config.showPrayerIconBackground());
	}

	static int resourcePackPrayerSpriteId(int prayerIndex)
	{
		return prayerIndex >= 0 && prayerIndex < RESOURCE_PACK_PRAYER_SPRITE_IDS.length
			? RESOURCE_PACK_PRAYER_SPRITE_IDS[prayerIndex]
			: -1;
	}

	private void addHintArrow(List<CustomizeALotSprite> icons, Actor actor)
	{
		if (!config.showHintArrows() || !client.hasHintArrow() || client.getGameCycle() % 20 >= 10)
		{
			return;
		}

		if (actor == client.getHintArrowNpc())
		{
			add(icons, sprites.get(SpriteID.HEADICONS_HINT, NPC_HINT_SPRITE_INDEX));
		}
		else if (actor == client.getHintArrowPlayer())
		{
			add(icons, sprites.get(SpriteID.HEADICONS_HINT, PLAYER_HINT_SPRITE_INDEX));
		}
	}

	private static void add(List<CustomizeALotSprite> icons, CustomizeALotSprite icon)
	{
		if (icon != null)
		{
			icons.add(icon);
		}
	}

	private float projectionDepth(Actor actor, int projectionHeight)
	{
		LocalPoint localLocation = actor.getLocalLocation();
		WorldView worldView = actor.getWorldView();
		Projection projection = worldView == null ? null : worldView.getCanvasProjection();
		if (localLocation == null || projection == null)
		{
			return Float.NaN;
		}

		int worldZ = Perspective.getFootprintTileHeight(
			client,
			localLocation,
			worldView.getPlane(),
			actor.getFootprintSize())
			- actor.getAnimationHeightOffset()
			- projectionHeight;
		float[] projected = projection.project(
			localLocation.getX(),
			worldZ,
			localLocation.getY(),
			projectionBuffer);
		return projected == null || projected.length < 3
			? Float.NaN
			: projected[2];
	}

	static int effectiveScalePercent(
		int configuredPercent,
		boolean scaleWithZoom,
		int projectionScale,
		float projectionDepth)
	{
		int basePercent = clampConfiguredScalePercent(configuredPercent);
		if (!scaleWithZoom
			|| projectionScale <= 0
			|| !Float.isFinite(projectionDepth)
			|| projectionDepth < MIN_PROJECTION_DEPTH)
		{
			return basePercent;
		}

		double magnification = projectionScale / (double) projectionDepth;
		double effectivePercent = basePercent * magnification / REFERENCE_MAGNIFICATION;
		if (!Double.isFinite(effectivePercent))
		{
			return basePercent;
		}
		return (int) clampRenderScalePercent(Math.round(effectivePercent));
	}

	static int scaled(int dimension, int percent)
	{
		long scaled = (long) dimension * clampRenderScalePercent(percent) / 100L;
		return (int) Math.max(1L, Math.min(Integer.MAX_VALUE, scaled));
	}

	static int iconLeft(int anchorX, int canvasWidth, int xOffset)
	{
		return anchorX - canvasWidth / 2 + xOffset;
	}

	static int stackBottomY(int anchorY, int occupiedTopY, int yOffset, int spacing)
	{
		int bottomY = occupiedTopY == CustomizeALotHealthBarRenderer.NO_OCCUPIED_TOP
			? anchorY
			: Math.min(anchorY, occupiedTopY);
		return bottomY - clampedSpacing(spacing) - yOffset;
	}

	static int nextIconBottomY(int previousIconTopY, int spacing)
	{
		return previousIconTopY - clampedSpacing(spacing);
	}

	static int scaledEdge(int coordinate, int percent)
	{
		long scaled = scaledEdge((long) coordinate, percent);
		return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, scaled));
	}

	static int scaledSpan(int offset, int dimension, int percent)
	{
		long start = scaledEdge((long) offset, percent);
		long end = scaledEdge((long) offset + dimension, percent);
		return (int) Math.max(1L, Math.min(Integer.MAX_VALUE, end - start));
	}

	private static long scaledEdge(long coordinate, int percent)
	{
		return Math.floorDiv(coordinate * clampRenderScalePercent(percent), 100L);
	}

	private static int clampConfiguredScalePercent(int percent)
	{
		return Math.max(50, Math.min(200, percent));
	}

	private static int clampRenderScalePercent(int percent)
	{
		return (int) clampRenderScalePercent((long) percent);
	}

	private static long clampRenderScalePercent(long percent)
	{
		return Math.max(MIN_RENDER_SCALE_PERCENT, Math.min(MAX_RENDER_SCALE_PERCENT, percent));
	}

	private static int clampedSpacing(int spacing)
	{
		return Math.max(0, Math.min(20, spacing));
	}

	void clearCache()
	{
		clearPrepared();
		sprites.clear();
	}

	private void clearPrepared()
	{
		preparedActor = null;
		iconBuffer.clear();
	}
}
