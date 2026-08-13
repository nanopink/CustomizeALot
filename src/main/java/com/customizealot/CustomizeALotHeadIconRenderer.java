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
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.gameval.SpriteID;

final class CustomizeALotHeadIconRenderer
{
	private static final int NPC_HINT_SPRITE_INDEX = 0;
	private static final int PLAYER_HINT_SPRITE_INDEX = 1;
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

	int render(
		Graphics2D graphics,
		Actor actor,
		Point anchor,
		int stackAnchorY,
		int occupiedTopY)
	{
		prepare(actor);
		return renderPrepared(graphics, actor, anchor, stackAnchorY, occupiedTopY);
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
		Point anchor,
		int stackAnchorY,
		int occupiedTopY)
	{
		if (actor != preparedActor || anchor == null || iconBuffer.isEmpty())
		{
			clearPrepared();
			return occupiedTopY;
		}

		int percent = clampScalePercent(config.headIconScalePercent());
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

	static int scaled(int dimension, int percent)
	{
		return Math.max(1, dimension * clampScalePercent(percent) / 100);
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
		return Math.floorDiv(coordinate * clampScalePercent(percent), 100);
	}

	static int scaledSpan(int offset, int dimension, int percent)
	{
		return Math.max(
			1,
			scaledEdge(offset + dimension, percent) - scaledEdge(offset, percent));
	}

	private static int clampScalePercent(int percent)
	{
		return Math.max(50, Math.min(200, percent));
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
