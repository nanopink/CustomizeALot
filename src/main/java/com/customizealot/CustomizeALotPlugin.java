package com.customizealot;

import com.google.inject.Provides;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Hitsplat;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.OverheadTextChanged;
import net.runelite.api.events.PlayerDespawned;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.ConfigProfile;
import net.runelite.client.config.FontType;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ChatboxInput;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.PluginChanged;
import net.runelite.client.events.ProfileChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(
	name = "Customize a Lot",
	configName = "hitsplatcustomizerplugin",
	description = "Customize hitsplats, health bars, overhead chat, and head icons for players and NPCs.",
	tags = {"hitsplat", "hitsplats", "hit", "splat", "damage", "heal", "healing", "combat", "customize", "layout", "filter", "healthbar", "chat", "prayer", "skull", "overhead", "icon"}
)
public class CustomizeALotPlugin extends Plugin
{
	private static final int GAME_CYCLE_MILLIS = 20;
	private static final double CONFIG_DOUBLE_SPINNER_STEP = 0.1;
	private static final double HEALTH_BAR_PIXEL_STEP = 1.0;
	private static final double CONFIG_STEP_EPSILON = 1.0e-6;
	private static final String LEGACY_TRIANGULAR_LAYOUT_MODE = "TRIANGULAR";
	private static final String CONFIG_GROUP_MIGRATION_VERSION = "1";
	private static final String PRESET_WORKFLOW_VERSION = "2";
	private static final String SECTION_PRESET_VERSION = "3";

	@Inject
	private CustomizeALotConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private CustomizeALotOverlay overlay;

	@Inject
	private CustomizeALotSettings settings;

	@Inject
	private CustomizeALotActorUiVisibilityTracker actorUiVisibilityTracker;

	@Inject
	private CustomizeALotLocalChatEffectTracker localChatEffectTracker;

	private final Map<Actor, CopyOnWriteArrayList<CustomizeALotHitsplat>> hitsplats = new ConcurrentHashMap<>();
	private final Map<Actor, TargetClassification> targetClassifications = new ConcurrentHashMap<>();
	private final AtomicLong customSelectionGeneration = new AtomicLong();
	private final AtomicLong healthBarCustomSelectionGeneration = new AtomicLong();
	private final AtomicLong overheadChatCustomSelectionGeneration = new AtomicLong();
	private final AtomicLong headIconCustomSelectionGeneration = new AtomicLong();

	private long nextSequence;
	private volatile boolean applyingPreset;
	private boolean migratingConfig;
	private volatile boolean adjustingHealthBarPixelStep;

	Map<Actor, CopyOnWriteArrayList<CustomizeALotHitsplat>> getHitsplats()
	{
		return hitsplats;
	}

	@Override
	protected void startUp()
	{
		cancelPendingCustomSelection();
		localChatEffectTracker.clear();
		migrateLegacyConfig();
		overlay.clearCaches();
		overlayManager.add(overlay);
		try
		{
			actorUiVisibilityTracker.enable();
		}
		catch (RuntimeException | Error ex)
		{
			actorUiVisibilityTracker.disable();
			overlayManager.remove(overlay);
			throw ex;
		}
	}

	@Override
	protected void shutDown()
	{
		cancelPendingCustomSelection();
		localChatEffectTracker.clear();
		try
		{
			actorUiVisibilityTracker.disable();
		}
		finally
		{
			overlayManager.remove(overlay);
			overlay.clearCaches();
			hitsplats.clear();
			targetClassifications.clear();
			nextSequence = 0;
		}
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		Actor actor = event.getActor();
		Hitsplat hitsplat = event.getHitsplat();
		if (actor == null || hitsplat == null)
		{
			return;
		}
		overlay.recordHealthBarDamage(actor, hitsplat.getHitsplatType(), hitsplat.getAmount());
		boolean mine = shouldTreatAsMine(hitsplat);
		int amount = hitsplat.getAmount();
		if (shouldDisableHitsplatsForActor(actor)
			|| settings.hideZeroHitsplats() && amount == 0
			|| settings.onlyDisplayMine() && !mine)
		{
			return;
		}

		int gameCycle = client.getGameCycle();
		CustomizeALotHitmarkDefinition definition = overlay.getDefinition(hitsplat.getHitsplatType());
		if (definition == null)
		{
			return;
		}
		int appearedOnGameCycle = CustomizeALotOverlay.nativeAppearanceGameCycle(
			definition,
			hitsplat.getDisappearsOnGameCycle(),
			gameCycle);

		addTrackedHitsplat(
			actor,
			hitsplat.getHitsplatType(),
			amount,
			mine,
			appearedOnGameCycle,
			gameCycle);
	}

	private CustomizeALotHitsplat addTrackedHitsplat(
		Actor actor,
		int hitsplatType,
		int amount,
		boolean mine,
		int appearedOnGameCycle,
		int gameCycle)
	{
		int fadeInCycles = fadeInCycles();
		int fullOpacityCycles = fullOpacityCycles();
		int fadeOutCycles = fadeOutCycles();
		int fullOpacityStartsOnGameCycle = appearedOnGameCycle + fadeInCycles;
		int fadeOutStartsOnGameCycle = fullOpacityStartsOnGameCycle + fullOpacityCycles;
		int expiresOnGameCycle = fadeOutStartsOnGameCycle + fadeOutCycles;
		if (expiresOnGameCycle <= gameCycle)
		{
			return null;
		}

		CopyOnWriteArrayList<CustomizeALotHitsplat> actorHitsplats = hitsplats.computeIfAbsent(actor, ignored -> new CopyOnWriteArrayList<>());
		boolean bossLike = isBossLikeTarget(actor);
		int minRadius = minRadiusFor(bossLike);
		int maxRadius = maxRadiusFor(bossLike);
		actorHitsplats.removeIf(activeHitsplat -> shouldRemoveFromTracking(
			actor,
			activeHitsplat,
			gameCycle,
			minRadius,
			maxRadius));
		int maxHitsplats = config.maxHitsplats();
		int position = config.reuseOldHitsplatSlots()
			? evictForTimedReuse(actorHitsplats, gameCycle, mine)
			: -1;
		if (position < 0)
		{
			position = getAvailablePosition(actor, actorHitsplats, maxHitsplats, gameCycle);
		}
		if (position < 0 && mine && settings.prioritizeMine())
		{
			position = evictForPrioritizedMine(actor, actorHitsplats, maxHitsplats, gameCycle);
		}

		if (position < 0)
		{
			return null;
		}
		CustomizeALotHitsplat trackedHitsplat = new CustomizeALotHitsplat(
				hitsplatType,
				amount,
				position,
				appearedOnGameCycle,
				reuseAgeStartsOnGameCycle(gameCycle, appearedOnGameCycle),
				fullOpacityStartsOnGameCycle,
				fadeOutStartsOnGameCycle,
				expiresOnGameCycle,
				nextSequence++,
				mine);
		actorHitsplats.add(trackedHitsplat);
		return trackedHitsplat;
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		cleanupExpiredHitsplats();
		actorUiVisibilityTracker.discardBefore(client.getGameCycle() - 1);
	}

