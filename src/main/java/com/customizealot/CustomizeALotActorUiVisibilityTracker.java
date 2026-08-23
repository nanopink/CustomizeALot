package com.customizealot;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Renderable;
import net.runelite.api.gameval.NpcID;
import net.runelite.client.callback.RenderCallback;
import net.runelite.client.callback.RenderCallbackManager;

/**
 * Records actors whose native 2D pass was accepted by every earlier render
 * listener. Ordinary actor UI is suppressed, while actors with secondary
 * gameplay bars retain the native pass. Registering this callback last lets
 * replacement UI respect actor hiders without reproducing their policy.
 */
@Singleton
final class CustomizeALotActorUiVisibilityTracker
{
	private final Client client;
	private final RenderCallbackManager renderCallbackManager;
	private final CustomizeALotStandardHealthSpriteSuppressor standardHealthSpriteSuppressor;
	private final Map<Actor, Integer> acceptedOnGameCycle = new ConcurrentHashMap<>();
	private final Map<Actor, Integer> nativeUiOnGameCycle = new ConcurrentHashMap<>();
	private final RenderCallback observer = new RenderCallback()
	{
		@Override
		public boolean addEntity(Renderable renderable, boolean ui)
		{
			boolean active = enabled;
			boolean actor = renderable instanceof Actor;
			boolean requiresNativeUi = active
				&& ui
				&& actor
				&& standardHealthSpriteSuppressor.isApplied()
				&& requiresNativeUi((Actor) renderable);
			if (active && ui && actor)
			{
				Actor acceptedActor = (Actor) renderable;
				Integer gameCycle = currentGameCycleValue();
				acceptedOnGameCycle.put(acceptedActor, gameCycle);
				if (requiresNativeUi)
				{
					nativeUiOnGameCycle.put(acceptedActor, gameCycle);
				}
				else
				{
					nativeUiOnGameCycle.remove(acceptedActor);
				}
			}
			if (shouldSuppressActorUi(active, ui, actor, requiresNativeUi))
			{
				return false;
			}

			return true;
		}
	};

	private volatile boolean enabled;
	private volatile int cachedGameCycle = Integer.MIN_VALUE;
	private volatile Integer cachedGameCycleValue = Integer.MIN_VALUE;

	@Inject
	CustomizeALotActorUiVisibilityTracker(
		Client client,
		RenderCallbackManager renderCallbackManager,
		CustomizeALotStandardHealthSpriteSuppressor standardHealthSpriteSuppressor)
	{
		this.client = client;
		this.renderCallbackManager = renderCallbackManager;
		this.standardHealthSpriteSuppressor = standardHealthSpriteSuppressor;
	}

	void enable()
	{
		if (!enabled)
		{
			renderCallbackManager.register(observer);
			enabled = true;
		}
	}

	void moveToEnd()
	{
		if (!enabled)
		{
			return;
		}

		renderCallbackManager.unregister(observer);
		renderCallbackManager.register(observer);
	}

	void disable()
	{
		if (enabled)
		{
			enabled = false;
			renderCallbackManager.unregister(observer);
		}
		clear();
	}

	void copyAcceptedInto(Collection<Actor> destination, int gameCycle)
	{
		if (destination == null)
		{
			return;
		}

		for (Map.Entry<Actor, Integer> entry : acceptedOnGameCycle.entrySet())
		{
			if (acceptedOn(entry.getValue(), gameCycle))
			{
				destination.add(entry.getKey());
			}
		}
	}

	void remove(Actor actor)
	{
		if (actor != null)
		{
			acceptedOnGameCycle.remove(actor);
			nativeUiOnGameCycle.remove(actor);
		}
	}

	boolean retainedNativeUi(Actor actor, int gameCycle)
	{
		return actor != null
			&& acceptedOn(nativeUiOnGameCycle.getOrDefault(actor, Integer.MIN_VALUE), gameCycle);
	}

	void discardBefore(int oldestGameCycle)
	{
		acceptedOnGameCycle.entrySet().removeIf(entry -> entry.getValue() < oldestGameCycle);
		nativeUiOnGameCycle.entrySet().removeIf(entry -> entry.getValue() < oldestGameCycle);
	}

	void clear()
	{
		acceptedOnGameCycle.clear();
		nativeUiOnGameCycle.clear();
		cachedGameCycleValue = Integer.MIN_VALUE;
		cachedGameCycle = Integer.MIN_VALUE;
	}

	private Integer currentGameCycleValue()
	{
		int gameCycle = client.getGameCycle();
		if (cachedGameCycle == gameCycle)
		{
			return cachedGameCycleValue;
		}

		synchronized (this)
		{
			if (cachedGameCycle != gameCycle)
			{
				cachedGameCycleValue = gameCycle;
				cachedGameCycle = gameCycle;
			}
			return cachedGameCycleValue;
		}
	}

	static boolean acceptedOn(int observedGameCycle, int gameCycle)
	{
		return observedGameCycle == gameCycle;
	}

	static boolean shouldSuppressActorUi(
		boolean enabled,
		boolean ui,
		boolean actor,
		boolean requiresNativeUi)
	{
		return enabled && ui && actor && !requiresNativeUi;
	}

	/**
	 * RuneLite exposes only an actor's primary health bar. The actors reported
	 * in issue #8 keep their complete native UI pass so their secondary bars
	 * remain accurate and native UI is not duplicated by replacements.
	 */
	static boolean requiresNativeUi(Actor actor)
	{
		return actor instanceof NPC && isSpecialBarCompatibilityNpc(((NPC) actor).getId());
	}

	static boolean isSpecialBarCompatibilityNpc(int npcId)
	{
		return usesDoomChargeBar(npcId)
			|| npcId == NpcID.MM_LAB_TABLE_BAR;
	}

	static boolean shouldRenderReplacementHealthWithNativeUi(Actor actor)
	{
		return actor instanceof NPC && usesDoomChargeBar(((NPC) actor).getId());
	}

	static boolean usesDoomChargeBar(int npcId)
	{
		return npcId == NpcID.DOM_BOSS
			|| npcId == NpcID.DOM_BOSS_SHIELDED
			|| npcId == NpcID.DOM_BOSS_BURROWED;
	}
}
