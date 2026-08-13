package com.customizealot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import net.runelite.client.ui.components.ColorJButton;
import org.junit.Test;

public class CustomizeALotConfigPanelSyncTest
{
	@Test
	public void synchronizesEachControlTypeInsideTheRequestedSection()
	{
		JPanel root = configPanel("Customize a Lot");
		JPanel hitsplats = section(root, "Hitsplats");
		JPanel healthBars = section(root, "Health bars");
		section(root, "Overhead chat");
		JPanel headIcons = section(root, "Head icons");

		JComboBox<CustomizeALotPreset> hitsplatPreset = new JComboBox<>(CustomizeALotPreset.values());
		hitsplatPreset.setSelectedItem(CustomizeALotPreset.RUNESCAPE);
		row(hitsplats, "Preset", hitsplatPreset);
		JSpinner hitsplatScale = intSpinner(100);
		row(hitsplats, "Scale", hitsplatScale);

		JSpinner healthScale = intSpinner(100);
		row(healthBars, "Scale", healthScale);
		JSpinner bossWidthScale = intSpinner(150);
		row(healthBars, "Boss width scale", bossWidthScale);
		JSpinner bossHeightScale = intSpinner(100);
		row(healthBars, "Boss height scale", bossHeightScale);
		JSpinner healthWidth = doubleSpinner(30.0);
		row(healthBars, "Width", healthWidth);
		JCheckBox segments = new JCheckBox();
		row(healthBars, "HP segments", segments);
		JComboBox<CustomizeALotHealthBarGradient> gradient =
			new JComboBox<>(CustomizeALotHealthBarGradient.values());
		row(healthBars, "Front gradient", gradient);
		ColorJButton frontColor = new ColorJButton("#000000FF", Color.BLACK);
		row(healthBars, "Front color", frontColor);

		JSpinner headIconScale = intSpinner(100);
		row(headIcons, "Scale", headIconScale);
		JCheckBox prayerBackground = new JCheckBox();
		prayerBackground.setSelected(true);
		row(headIcons, "Prayer background", prayerBackground);

		Color updatedColor = new Color(12, 34, 56, 78);
		Map<String, Object> values = new LinkedHashMap<>();
		values.put(CustomizeALotConfig.PRESET_KEY, "CUSTOM");
		values.put(CustomizeALotConfig.HEALTH_BAR_SCALE_PERCENT_KEY, 150);
		values.put(CustomizeALotConfig.HEALTH_BAR_LARGE_SCALE_PERCENT_KEY, 175);
		values.put(CustomizeALotConfig.HEALTH_BAR_LARGE_HEIGHT_SCALE_PERCENT_KEY, 125);
		values.put(CustomizeALotConfig.HEALTH_BAR_SOLID_WIDTH_KEY, 49.0);
		values.put(CustomizeALotConfig.HEALTH_BAR_SEGMENTS_ENABLED_KEY, true);
		values.put(CustomizeALotConfig.HEALTH_BAR_FRONT_GRADIENT_KEY,
			CustomizeALotHealthBarGradient.HORIZONTAL);
		values.put(CustomizeALotConfig.HEALTH_BAR_FRONT_COLOR_KEY, updatedColor);
		values.put(CustomizeALotConfig.SHOW_PRAYER_ICON_BACKGROUND_KEY, false);

		assertEquals(9, CustomizeALotConfigPanelSync.synchronize(root, values));
		assertSame(CustomizeALotPreset.CUSTOM, hitsplatPreset.getSelectedItem());
		assertEquals(100, hitsplatScale.getValue());
		assertEquals(150, healthScale.getValue());
		assertEquals(175, bossWidthScale.getValue());
		assertEquals(125, bossHeightScale.getValue());
		assertEquals(49.0, (Double) healthWidth.getValue(), 0.0);
		assertTrue(segments.isSelected());
		assertSame(CustomizeALotHealthBarGradient.HORIZONTAL, gradient.getSelectedItem());
		assertEquals(updatedColor, frontColor.getColor());
		assertEquals("#4E0C2238", frontColor.getText());
		assertEquals(100, headIconScale.getValue());
		assertFalse(prayerBackground.isSelected());
		assertTrue(CustomizeALotConfigPanelSync.spinnerHasValue(
			root,
			CustomizeALotConfig.HEALTH_BAR_SOLID_WIDTH_KEY,
			"49.0"));
		assertFalse(CustomizeALotConfigPanelSync.spinnerHasValue(
			root,
			CustomizeALotConfig.HEALTH_BAR_SOLID_WIDTH_KEY,
			"50.0"));
		assertFalse(CustomizeALotConfigPanelSync.spinnerHasValue(
			root,
			CustomizeALotConfig.HEALTH_BAR_FRONT_COLOR_KEY,
			"49.0"));
	}