	@Subscribe
	public void onPluginChanged(PluginChanged event)
	{
		if (event.getPlugin() != this)
		{
			// Stay behind actor-hiding callbacks so an accepted actor was accepted by all of them.
			actorUiVisibilityTracker.moveToEnd();
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!CustomizeALotConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}
		if (migratingConfig)
		{
			return;
		}

		if (CustomizeALotConfig.PRESET_KEY.equals(event.getKey()))
		{
			cancelPendingCustomSelection(customSelectionGeneration);
			CustomizeALotPreset preset = presetFromValue(event.getNewValue());
			if (preset != null && preset != CustomizeALotPreset.CUSTOM)
			{
				applyPreset(preset);
				scheduleHitsplatCleanup();
			}
			return;
		}
		if (CustomizeALotConfig.HEALTH_BAR_PRESET_KEY.equals(event.getKey()))
		{
			cancelPendingCustomSelection(healthBarCustomSelectionGeneration);
			CustomizeALotHealthBarPreset preset = healthBarPresetFromValue(event.getNewValue());
			if (preset != null && preset != CustomizeALotHealthBarPreset.CUSTOM)
			{
				applyHealthBarPreset(preset);
			}
			return;
		}
		if (CustomizeALotConfig.OVERHEAD_CHAT_PRESET_KEY.equals(event.getKey()))
		{
			cancelPendingCustomSelection(overheadChatCustomSelectionGeneration);
			CustomizeALotOverheadChatPreset preset = overheadChatPresetFromValue(event.getNewValue());
			if (preset != null && preset != CustomizeALotOverheadChatPreset.CUSTOM)
			{
				applyOverheadChatPreset(preset);
			}
			return;
		}
		if (CustomizeALotConfig.HEAD_ICON_PRESET_KEY.equals(event.getKey()))
		{
			cancelPendingCustomSelection(headIconCustomSelectionGeneration);
			CustomizeALotHeadIconPreset preset = headIconPresetFromValue(event.getNewValue());
			if (preset != null && preset != CustomizeALotHeadIconPreset.CUSTOM)
			{
				applyHeadIconPreset(preset);
			}
			return;
		}

		Number steppedHealthBarValue = null;
		if (isHealthBarPixelStepSetting(event.getKey()))
		{
			steppedHealthBarValue = steppedHealthBarPixelValue(
				applyingPreset
					|| adjustingHealthBarPixelStep
					|| !CustomizeALotConfigPanelSync.openSpinnerHasValue(
						event.getKey(),
						event.getNewValue()),
				event.getKey(),
				event.getOldValue(),
				event.getNewValue());
		}
		if (steppedHealthBarValue != null)
		{
			adjustingHealthBarPixelStep = true;
			try
			{
				configManager.setConfiguration(
					CustomizeALotConfig.GROUP,
					event.getKey(),
					steppedHealthBarValue);
				CustomizeALotConfigPanelSync.refreshOpenPanelNow(
					Collections.<String, Object>singletonMap(event.getKey(), steppedHealthBarValue));
			}
			finally
			{
				adjustingHealthBarPixelStep = false;
			}
		}

		if (shouldSwitchToCustom(applyingPreset, event.getKey(), config.preset()))
		{
			queueCustomSelection();
		}
		if (shouldSwitchHealthBarToCustom(
			applyingPreset,
			event.getKey(),
			config.healthBarPreset()))
		{
			queueCustomSelection(
				CustomizeALotConfig.HEALTH_BAR_PRESET_KEY,
				healthBarCustomSelectionGeneration);
		}
		if (shouldSwitchOverheadChatToCustom(
			applyingPreset,
			event.getKey(),
			config.overheadChatPreset()))
		{
			queueCustomSelection(
				CustomizeALotConfig.OVERHEAD_CHAT_PRESET_KEY,
				overheadChatCustomSelectionGeneration);
		}
		if (shouldSwitchHeadIconToCustom(
			applyingPreset,
			event.getKey(),
			config.headIconPreset()))
		{
			queueCustomSelection(
				CustomizeALotConfig.HEAD_ICON_PRESET_KEY,
				headIconCustomSelectionGeneration);
		}
		if (CustomizeALotConfig.HITSPLAT_FONT_KEY.equals(event.getKey())
			|| CustomizeALotConfig.HITSPLAT_SCALE_PERCENT_KEY.equals(event.getKey()))
		{
			clientThread.invokeLater(overlay::clearHitsplatLayoutCache);
		}
		if (CustomizeALotConfig.LARGE_TARGET_DETECTION_KEY.equals(event.getKey())
			|| CustomizeALotConfig.LARGE_TARGET_SIZE_KEY.equals(event.getKey())
			|| CustomizeALotConfig.LARGE_TARGET_HEALTH_SCALE_KEY.equals(event.getKey()))
		{
			targetClassifications.clear();
			scheduleHitsplatCleanup();
		}

		if (CustomizeALotConfig.MAX_HITSPLATS_KEY.equals(event.getKey())
			|| !applyingPreset && isPresetControlledSetting(event.getKey()))
		{
			scheduleHitsplatCleanup();
		}

	}

	@Subscribe
	public void onProfileChanged(ProfileChanged event)
	{
		cancelPendingCustomSelection();
		if (!applyingPreset)
		{
			migrateLegacyConfig();
			scheduleHitsplatCleanup();
		}
	}

	private void queueCustomSelection()
	{
		queueCustomSelection(CustomizeALotConfig.PRESET_KEY, customSelectionGeneration);
	}

	private void queueCustomSelection(String presetKey, AtomicLong selectionGeneration)
	{
		long generation;
		long configProfileId;
		synchronized (selectionGeneration)
		{
			generation = selectionGeneration.incrementAndGet();
			configProfileId = currentConfigProfileId();
		}
		SwingUtilities.invokeLater(() ->
		{
			synchronized (selectionGeneration)
			{
				if (configProfileId != currentConfigProfileId()
					|| !shouldApplyPendingCustomSelectionForNamedPreset(
						generation,
						selectionGeneration.get(),
						applyingPreset,
						isNamedPresetSelected(presetKey)))
				{
					return;
				}

				configManager.setConfiguration(
					CustomizeALotConfig.GROUP,
					presetKey,
					"CUSTOM");
				CustomizeALotConfigPanelSync.refreshOpenPanel(
					Collections.<String, Object>singletonMap(presetKey, "CUSTOM"));
			}
		});
	}

	private void cancelPendingCustomSelection()
	{
		cancelPendingCustomSelection(customSelectionGeneration);
		cancelPendingCustomSelection(healthBarCustomSelectionGeneration);
		cancelPendingCustomSelection(overheadChatCustomSelectionGeneration);
		cancelPendingCustomSelection(headIconCustomSelectionGeneration);
	}

	private static void cancelPendingCustomSelection(AtomicLong selectionGeneration)
	{
		synchronized (selectionGeneration)
		{
			selectionGeneration.incrementAndGet();
		}
	}

	private boolean isNamedPresetSelected(String presetKey)
	{
		if (CustomizeALotConfig.HEALTH_BAR_PRESET_KEY.equals(presetKey))
		{
			CustomizeALotHealthBarPreset preset = config.healthBarPreset();
			return preset != null && preset != CustomizeALotHealthBarPreset.CUSTOM;
		}
		if (CustomizeALotConfig.OVERHEAD_CHAT_PRESET_KEY.equals(presetKey))
		{
			CustomizeALotOverheadChatPreset preset = config.overheadChatPreset();
			return preset != null && preset != CustomizeALotOverheadChatPreset.CUSTOM;
		}
		if (CustomizeALotConfig.HEAD_ICON_PRESET_KEY.equals(presetKey))
		{
			CustomizeALotHeadIconPreset preset = config.headIconPreset();
			return preset != null && preset != CustomizeALotHeadIconPreset.CUSTOM;
		}
		CustomizeALotPreset preset = config.preset();
		return preset != null && preset != CustomizeALotPreset.CUSTOM;
	}

	private long currentConfigProfileId()
	{
		ConfigProfile profile = configManager.getProfile();
		return profile == null ? Long.MIN_VALUE : profile.getId();
	}

	static boolean shouldApplyPendingCustomSelection(
		long scheduledGeneration,
		long currentGeneration,
		boolean applyingPreset,
		CustomizeALotPreset selectedPreset)
	{
		return shouldApplyPendingCustomSelectionForNamedPreset(
			scheduledGeneration,
			currentGeneration,
			applyingPreset,
			selectedPreset != null && selectedPreset != CustomizeALotPreset.CUSTOM);
	}

	static boolean shouldApplyPendingCustomSelectionForNamedPreset(
		long scheduledGeneration,
		long currentGeneration,
		boolean applyingPreset,
		boolean namedPresetSelected)
	{
		return scheduledGeneration == currentGeneration
			&& !applyingPreset
			&& namedPresetSelected;
	}

	static Number steppedHealthBarPixelValue(
		boolean applyingPreset,
		String key,
		String oldValue,
		String newValue)
	{
		if (applyingPreset || key == null || oldValue == null || newValue == null)
		{
			return null;
		}

		if (CustomizeALotConfig.HEALTH_BAR_SOLID_WIDTH_KEY.equals(key)
			|| CustomizeALotConfig.HEALTH_BAR_HEIGHT_KEY.equals(key))
		{
			double oldNumber;
			double newNumber;
			try
			{
				oldNumber = Double.parseDouble(oldValue);
				newNumber = Double.parseDouble(newValue);
			}
			catch (NumberFormatException ex)
			{
				return null;
			}
			if (!Double.isFinite(oldNumber) || !Double.isFinite(newNumber))
			{
				return null;
			}

			double delta = newNumber - oldNumber;
			if (Math.abs(Math.abs(delta) - CONFIG_DOUBLE_SPINNER_STEP) > CONFIG_STEP_EPSILON)
			{
				return null;
			}
			double minimum = CustomizeALotConfig.HEALTH_BAR_SOLID_WIDTH_KEY.equals(key)
				? 10.0
				: 2.0;
			double maximum = CustomizeALotConfig.HEALTH_BAR_SOLID_WIDTH_KEY.equals(key)
				? 200.0
				: 20.0;
			return Math.max(
				minimum,
				Math.min(maximum, oldNumber + Math.copySign(HEALTH_BAR_PIXEL_STEP, delta)));
		}

		if (CustomizeALotConfig.HEALTH_BAR_X_OFFSET_KEY.equals(key)
			|| CustomizeALotConfig.HEALTH_BAR_Y_OFFSET_KEY.equals(key))
		{
			int oldNumber;
			int newNumber;
			try
			{
				oldNumber = Integer.parseInt(oldValue);
				newNumber = Integer.parseInt(newValue);
			}
			catch (NumberFormatException ex)
			{
				return null;
			}
			int delta = newNumber - oldNumber;
			if (Math.abs(delta) != 1)
			{
				return null;
			}
			return Math.max(-256, Math.min(256, oldNumber + Integer.signum(delta) * 2));
		}

		return null;
	}

	private static boolean isHealthBarPixelStepSetting(String key)
	{
		return CustomizeALotConfig.HEALTH_BAR_SOLID_WIDTH_KEY.equals(key)
			|| CustomizeALotConfig.HEALTH_BAR_HEIGHT_KEY.equals(key)
			|| CustomizeALotConfig.HEALTH_BAR_X_OFFSET_KEY.equals(key)
			|| CustomizeALotConfig.HEALTH_BAR_Y_OFFSET_KEY.equals(key);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		overlay.clearCaches();
		if (event.getGameState() != GameState.LOGGED_IN)
		{
			actorUiVisibilityTracker.clear();
			localChatEffectTracker.clear();
			hitsplats.clear();
			targetClassifications.clear();
			nextSequence = 0;
		}
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		NPC npc = event.getNpc();
		actorUiVisibilityTracker.remove(npc);
		localChatEffectTracker.remove(npc);
		overlay.removeActor(npc);
		hitsplats.remove(npc);
		targetClassifications.remove(npc);
	}

	@Subscribe
	public void onPlayerDespawned(PlayerDespawned event)
	{
		Player player = event.getPlayer();
		actorUiVisibilityTracker.remove(player);
		localChatEffectTracker.remove(player);
		overlay.removeActor(player);
		hitsplats.remove(player);
		targetClassifications.remove(player);
	}

	@Subscribe(priority = Float.NEGATIVE_INFINITY)
	public void onChatboxInput(ChatboxInput event)
	{
		localChatEffectTracker.recordOutgoing(
			event.getValue(),
			event.getChatType(),
			event.isConsumed(),
			client.getGameCycle());
	}

	@Subscribe
	public void onOverheadTextChanged(OverheadTextChanged event)
	{
		localChatEffectTracker.recordOverhead(
			event.getActor(),
			client.getLocalPlayer(),
			event.getOverheadText(),
			client.getGameCycle());
	}

	private void cleanupExpiredHitsplats()
	{
		int gameCycle = client.getGameCycle();
		hitsplats.entrySet().removeIf(entry ->
		{
			Actor actor = entry.getKey();
			boolean bossLike = isBossLikeTarget(actor);
			int minRadius = minRadiusFor(bossLike);
			int maxRadius = maxRadiusFor(bossLike);
			entry.getValue().removeIf(hitsplat -> shouldRemoveFromTracking(
				actor,
				hitsplat,
				gameCycle,
				minRadius,
				maxRadius));
			if (entry.getValue().isEmpty())
			{
				targetClassifications.remove(entry.getKey());
				return true;
			}
			return false;
		});
	}

	private void scheduleHitsplatCleanup()
	{
		clientThread.invokeLater(this::cleanupExpiredHitsplats);
	}

	private void migrateLegacyConfig()
	{
		boolean previouslyApplyingPreset = applyingPreset;
		boolean previouslyMigratingConfig = migratingConfig;
		applyingPreset = true;
		migratingConfig = true;
		try
		{
			boolean configGroupMigrationNeeded = migrateLegacyConfigGroup();
			migrateLegacyOpacityConfig();
			migrateLegacyLayoutConfig();
			migrateLegacySpacingConfig();
			removeLegacyGlobalOffsets();
			migrateLegacyOverheadChatConfig();
			migratePresetWorkflow();
			migrateSectionPresetWorkflows();
			if (configGroupMigrationNeeded)
			{
				configManager.setConfiguration(
					CustomizeALotConfig.GROUP,
					CustomizeALotConfig.CONFIG_GROUP_MIGRATION_VERSION_KEY,
					CONFIG_GROUP_MIGRATION_VERSION);
			}
		}
		finally
		{
			applyingPreset = previouslyApplyingPreset;
			migratingConfig = previouslyMigratingConfig;
			cancelPendingCustomSelection();
		}
	}

	private boolean migrateLegacyConfigGroup()
	{
		String migratedVersion = configManager.getConfiguration(
			CustomizeALotConfig.GROUP,
			CustomizeALotConfig.CONFIG_GROUP_MIGRATION_VERSION_KEY);
		if (CONFIG_GROUP_MIGRATION_VERSION.equals(migratedVersion))
		{
			return false;
		}

		copyConfigurationGroup(
			configManager.getConfigurationKeys(CustomizeALotConfig.LEGACY_GROUP + "."),
			CustomizeALotConfig.LEGACY_GROUP,
			CustomizeALotConfig.GROUP,
			configManager::getConfiguration,
			(key, value) -> configManager.setConfiguration(CustomizeALotConfig.GROUP, key, value));
		return true;
	}

	static void copyConfigurationGroup(
		List<String> wholeKeys,
		String sourceGroup,
		String destinationGroup,
		BiFunction<String, String, String> reader,
		BiConsumer<String, String> writer)
	{
		if (wholeKeys == null || sourceGroup == null || destinationGroup == null
			|| sourceGroup.equals(destinationGroup) || reader == null || writer == null)
		{
			return;
		}

		String prefix = sourceGroup + ".";
		for (String wholeKey : wholeKeys)
		{
			if (wholeKey == null || !wholeKey.startsWith(prefix))
			{
				continue;
			}

			String key = wholeKey.substring(prefix.length());
			if (key.isEmpty())
			{
				continue;
			}

			String value = reader.apply(sourceGroup, key);
			if (value != null)
			{
				writer.accept(key, value);
			}
		}
	}

	private void migrateSectionPresetWorkflows()
	{
		CustomizeALotHealthBarPreset healthBarPreset = config.healthBarPreset();
		CustomizeALotOverheadChatPreset overheadChatPreset = config.overheadChatPreset();
		String sectionPresetVersion = configManager.getConfiguration(
			CustomizeALotConfig.GROUP,
			CustomizeALotConfig.SECTION_PRESET_VERSION_KEY);
		boolean sectionPresetUpdateNeeded = !SECTION_PRESET_VERSION.equals(sectionPresetVersion);
		if (shouldRefreshUpdatedSectionPreset(
			sectionPresetUpdateNeeded,
			healthBarPreset == CustomizeALotHealthBarPreset.RUINED_HEIR,
			healthBarSettingsMatchConfig(
				CustomizeALotHealthBarPreset.version2RuinedHeirSettings(),
				config)
				|| healthBarSettingsMatchConfig(
					CustomizeALotHealthBarPreset.previousRuinedHeirSettings(),
					config)
				|| healthBarSettingsMatchConfig(
					CustomizeALotHealthBarPreset.legacyRuinedHeirSettings(),
					config)))
		{
			applyHealthBarPreset(healthBarPreset);
		}
		if (shouldRefreshUpdatedSectionPreset(
			sectionPresetUpdateNeeded,
			overheadChatPreset == CustomizeALotOverheadChatPreset.RUINED_HEIR,
			overheadChatSettingsMatchConfig(
				CustomizeALotOverheadChatPreset.previousRuinedHeirSettings(),
				config)
				|| overheadChatSettingsMatchConfig(
					CustomizeALotOverheadChatPreset.legacyRuinedHeirSettings(),
					config)))
		{
			applyOverheadChatPreset(overheadChatPreset);
		}
		if (sectionPresetUpdateNeeded)
		{
			configManager.setConfiguration(
				CustomizeALotConfig.GROUP,
				CustomizeALotConfig.SECTION_PRESET_VERSION_KEY,
				SECTION_PRESET_VERSION);
		}

		if (healthBarPreset != null
			&& healthBarPreset != CustomizeALotHealthBarPreset.CUSTOM
			&& !healthBarPresetMatchesConfig(healthBarPreset, config))
		{
			selectCustomSectionPreset(CustomizeALotConfig.HEALTH_BAR_PRESET_KEY);
		}

		if (overheadChatPreset != null
			&& overheadChatPreset != CustomizeALotOverheadChatPreset.CUSTOM
			&& !overheadChatPresetMatchesConfig(overheadChatPreset, config))
		{
			selectCustomSectionPreset(CustomizeALotConfig.OVERHEAD_CHAT_PRESET_KEY);
		}

		CustomizeALotHeadIconPreset headIconPreset = config.headIconPreset();
		if (headIconPreset != null
			&& headIconPreset != CustomizeALotHeadIconPreset.CUSTOM
			&& !headIconPresetMatchesConfig(headIconPreset, config))
		{
			selectCustomSectionPreset(CustomizeALotConfig.HEAD_ICON_PRESET_KEY);
		}
	}