	@Test
	public void duplicateNamesAreScopedBySectionEvenWhenCollapsed()
	{
		JPanel root = configPanel("Customize a Lot");
		JPanel hitsplats = section(root, "Hitsplats");
		JPanel healthBars = section(root, "Health bars");
		JPanel overheadChat = section(root, "Overhead chat");
		JPanel headIcons = section(root, "Head icons");

		JComboBox<CustomizeALotPreset> hitsplatPreset = new JComboBox<>(CustomizeALotPreset.values());
		JComboBox<CustomizeALotHealthBarPreset> healthPreset =
			new JComboBox<>(CustomizeALotHealthBarPreset.values());
		JComboBox<CustomizeALotOverheadChatPreset> chatPreset =
			new JComboBox<>(CustomizeALotOverheadChatPreset.values());
		JComboBox<CustomizeALotHeadIconPreset> iconPreset =
			new JComboBox<>(CustomizeALotHeadIconPreset.values());
		hitsplatPreset.setSelectedItem(CustomizeALotPreset.RUNESCAPE);
		healthPreset.setSelectedItem(CustomizeALotHealthBarPreset.RUNESCAPE);
		chatPreset.setSelectedItem(CustomizeALotOverheadChatPreset.RUNESCAPE);
		iconPreset.setSelectedItem(CustomizeALotHeadIconPreset.RUNESCAPE);
		row(hitsplats, "Preset", hitsplatPreset);
		row(healthBars, "Preset", healthPreset);
		row(overheadChat, "Preset", chatPreset);
		row(headIcons, "Preset", iconPreset);
		overheadChat.setVisible(false);

		Map<String, Object> values = new LinkedHashMap<>();
		values.put(CustomizeALotConfig.OVERHEAD_CHAT_PRESET_KEY, "CUSTOM");

		assertEquals(1, CustomizeALotConfigPanelSync.synchronize(root, values));
		assertSame(CustomizeALotPreset.RUNESCAPE, hitsplatPreset.getSelectedItem());
		assertSame(CustomizeALotHealthBarPreset.RUNESCAPE, healthPreset.getSelectedItem());
		assertSame(CustomizeALotOverheadChatPreset.CUSTOM, chatPreset.getSelectedItem());
		assertSame(CustomizeALotHeadIconPreset.RUNESCAPE, iconPreset.getSelectedItem());
	}

	@Test
	public void ignoresLookalikeRowsOutsideTheCustomizeALotPanel()
	{
		JPanel root = configPanel("Another plugin");
		JPanel hitsplats = section(root, "Hitsplats");
		section(root, "Health bars");
		section(root, "Overhead chat");
		section(root, "Head icons");
		JSpinner scale = intSpinner(100);
		row(hitsplats, "Scale", scale);

		Map<String, Object> values = new LinkedHashMap<>();
		values.put(CustomizeALotConfig.HITSPLAT_SCALE_PERCENT_KEY, 175);

		assertEquals(0, CustomizeALotConfigPanelSync.synchronize(root, values));
		assertEquals(100, scale.getValue());
	}

	@Test
	public void everyPresetControlledKeyHasARegisteredField()
	{
		Map<String, Object> hitsplatSettings = new LinkedHashMap<>();
		CustomizeALotPlugin.applyPreset(CustomizeALotPreset.RUNESCAPE, hitsplatSettings::put);
		assertRegistered(hitsplatSettings);
		assertRegistered(CustomizeALotHealthBarPreset.RUNESCAPE.getSettings());
		assertRegistered(CustomizeALotHealthBarPreset.RUINED_HEIR.getSettings());
		assertRegistered(CustomizeALotOverheadChatPreset.RUNESCAPE.getSettings());
		assertRegistered(CustomizeALotOverheadChatPreset.RUINED_HEIR.getSettings());
		assertRegistered(CustomizeALotHeadIconPreset.RUNESCAPE.getSettings());
		assertTrue(CustomizeALotConfigPanelSync.hasRegisteredField(CustomizeALotConfig.PRESET_KEY));
		assertTrue(CustomizeALotConfigPanelSync.hasRegisteredField(CustomizeALotConfig.HEALTH_BAR_PRESET_KEY));
		assertTrue(CustomizeALotConfigPanelSync.hasRegisteredField(CustomizeALotConfig.OVERHEAD_CHAT_PRESET_KEY));
		assertTrue(CustomizeALotConfigPanelSync.hasRegisteredField(CustomizeALotConfig.HEAD_ICON_PRESET_KEY));
		assertFalse(CustomizeALotConfigPanelSync.hasRegisteredField("unknown"));
	}

	private static void assertRegistered(Map<String, Object> settings)
	{
		for (String key : settings.keySet())
		{
			assertTrue(key, CustomizeALotConfigPanelSync.hasRegisteredField(key));
		}
	}

	private static JPanel configPanel(String title)
	{
		JPanel root = new JPanel();
		JPanel header = new JPanel();
		header.add(new JLabel(title));
		root.add(header);
		return root;
	}

	private static JPanel section(JPanel root, String name)
	{
		JPanel section = new JPanel();
		JPanel header = new JPanel();
		header.add(new JLabel(name));
		section.add(header);
		root.add(section);
		return section;
	}

	private static void row(JPanel section, String name, java.awt.Component control)
	{
		JPanel row = new JPanel();
		row.add(new JLabel(name));
		row.add(control);
		section.add(row);
	}

	private static JSpinner intSpinner(int value)
	{
		return new JSpinner(new SpinnerNumberModel(value, -1000, 1000, 1));
	}

	private static JSpinner doubleSpinner(double value)
	{
		return new JSpinner(new SpinnerNumberModel(value, 0.0, 1000.0, 0.1));
	}
}