	private void selectCustomSectionPreset(String presetKey)
	{
		configManager.setConfiguration(
			CustomizeALotConfig.GROUP,
			presetKey,
			"CUSTOM");
		CustomizeALotConfigPanelSync.refreshOpenPanel(
			Collections.<String, Object>singletonMap(presetKey, "CUSTOM"));
	}

	static boolean shouldRefreshUpdatedSectionPreset(
		boolean updateNeeded,
		boolean updatedPresetSelected,
		boolean previousSettingsMatch)
	{
		return updateNeeded && updatedPresetSelected && previousSettingsMatch;
	}

	private void migratePresetWorkflow()
	{
		String migratedVersion = configManager.getConfiguration(
			CustomizeALotConfig.GROUP,
			CustomizeALotConfig.PRESET_WORKFLOW_VERSION_KEY);
		boolean currentWorkflow = PRESET_WORKFLOW_VERSION.equals(migratedVersion);
		CustomizeALotPreset selectedPreset = config.preset();
		if (selectedPreset != null
			&& selectedPreset != CustomizeALotPreset.CUSTOM
			&& !presetMatchesConfig(selectedPreset, config))
		{
			if (!currentWorkflow && legacyPresetMatchesConfig(selectedPreset, config))
			{
				applyPreset(selectedPreset);
			}
			else
			{
				configManager.setConfiguration(
					CustomizeALotConfig.GROUP,
					CustomizeALotConfig.PRESET_KEY,
					CustomizeALotPreset.CUSTOM.name());
			}
		}

		if (!currentWorkflow)
		{
			configManager.setConfiguration(
				CustomizeALotConfig.GROUP,
				CustomizeALotConfig.PRESET_WORKFLOW_VERSION_KEY,
				PRESET_WORKFLOW_VERSION);
		}
	}

	private void migrateLegacyOverheadChatConfig()
	{
		String fontSize = configManager.getConfiguration(
			CustomizeALotConfig.GROUP,
			CustomizeALotConfig.LEGACY_OVERHEAD_CHAT_FONT_SIZE_KEY);
		if (fontSize != null
			&& configManager.getConfiguration(
				CustomizeALotConfig.GROUP,
				CustomizeALotConfig.OVERHEAD_CHAT_FONT_KEY) == null)
		{
			int size = 16;
			try
			{
				size = Math.max(8, Math.min(32, Integer.parseInt(fontSize)));
			}
			catch (NumberFormatException ignored)
			{
				// Keep the default size.
			}
			configManager.setConfiguration(
				CustomizeALotConfig.GROUP,
				CustomizeALotConfig.OVERHEAD_CHAT_FONT_KEY,
				FontType.BOLD.withSize(size));
		}

		configManager.unsetConfiguration(
			CustomizeALotConfig.GROUP,
			CustomizeALotConfig.LEGACY_CUSTOMIZE_OVERHEAD_CHAT_KEY);
		configManager.unsetConfiguration(
			CustomizeALotConfig.GROUP,
			CustomizeALotConfig.LEGACY_OVERHEAD_CHAT_FONT_SIZE_KEY);
	}

	private void migrateLegacyOpacityConfig()
	{
		String opacity = configManager.getConfiguration(CustomizeALotConfig.GROUP, CustomizeALotConfig.LEGACY_OPACITY_KEY);
		if (opacity == null)
		{
			return;
		}

		if (configManager.getConfiguration(
			CustomizeALotConfig.GROUP,
			CustomizeALotConfig.OPACITY_PERCENT_KEY) == null)
		{
			configManager.setConfiguration(
				CustomizeALotConfig.GROUP,
				CustomizeALotConfig.OPACITY_PERCENT_KEY,
				opacityPercent(opacity));
		}

		configManager.unsetConfiguration(CustomizeALotConfig.GROUP, CustomizeALotConfig.LEGACY_OPACITY_KEY);
	}

	private void migrateLegacyLayoutConfig()
	{
		String layoutMode = configManager.getConfiguration(CustomizeALotConfig.GROUP, CustomizeALotConfig.LEGACY_LAYOUT_MODE_KEY);
		String layoutStyle = configManager.getConfiguration(CustomizeALotConfig.GROUP, CustomizeALotConfig.LEGACY_LAYOUT_STYLE_KEY);
		if (layoutMode == null && layoutStyle == null)
		{
			return;
		}

		CustomizeALotLayoutShape layoutShape = null;
		CustomizeALotLayoutBehavior layoutBehavior = null;
		CustomizeALotLayoutDirection layoutDirection = null;
		if (layoutMode != null)
		{
			if ("SYMMETRIC".equals(layoutMode))
			{
				layoutShape = CustomizeALotLayoutShape.HEXAGONAL;
				layoutBehavior = CustomizeALotLayoutBehavior.SYMMETRIC;
			}
			else if ("COUNTERCLOCKWISE".equals(layoutMode))
			{
				layoutDirection = CustomizeALotLayoutDirection.COUNTERCLOCKWISE;
			}
			else if ("CLOCKWISE".equals(layoutMode) || LEGACY_TRIANGULAR_LAYOUT_MODE.equals(layoutMode))
			{
				layoutDirection = CustomizeALotLayoutDirection.CLOCKWISE;
			}
		}

		if (layoutStyle != null)
		{
			if ("DIAMOND".equals(layoutStyle))
			{
				layoutShape = CustomizeALotLayoutShape.DIAMOND;
				if (layoutBehavior == null)
				{
					layoutBehavior = CustomizeALotLayoutBehavior.INCREMENTAL;
				}
			}
			else if ("CIRCULAR".equals(layoutStyle))
			{
				layoutShape = CustomizeALotLayoutShape.HEXAGONAL;
				if (layoutBehavior == null)
				{
					layoutBehavior = CustomizeALotLayoutBehavior.INCREMENTAL;
				}
			}
			else if ("SYMMETRIC".equals(layoutStyle))
			{
				layoutShape = CustomizeALotLayoutShape.HEXAGONAL;
				layoutBehavior = CustomizeALotLayoutBehavior.SYMMETRIC;
			}
		}

		if (layoutShape != null
			&& configManager.getConfiguration(
				CustomizeALotConfig.GROUP,
				CustomizeALotConfig.LAYOUT_SHAPE_KEY) == null)
		{
			configManager.setConfiguration(CustomizeALotConfig.GROUP, CustomizeALotConfig.LAYOUT_SHAPE_KEY, layoutShape.name());
		}

		if (layoutBehavior != null
			&& configManager.getConfiguration(
				CustomizeALotConfig.GROUP,
				CustomizeALotConfig.LAYOUT_BEHAVIOR_KEY) == null)
		{
			configManager.setConfiguration(CustomizeALotConfig.GROUP, CustomizeALotConfig.LAYOUT_BEHAVIOR_KEY, layoutBehavior.name());
		}

		if (layoutDirection != null
			&& configManager.getConfiguration(
				CustomizeALotConfig.GROUP,
				CustomizeALotConfig.LAYOUT_DIRECTION_KEY) == null)
		{
			configManager.setConfiguration(CustomizeALotConfig.GROUP, CustomizeALotConfig.LAYOUT_DIRECTION_KEY, layoutDirection.name());
		}

		configManager.unsetConfiguration(CustomizeALotConfig.GROUP, CustomizeALotConfig.LEGACY_LAYOUT_MODE_KEY);
		configManager.unsetConfiguration(CustomizeALotConfig.GROUP, CustomizeALotConfig.LEGACY_LAYOUT_STYLE_KEY);
	}

	private void migrateLegacySpacingConfig()
	{
		String slotSpacing = configManager.getConfiguration(CustomizeALotConfig.GROUP, CustomizeALotConfig.LEGACY_SLOT_SPACING_KEY);
		String xSpacing = configManager.getConfiguration(CustomizeALotConfig.GROUP, CustomizeALotConfig.LEGACY_X_SPACING_KEY);
		String ySpacing = configManager.getConfiguration(CustomizeALotConfig.GROUP, CustomizeALotConfig.LEGACY_Y_SPACING_KEY);
		if (slotSpacing == null && xSpacing == null && ySpacing == null)
		{
			return;
		}

		if ((xSpacing != null || slotSpacing != null)
			&& configManager.getConfiguration(
				CustomizeALotConfig.GROUP,
				CustomizeALotConfig.X_SPACING_KEY) == null)
		{
			configManager.setConfiguration(
				CustomizeALotConfig.GROUP,
				CustomizeALotConfig.X_SPACING_KEY,
				roundedSpacing(xSpacing != null ? xSpacing : slotSpacing));
		}
		if ((ySpacing != null || slotSpacing != null)
			&& configManager.getConfiguration(
				CustomizeALotConfig.GROUP,
				CustomizeALotConfig.Y_SPACING_KEY) == null)
		{
			configManager.setConfiguration(
				CustomizeALotConfig.GROUP,
				CustomizeALotConfig.Y_SPACING_KEY,
				roundedSpacing(ySpacing != null ? ySpacing : slotSpacing));
		}

		configManager.unsetConfiguration(CustomizeALotConfig.GROUP, CustomizeALotConfig.LEGACY_SLOT_SPACING_KEY);
		configManager.unsetConfiguration(CustomizeALotConfig.GROUP, CustomizeALotConfig.LEGACY_X_SPACING_KEY);
		configManager.unsetConfiguration(CustomizeALotConfig.GROUP, CustomizeALotConfig.LEGACY_Y_SPACING_KEY);
	}

	private void removeLegacyGlobalOffsets()
	{
		configManager.unsetConfiguration(
			CustomizeALotConfig.GROUP,
			CustomizeALotConfig.LEGACY_GLOBAL_X_OFFSET_KEY);
		configManager.unsetConfiguration(
			CustomizeALotConfig.GROUP,
			CustomizeALotConfig.LEGACY_GLOBAL_Y_OFFSET_KEY);
	}

	private void applyPreset(CustomizeALotPreset preset)
	{
		Map<String, Object> presetSettings = new LinkedHashMap<>();
		applyPreset(preset, presetSettings::put);
		applySectionPreset(presetSettings);
	}

	private void applyHealthBarPreset(CustomizeALotHealthBarPreset preset)
	{
		applySectionPreset(preset.getSettings());
	}

	private void applyOverheadChatPreset(CustomizeALotOverheadChatPreset preset)
	{
		applySectionPreset(preset.getSettings());
	}

	private void applyHeadIconPreset(CustomizeALotHeadIconPreset preset)
	{
		applySectionPreset(preset.getSettings());
	}

	private void applySectionPreset(Map<String, Object> presetSettings)
	{
		boolean previouslyApplyingPreset = applyingPreset;
		applyingPreset = true;
		try
		{
			applySectionPreset(presetSettings, (key, value) ->
				configManager.setConfiguration(CustomizeALotConfig.GROUP, key, value));
			CustomizeALotConfigPanelSync.refreshOpenPanel(presetSettings);
		}
		finally
		{
			applyingPreset = previouslyApplyingPreset;
		}
	}

	static void applyPreset(CustomizeALotPreset preset, BiConsumer<String, Object> settingWriter)
	{
		settingWriter.accept(CustomizeALotConfig.ONLY_DISPLAY_MINE_KEY, preset.isOnlyDisplayMine());
		settingWriter.accept(CustomizeALotConfig.PRIORITIZE_MINE_KEY, preset.isPrioritizeMine());
		settingWriter.accept(CustomizeALotConfig.HIDE_ZERO_HITSPLATS_KEY, preset.isHideZeroHitsplats());
		settingWriter.accept(CustomizeALotConfig.LAYOUT_SHAPE_KEY, preset.getLayoutShape().name());
		settingWriter.accept(CustomizeALotConfig.LAYOUT_DIRECTION_KEY, preset.getLayoutDirection().name());
		settingWriter.accept(CustomizeALotConfig.LAYOUT_BEHAVIOR_KEY, preset.getLayoutBehavior().name());
		settingWriter.accept(CustomizeALotConfig.HITSPLAT_SCALE_PERCENT_KEY, preset.getHitsplatScalePercent());
		settingWriter.accept(CustomizeALotConfig.MIN_RADIUS_KEY, preset.getMinRadius());
		settingWriter.accept(CustomizeALotConfig.MAX_RADIUS_KEY, preset.getMaxRadius());
		settingWriter.accept(CustomizeALotConfig.X_SPACING_KEY, preset.getXSpacing());
		settingWriter.accept(CustomizeALotConfig.Y_SPACING_KEY, preset.getYSpacing());
		settingWriter.accept(CustomizeALotConfig.HITSPLAT_FONT_KEY, FontType.SMALL);
		settingWriter.accept(CustomizeALotConfig.FADE_IN_DURATION_KEY, preset.getFadeInDuration());
		settingWriter.accept(CustomizeALotConfig.FULL_OPACITY_DURATION_KEY, preset.getFullOpacityDuration());
		settingWriter.accept(CustomizeALotConfig.FADE_OUT_DURATION_KEY, preset.getFadeOutDuration());
	}

	static void applyHealthBarPreset(
		CustomizeALotHealthBarPreset preset,
		BiConsumer<String, Object> settingWriter)
	{
		applySectionPreset(preset == null ? null : preset.getSettings(), settingWriter);
	}

	static void applyOverheadChatPreset(
		CustomizeALotOverheadChatPreset preset,
		BiConsumer<String, Object> settingWriter)
	{
		applySectionPreset(preset == null ? null : preset.getSettings(), settingWriter);
	}

	static void applyHeadIconPreset(
		CustomizeALotHeadIconPreset preset,
		BiConsumer<String, Object> settingWriter)
	{
		applySectionPreset(preset == null ? null : preset.getSettings(), settingWriter);
	}

	private static void applySectionPreset(
		Map<String, Object> presetSettings,
		BiConsumer<String, Object> settingWriter)
	{
		if (presetSettings == null || settingWriter == null)
		{
			return;
		}
		presetSettings.forEach(settingWriter);
	}

	static boolean healthBarPresetMatchesConfig(
		CustomizeALotHealthBarPreset preset,
		CustomizeALotConfig config)
	{
		if (preset == null)
		{
			return false;
		}
		return healthBarSettingsMatchConfig(preset.getSettings(), config);
	}

	static boolean healthBarSettingsMatchConfig(
		Map<String, Object> expected,
		CustomizeALotConfig config)
	{
		if (expected == null || config == null)
		{
			return false;
		}
		Map<String, Object> values = new LinkedHashMap<>();
		values.put(CustomizeALotConfig.HEALTH_BAR_STYLE_KEY, config.healthBarStyle());
		values.put(CustomizeALotConfig.HEALTH_BAR_SCALE_MODE_KEY, config.healthBarScaleMode());
		values.put(CustomizeALotConfig.HEALTH_BAR_SCALE_PERCENT_KEY, config.healthBarScalePercent());
		values.put(CustomizeALotConfig.HEALTH_BAR_SCALE_THRESHOLD_KEY, config.healthBarScaleThreshold());
		values.put(CustomizeALotConfig.HEALTH_BAR_LARGE_SCALE_PERCENT_KEY, config.healthBarLargeScalePercent());
		values.put(CustomizeALotConfig.HEALTH_BAR_LARGE_HEIGHT_SCALE_PERCENT_KEY,
			config.healthBarLargeHeightScalePercent());
		values.put(CustomizeALotConfig.HEALTH_BAR_SOLID_WIDTH_KEY, config.healthBarSolidWidth());
		values.put(CustomizeALotConfig.HEALTH_BAR_HEIGHT_KEY, config.healthBarHeight());
		values.put(CustomizeALotConfig.HEALTH_BAR_X_OFFSET_KEY, config.healthBarXOffset());
		values.put(CustomizeALotConfig.HEALTH_BAR_Y_OFFSET_KEY, config.healthBarYOffset());
		values.put(CustomizeALotConfig.HEALTH_BAR_FILL_DIRECTION_KEY, config.healthBarFillDirection());
		values.put(CustomizeALotConfig.HEALTH_BAR_FRONT_GRADIENT_COORDINATES_KEY,
			config.healthBarFrontGradientCoordinates());
		values.put(CustomizeALotConfig.HEALTH_BAR_BACK_GRADIENT_COORDINATES_KEY,
			config.healthBarBackGradientCoordinates());
		values.put(CustomizeALotConfig.HEALTH_BAR_FRONT_GRADIENT_KEY, config.healthBarFrontGradient());
		values.put(CustomizeALotConfig.HEALTH_BAR_FRONT_COLOR_KEY, config.healthBarFrontColor());
		values.put(CustomizeALotConfig.HEALTH_BAR_FRONT_SECONDARY_COLOR_KEY,
			config.healthBarFrontSecondaryColor());
		values.put(CustomizeALotConfig.HEALTH_BAR_POISONED_FRONT_COLOR_KEY,
			config.healthBarPoisonedFrontColor());
		values.put(CustomizeALotConfig.HEALTH_BAR_BACK_GRADIENT_KEY, config.healthBarBackGradient());
		values.put(CustomizeALotConfig.HEALTH_BAR_BACK_COLOR_KEY, config.healthBarBackColor());
		values.put(CustomizeALotConfig.HEALTH_BAR_BACK_SECONDARY_COLOR_KEY,
			config.healthBarBackSecondaryColor());
		values.put(CustomizeALotConfig.HEALTH_BAR_DAMAGE_TRAIL_ENABLED_KEY,
			config.healthBarDamageTrailEnabled());
		values.put(CustomizeALotConfig.HEALTH_BAR_DAMAGE_TRAIL_COLOR_KEY,
			config.healthBarDamageTrailColor());
		values.put(CustomizeALotConfig.HEALTH_BAR_DAMAGE_TRAIL_HOLD_KEY,
			config.healthBarDamageTrailHold());
		values.put(CustomizeALotConfig.HEALTH_BAR_DAMAGE_TRAIL_DRAIN_KEY,
			config.healthBarDamageTrailDrain());
		values.put(CustomizeALotConfig.HEALTH_BAR_SEGMENTS_ENABLED_KEY,
			config.healthBarSegmentsEnabled());
		values.put(CustomizeALotConfig.HEALTH_BAR_SEGMENT_VALUE_MODE_KEY,
			config.healthBarSegmentValueMode());
		values.put(CustomizeALotConfig.HEALTH_BAR_HITPOINTS_PER_SEGMENT_KEY,
			config.healthBarHitpointsPerSegment());
		values.put(CustomizeALotConfig.HEALTH_BAR_SEGMENT_COLOR_KEY,
			config.healthBarSegmentColor());
		values.put(CustomizeALotConfig.HEALTH_BAR_SEGMENT_THICKNESS_KEY,
			config.healthBarSegmentThickness());
		values.put(CustomizeALotConfig.HEALTH_BAR_BORDER_COLOR_KEY,
			config.healthBarBorderColor());
		values.put(CustomizeALotConfig.HEALTH_BAR_BORDER_THICKNESS_KEY,
			config.healthBarBorderThickness());
		values.put(CustomizeALotConfig.HEALTH_BAR_CORNER_RADIUS_KEY,
			config.healthBarCornerRadius());
		return presetSettingsMatch(expected, values);
	}

	static boolean overheadChatPresetMatchesConfig(
		CustomizeALotOverheadChatPreset preset,
		CustomizeALotConfig config)
	{
		if (preset == null)
		{
			return false;
		}
		return overheadChatSettingsMatchConfig(preset.getSettings(), config);
	}

	static boolean overheadChatSettingsMatchConfig(
		Map<String, Object> expected,
		CustomizeALotConfig config)
	{
		if (expected == null || config == null)
		{
			return false;
		}
		Map<String, Object> values = new LinkedHashMap<>();
		values.put(CustomizeALotConfig.SHOW_NPC_OVERHEAD_CHAT_KEY, config.showNpcOverheadChat());
		values.put(CustomizeALotConfig.OVERHEAD_CHAT_FONT_KEY, config.overheadChatFont());
		values.put(CustomizeALotConfig.OVERHEAD_CHAT_COLOR_KEY, config.overheadChatColor());
		values.put(CustomizeALotConfig.OVERHEAD_CHAT_RELATIONSHIP_COLORS_KEY,
			config.overheadChatRelationshipColors());
		values.put(CustomizeALotConfig.OVERHEAD_CHAT_FRIEND_COLOR_KEY,
			config.overheadChatFriendColor());
		values.put(CustomizeALotConfig.OVERHEAD_CHAT_CLAN_COLOR_KEY,
			config.overheadChatClanColor());
		values.put(CustomizeALotConfig.OVERHEAD_CHAT_GROUP_IRON_COLOR_KEY,
			config.overheadChatGroupIronColor());
		values.put(CustomizeALotConfig.OVERHEAD_CHAT_EFFECT_KEY, config.overheadChatEffect());
		values.put(CustomizeALotConfig.OVERHEAD_CHAT_SHADOW_KEY, config.overheadChatShadow());
		values.put(CustomizeALotConfig.OVERHEAD_CHAT_SHADOW_COLOR_KEY,
			config.overheadChatShadowColor());
		values.put(CustomizeALotConfig.OVERHEAD_CHAT_X_OFFSET_KEY, config.overheadChatXOffset());
		values.put(CustomizeALotConfig.OVERHEAD_CHAT_Y_OFFSET_KEY, config.overheadChatYOffset());
		return presetSettingsMatch(expected, values);
	}

	static boolean headIconPresetMatchesConfig(
		CustomizeALotHeadIconPreset preset,
		CustomizeALotConfig config)
	{
		if (preset == null || config == null)
		{
			return false;
		}

		Map<String, Object> values = new LinkedHashMap<>();
		values.put(CustomizeALotConfig.SHOW_PRAYER_ICONS_KEY, config.showPrayerIcons());
		values.put(CustomizeALotConfig.SHOW_SKULL_ICONS_KEY, config.showSkullIcons());
		values.put(CustomizeALotConfig.SHOW_NPC_ICONS_KEY, config.showNpcIcons());
		values.put(CustomizeALotConfig.SHOW_HINT_ARROWS_KEY, config.showHintArrows());
		values.put(CustomizeALotConfig.HEAD_ICON_SCALE_PERCENT_KEY, config.headIconScalePercent());
		values.put(CustomizeALotConfig.HEAD_ICON_X_OFFSET_KEY, config.headIconXOffset());
		values.put(CustomizeALotConfig.HEAD_ICON_Y_OFFSET_KEY, config.headIconYOffset());
		values.put(CustomizeALotConfig.HEAD_ICON_SPACING_KEY, config.headIconSpacing());
		return presetSettingsMatch(preset.getSettings(), values);
	}

	private static boolean presetSettingsMatch(
		Map<String, Object> expected,
		Map<String, Object> actual)
	{
		if (expected.size() != actual.size())
		{
			return false;
		}
		for (Map.Entry<String, Object> entry : expected.entrySet())
		{
			if (!presetSettingEquals(entry.getValue(), actual.get(entry.getKey())))
			{
				return false;
			}
		}
		return true;
	}

	private static boolean presetSettingEquals(Object expected, Object actual)
	{
		if (expected instanceof FontType || actual instanceof FontType)
		{
			return expected instanceof FontType
				&& actual instanceof FontType
				&& sameFont((FontType) expected, (FontType) actual);
		}
		if (expected instanceof Number && actual instanceof Number)
		{
			return Double.compare(
				((Number) expected).doubleValue(),
				((Number) actual).doubleValue()) == 0;
		}
		return Objects.equals(expected, actual);
	}

	static boolean presetMatchesConfig(
		CustomizeALotPreset preset,
		CustomizeALotConfig config)
	{
		return preset != null
			&& presetMatchesConfig(preset, config, preset.getMinRadius());
	}

	static boolean legacyPresetMatchesConfig(
		CustomizeALotPreset preset,
		CustomizeALotConfig config)
	{
		return preset == CustomizeALotPreset.CHAOS
			&& presetMatchesConfig(preset, config, 0);
	}

	private static boolean presetMatchesConfig(
		CustomizeALotPreset preset,
		CustomizeALotConfig config,
		int expectedMinRadius)
	{
		return config != null
			&& config.onlyDisplayMine() == preset.isOnlyDisplayMine()
			&& config.prioritizeMine() == preset.isPrioritizeMine()
			&& config.hideZeroHitsplats() == preset.isHideZeroHitsplats()
			&& config.layoutShape() == preset.getLayoutShape()
			&& config.layoutDirection() == preset.getLayoutDirection()
			&& config.layoutBehavior() == preset.getLayoutBehavior()
			&& config.hitsplatScalePercent() == preset.getHitsplatScalePercent()
			&& config.minRadius() == expectedMinRadius
			&& config.maxRadius() == preset.getMaxRadius()
			&& config.xSpacing() == preset.getXSpacing()
			&& config.ySpacing() == preset.getYSpacing()
			&& sameFont(config.hitsplatFont(), FontType.SMALL)
			&& config.fadeInDuration() == preset.getFadeInDuration()
			&& config.fullOpacityDuration() == preset.getFullOpacityDuration()
			&& config.fadeOutDuration() == preset.getFadeOutDuration();
	}

	private static boolean sameFont(FontType first, FontType second)
	{
		return first == second
			|| first != null
				&& second != null
				&& Objects.equals(first.getFamily(), second.getFamily())
				&& first.getSize() == second.getSize()
				&& first.isBold() == second.isBold()
				&& first.isItalic() == second.isItalic();
	}

	static CustomizeALotPreset presetFromValue(String value)
	{
		return sectionPresetFromValue(value, CustomizeALotPreset.values());
	}

	static CustomizeALotHealthBarPreset healthBarPresetFromValue(String value)
	{
		return sectionPresetFromValue(value, CustomizeALotHealthBarPreset.values());
	}

	static CustomizeALotOverheadChatPreset overheadChatPresetFromValue(String value)
	{
		return sectionPresetFromValue(value, CustomizeALotOverheadChatPreset.values());
	}

	static CustomizeALotHeadIconPreset headIconPresetFromValue(String value)
	{
		return sectionPresetFromValue(value, CustomizeALotHeadIconPreset.values());
	}

	private static <T extends Enum<T>> T sectionPresetFromValue(String value, T[] presets)
	{
		if (value == null || presets.length == 0)
		{
			return null;
		}
		for (T preset : presets)
		{
			if (preset.toString().equals(value))
			{
				return preset;
			}
		}

		try
		{
			return Enum.valueOf(presets[0].getDeclaringClass(), value);
		}
		catch (IllegalArgumentException ex)
		{
			return null;
		}
	}

	static boolean shouldSwitchToCustom(
		boolean applyingPreset,
		String key,
		CustomizeALotPreset selectedPreset)
	{
		return !applyingPreset
			&& isPresetControlledSetting(key)
			&& selectedPreset != null
			&& selectedPreset != CustomizeALotPreset.CUSTOM;
	}

	static boolean shouldSwitchHealthBarToCustom(
		boolean applyingPreset,
		String key,
		CustomizeALotHealthBarPreset selectedPreset)
	{
		return !applyingPreset
			&& isHealthBarPresetControlledSetting(key)
			&& selectedPreset != null
			&& selectedPreset != CustomizeALotHealthBarPreset.CUSTOM;
	}

	static boolean shouldSwitchOverheadChatToCustom(
		boolean applyingPreset,
		String key,
		CustomizeALotOverheadChatPreset selectedPreset)
	{
		return !applyingPreset
			&& isOverheadChatPresetControlledSetting(key)
			&& selectedPreset != null
			&& selectedPreset != CustomizeALotOverheadChatPreset.CUSTOM;
	}

	static boolean shouldSwitchHeadIconToCustom(
		boolean applyingPreset,
		String key,
		CustomizeALotHeadIconPreset selectedPreset)
	{
		return !applyingPreset
			&& isHeadIconPresetControlledSetting(key)
			&& selectedPreset != null
			&& selectedPreset != CustomizeALotHeadIconPreset.CUSTOM;
	}

	static boolean isPresetControlledSetting(String key)
	{
		return CustomizeALotConfig.ONLY_DISPLAY_MINE_KEY.equals(key)
			|| CustomizeALotConfig.PRIORITIZE_MINE_KEY.equals(key)
			|| CustomizeALotConfig.HIDE_ZERO_HITSPLATS_KEY.equals(key)
			|| CustomizeALotConfig.LAYOUT_SHAPE_KEY.equals(key)
			|| CustomizeALotConfig.LAYOUT_DIRECTION_KEY.equals(key)
			|| CustomizeALotConfig.LAYOUT_BEHAVIOR_KEY.equals(key)
			|| CustomizeALotConfig.HITSPLAT_SCALE_PERCENT_KEY.equals(key)
			|| CustomizeALotConfig.MIN_RADIUS_KEY.equals(key)
			|| CustomizeALotConfig.MAX_RADIUS_KEY.equals(key)
			|| CustomizeALotConfig.X_SPACING_KEY.equals(key)
			|| CustomizeALotConfig.Y_SPACING_KEY.equals(key)
			|| CustomizeALotConfig.HITSPLAT_FONT_KEY.equals(key)
			|| CustomizeALotConfig.FADE_IN_DURATION_KEY.equals(key)
			|| CustomizeALotConfig.FULL_OPACITY_DURATION_KEY.equals(key)
			|| CustomizeALotConfig.FADE_OUT_DURATION_KEY.equals(key);
	}

	static boolean isHealthBarPresetControlledSetting(String key)
	{
		return key != null && CustomizeALotHealthBarPreset.DEFAULT.getSettings().containsKey(key);
	}

	static boolean isOverheadChatPresetControlledSetting(String key)
	{
		return key != null && CustomizeALotOverheadChatPreset.DEFAULT.getSettings().containsKey(key);
	}

	static boolean isHeadIconPresetControlledSetting(String key)
	{
		return key != null && CustomizeALotHeadIconPreset.DEFAULT.getSettings().containsKey(key);
	}

	private static int roundedSpacing(String value)
	{
		if (value == null)
		{
			return 0;
		}

		try
		{
			return Math.max(-64, Math.min(64, (int) Math.round(Double.parseDouble(value))));
		}
		catch (NumberFormatException ex)
		{
			return 0;
		}
	}

	static int opacityPercent(String value)
	{
		if (value == null)
		{
			return 100;
		}

		try
		{
			double opacity = Double.parseDouble(value);
			if (Double.isNaN(opacity) || Double.isInfinite(opacity))
			{
				return 100;
			}

			double percent = opacity <= 1.0 ? opacity * 100.0 : opacity;
			return Math.max(0, Math.min(100, (int) Math.round(percent)));
		}
		catch (NumberFormatException ex)
		{
			return 100;
		}
	}

	private int getAvailablePosition(
		Actor actor,
		CopyOnWriteArrayList<CustomizeALotHitsplat> actorHitsplats,
		int maxHitsplats,
		int gameCycle)
	{
		if (settings.layoutBehavior() == CustomizeALotLayoutBehavior.RANDOM)
		{
			return getRandomAvailablePosition(actor, actorHitsplats, maxHitsplats, gameCycle);
		}

		return getLowestAvailablePosition(actor, actorHitsplats, maxHitsplats, gameCycle);
	}

	private int getLowestAvailablePosition(
		Actor actor,
		CopyOnWriteArrayList<CustomizeALotHitsplat> actorHitsplats,
		int maxHitsplats,
		int gameCycle)
	{
		boolean bossLike = isBossLikeTarget(actor);
		int limit = positionLimit(actorHitsplats.size(), maxHitsplats, bossLike);
		for (int position = 0; position < limit; position++)
		{
			if (!isPositionTaken(actorHitsplats, position, gameCycle))
			{
				return position;
			}
		}

		return -1;
	}

	private int getRandomAvailablePosition(
		Actor actor,
		CopyOnWriteArrayList<CustomizeALotHitsplat> actorHitsplats,
		int maxHitsplats,
		int gameCycle)
	{
		boolean bossLike = isBossLikeTarget(actor);
		int limit = hardPositionLimit(maxHitsplats, bossLike);
		int minRadius = minRadiusFor(bossLike);
		for (int position = 0; position < limit; )
		{
			int radius = CustomizeALotLayout.radiusForPosition(position, minRadius, settings.layoutShape());
			List<Integer> availablePositions = new ArrayList<>();
			while (position < limit && CustomizeALotLayout.radiusForPosition(position, minRadius, settings.layoutShape()) == radius)
			{
				if (!isPositionTaken(actorHitsplats, position, gameCycle))
				{
					availablePositions.add(position);
				}
				position++;
			}

			if (!availablePositions.isEmpty())
			{
				return availablePositions.get(ThreadLocalRandom.current().nextInt(availablePositions.size()));
			}
		}

		return -1;
	}

	private static boolean isPositionTaken(CopyOnWriteArrayList<CustomizeALotHitsplat> actorHitsplats, int position, int gameCycle)
	{
		for (CustomizeALotHitsplat hitsplat : actorHitsplats)
		{
			if (!hitsplat.isExpired(gameCycle) && hitsplat.getPosition() == position)
			{
				return true;
			}
		}

		return false;
	}

	static boolean shouldTreatAsMine(Hitsplat hitsplat)
	{
		return hitsplat.isMine();
	}

	private int evictForTimedReuse(
		CopyOnWriteArrayList<CustomizeALotHitsplat> actorHitsplats,
		int gameCycle,
		boolean mine)
	{
		CustomizeALotHitsplat victim = findTimedReuseVictim(
			actorHitsplats,
			gameCycle,
			reuseWindowCycles(config.hitsplatReuseInterval()),
			mine,
			settings.prioritizeMine());
		if (victim == null || !actorHitsplats.remove(victim))
		{
			return -1;
		}

		return victim.getPosition();
	}

	static CustomizeALotHitsplat findTimedReuseVictim(
		List<CustomizeALotHitsplat> actorHitsplats,
		int gameCycle,
		int minimumAgeCycles,
		boolean incomingMine,
		boolean prioritizeMine)
	{
		if (incomingMine && prioritizeMine)
		{
			CustomizeALotHitsplat nonMine = findOldestReusableHitsplat(
				actorHitsplats,
				gameCycle,
				minimumAgeCycles,
				false);
			if (nonMine != null)
			{
				return nonMine;
			}
		}

		return findOldestReusableHitsplat(
			actorHitsplats,
			gameCycle,
			minimumAgeCycles,
			true);
	}

	private static CustomizeALotHitsplat findOldestReusableHitsplat(
		List<CustomizeALotHitsplat> actorHitsplats,
		int gameCycle,
		int minimumAgeCycles,
		boolean includeMine)
	{
		CustomizeALotHitsplat oldest = null;
		for (CustomizeALotHitsplat hitsplat : actorHitsplats)
		{
			long age = (long) gameCycle - hitsplat.getReuseAgeStartsOnGameCycle();
			if (age < Math.max(1, minimumAgeCycles)
				|| !includeMine && hitsplat.isMine())
			{
				continue;
			}

			if (oldest == null
				|| hitsplat.getReuseAgeStartsOnGameCycle() < oldest.getReuseAgeStartsOnGameCycle()
				|| hitsplat.getReuseAgeStartsOnGameCycle() == oldest.getReuseAgeStartsOnGameCycle()
					&& hitsplat.getSequence() < oldest.getSequence())
			{
				oldest = hitsplat;
			}
		}

		return oldest;
	}

	static int reuseWindowCycles(int milliseconds)
	{
		long clampedMilliseconds = Math.max(0L, (long) milliseconds);
		long cycles = (clampedMilliseconds + GAME_CYCLE_MILLIS - 1L) / GAME_CYCLE_MILLIS;
		return (int) Math.min(Integer.MAX_VALUE, Math.max(1L, cycles));
	}

	static int reuseAgeStartsOnGameCycle(int eventGameCycle, int appearedOnGameCycle)
	{
		return Math.max(eventGameCycle, appearedOnGameCycle);
	}

	private int evictForPrioritizedMine(Actor actor, CopyOnWriteArrayList<CustomizeALotHitsplat> actorHitsplats, int maxHitsplats, int gameCycle)
	{
		CustomizeALotHitsplat victim = findOldestEvictableHitsplat(actor, actorHitsplats, maxHitsplats, gameCycle, false);
		if (victim == null)
		{
			victim = findOldestEvictableHitsplat(actor, actorHitsplats, maxHitsplats, gameCycle, true);
		}

		if (victim == null)
		{
			return -1;
		}

		actorHitsplats.remove(victim);
		return victim.getPosition();
	}

	private CustomizeALotHitsplat findOldestEvictableHitsplat(
		Actor actor,
		CopyOnWriteArrayList<CustomizeALotHitsplat> actorHitsplats,
		int maxHitsplats,
		int gameCycle,
		boolean includeMine)
	{
		CustomizeALotHitsplat oldest = null;
		boolean bossLike = isBossLikeTarget(actor);
		int minRadius = minRadiusFor(bossLike);
		int maxRadius = maxRadiusFor(bossLike);
		for (CustomizeALotHitsplat hitsplat : actorHitsplats)
		{
			if (shouldRemoveFromTracking(
				actor,
				hitsplat,
				gameCycle,
				minRadius,
				maxRadius)
				|| maxHitsplats > 0 && hitsplat.getPosition() >= maxHitsplats
				|| !includeMine && hitsplat.isMine())
			{
				continue;
			}

			if (oldest == null || hitsplat.getSequence() < oldest.getSequence())
			{
				oldest = hitsplat;
			}
		}

		return oldest;
	}

	private boolean shouldRemoveFromTracking(
		Actor actor,
		CustomizeALotHitsplat hitsplat,
		int gameCycle,
		int minRadius,
		int maxRadius)
	{
		return hitsplat.isExpired(gameCycle)
			|| hitsplat.isOutsidePositionLimit(config.maxHitsplats())
			|| shouldDisableHitsplatsForActor(actor)
			|| settings.hideZeroHitsplats() && hitsplat.getAmount() == 0
			|| settings.onlyDisplayMine() && !hitsplat.isMine()
			|| !CustomizeALotLayout.isPositionWithinRadiusLimit(
				hitsplat.getPosition(),
				minRadius,
				maxRadius,
				settings.layoutShape());
	}

	boolean shouldDisableHitsplatsForActor(Actor actor)
	{
		Player localPlayer = client.getLocalPlayer();
		if (actor == localPlayer)
		{
			return config.disableMyHitsplats();
		}

		if (actor instanceof NPC)
		{
			return config.disableEnemyHitsplats();
		}

		if (actor instanceof Player)
		{
			return config.disableAllyHitsplats();
		}

		return false;
	}

	boolean isBossLikeTarget(Actor actor)
	{
		if (actor == null)
		{
			return false;
		}

		int footprintSize = actor.getFootprintSize();
		int publicHealthScale = actor.getHealthScale();
		int actorDefinitionId = actor instanceof NPC ? ((NPC) actor).getId() : -1;
		TargetClassification cached = targetClassifications.get(actor);
		if (cached != null
			&& canReuseTargetClassification(
				cached.footprintSize,
				cached.publicHealthScale,
				cached.actorDefinitionId,
				footprintSize,
				publicHealthScale,
				actorDefinitionId))
		{
			return cached.bossLike;
		}

		boolean detected = detectsBossLikeTarget(
			config.largeTargetDetection(),
			footprintSize,
			publicHealthScale,
			config.largeTargetSize(),
			config.largeTargetHealthScale());
		targetClassifications.put(actor, new TargetClassification(
			footprintSize,
			publicHealthScale,
			actorDefinitionId,
			detected));
		return detected;
	}

	static boolean canReuseTargetClassification(
		int cachedFootprintSize,
		int cachedPublicHealthScale,
		int cachedActorDefinitionId,
		int footprintSize,
		int publicHealthScale,
		int actorDefinitionId)
	{
		return cachedFootprintSize == footprintSize
			&& cachedActorDefinitionId == actorDefinitionId
			&& (publicHealthScale <= 0 || cachedPublicHealthScale == publicHealthScale);
	}

	int minRadiusFor(boolean bossLike)
	{
		return bossLike ? settings.minRadius() : 0;
	}

	int maxRadiusFor(boolean bossLike)
	{
		return bossLike ? settings.maxRadius() : 0;
	}

	static boolean detectsBossLikeTarget(
		CustomizeALotTargetDetection detection,
		int footprintSize,
		int publicHealthScale,
		int footprintThreshold,
		int healthScaleThreshold)
	{
		boolean footprintMatch = footprintSize >= Math.max(1, footprintThreshold);
		boolean healthScaleMatch = publicHealthScale >= Math.max(100, healthScaleThreshold);
		CustomizeALotTargetDetection effectiveDetection = detection == null
			? CustomizeALotTargetDetection.EITHER
			: detection;
		switch (effectiveDetection)
		{
			case FOOTPRINT:
				return footprintMatch;
			case HEALTH_SCALE:
				return healthScaleMatch;
			case EITHER:
			default:
				return footprintMatch || healthScaleMatch;
		}
	}

	private int positionLimit(int activeHitsplatCount, int maxHitsplats, boolean bossLike)
	{
		int limit = maxHitsplats <= 0 ? activeHitsplatCount + 1 : maxHitsplats;
		int radiusLimit = CustomizeALotLayout.slotLimit(
			minRadiusFor(bossLike),
			maxRadiusFor(bossLike),
			settings.layoutShape());
		if (radiusLimit != Integer.MAX_VALUE)
		{
			limit = Math.min(limit, radiusLimit);
		}

		return Math.max(0, limit);
	}

	private int hardPositionLimit(int maxHitsplats, boolean bossLike)
	{
		int limit = maxHitsplats <= 0 ? Integer.MAX_VALUE : maxHitsplats;
		int radiusLimit = CustomizeALotLayout.slotLimit(
			minRadiusFor(bossLike),
			maxRadiusFor(bossLike),
			settings.layoutShape());
		if (radiusLimit != Integer.MAX_VALUE)
		{
			limit = Math.min(limit, radiusLimit);
		}

		return Math.max(0, limit);
	}

	private int fadeInCycles()
	{
		return Math.max(0, settings.fadeInDuration() / GAME_CYCLE_MILLIS);
	}

	private int fullOpacityCycles()
	{
		return Math.max(0, settings.fullOpacityDuration() / GAME_CYCLE_MILLIS);
	}

	private int fadeOutCycles()
	{
		return Math.max(0, settings.fadeOutDuration() / GAME_CYCLE_MILLIS);
	}

	private static final class TargetClassification
	{
		private final int footprintSize;
		private final int publicHealthScale;
		private final int actorDefinitionId;
		private final boolean bossLike;

		private TargetClassification(
			int footprintSize,
			int publicHealthScale,
			int actorDefinitionId,
			boolean bossLike)
		{
			this.footprintSize = footprintSize;
			this.publicHealthScale = publicHealthScale;
			this.actorDefinitionId = actorDefinitionId;
			this.bossLike = bossLike;
		}
	}

	@Provides
	CustomizeALotConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(CustomizeALotConfig.class);
	}
}
