package com.customizealot;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Window;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JSpinner;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;
import net.runelite.client.ui.components.ColorJButton;
import net.runelite.client.util.ColorUtil;

final class CustomizeALotConfigPanelSync
{
	private static final String PLUGIN_TITLE = "Customize a Lot";
	private static final String HITSPLATS_SECTION = "Hitsplats";
	private static final String HEALTH_BARS_SECTION = "Health bars";
	private static final String OVERHEAD_CHAT_SECTION = "Overhead chat";
	private static final String HEAD_ICONS_SECTION = "Head icons";
	private static final String[] REQUIRED_SECTIONS = {
		HITSPLATS_SECTION,
		HEALTH_BARS_SECTION,
		OVERHEAD_CHAT_SECTION,
		HEAD_ICONS_SECTION
	};
	private static final Map<String, ConfigField> FIELDS = createFields();

	private CustomizeALotConfigPanelSync()
	{
	}

	static void refreshOpenPanel(Map<String, Object> values)
	{
		if (values == null || values.isEmpty())
		{
			return;
		}

		Map<String, Object> pendingValues = new LinkedHashMap<>();
		for (Map.Entry<String, Object> entry : values.entrySet())
		{
			if (FIELDS.containsKey(entry.getKey()))
			{
				pendingValues.put(entry.getKey(), entry.getValue());
			}
		}
		if (pendingValues.isEmpty())
		{
			return;
		}

		// RuneLite does not refresh sibling controls after programmatic config writes.
		// Update only this plugin's known rows, following the targeted UI pattern used by Bank Gear Slots.
		SwingUtilities.invokeLater(() ->
		{
			for (Window window : Window.getWindows())
			{
				synchronize(window, pendingValues);
			}
		});
	}

	static void refreshOpenPanelNow(Map<String, Object> values)
	{
		if (!SwingUtilities.isEventDispatchThread())
		{
			refreshOpenPanel(values);
			return;
		}

		for (Window window : Window.getWindows())
		{
			synchronize(window, values);
		}
	}

	static boolean openSpinnerHasValue(String key, String configuredValue)
	{
		if (!SwingUtilities.isEventDispatchThread())
		{
			return false;
		}
		for (Window window : Window.getWindows())
		{
			if (spinnerHasValue(window, key, configuredValue))
			{
				return true;
			}
		}
		return false;
	}

	static boolean spinnerHasValue(Component root, String key, String configuredValue)
	{
		ConfigField field = FIELDS.get(key);
		if (root == null || field == null || configuredValue == null)
		{
			return false;
		}

		Container configPanel = findConfigPanel(root);
		Container sectionPanel = configPanel == null
			? null
			: findSectionPanel(configPanel, field.section);
		Container row = sectionPanel == null ? null : findRow(sectionPanel, field.name);
		JSpinner spinner = row == null
			? null
			: (JSpinner) findDescendant(row, child -> child instanceof JSpinner);
		if (spinner == null || !(spinner.getValue() instanceof Number))
		{
			return false;
		}

		try
		{
			return Double.compare(
				((Number) spinner.getValue()).doubleValue(),
				Double.parseDouble(configuredValue)) == 0;
		}
		catch (NumberFormatException ex)
		{
			return false;
		}
	}

	static int synchronize(Component root, Map<String, Object> values)
	{
		if (root == null || values == null || values.isEmpty())
		{
			return 0;
		}

		Container configPanel = findConfigPanel(root);
		if (configPanel == null)
		{
			return 0;
		}

		Map<String, Container> sectionPanels = new HashMap<>();
		int synchronizedFields = 0;
		for (Map.Entry<String, Object> entry : values.entrySet())
		{
			ConfigField field = FIELDS.get(entry.getKey());
			if (field == null)
			{
				continue;
			}

			Container sectionPanel = sectionPanels.get(field.section);
			if (sectionPanel == null)
			{
				sectionPanel = findSectionPanel(configPanel, field.section);
				if (sectionPanel == null)
				{
					continue;
				}
				sectionPanels.put(field.section, sectionPanel);
			}

			Container row = findRow(sectionPanel, field.name);
			if (row != null && updateControl(row, entry.getValue(), field))
			{
				synchronizedFields++;
			}
		}
		return synchronizedFields;
	}

	static boolean hasRegisteredField(String key)
	{
		return FIELDS.containsKey(key);
	}

	private static Container findConfigPanel(Component component)
	{
		if (component instanceof JLabel
			&& PLUGIN_TITLE.equals(((JLabel) component).getText()))
		{
			for (Container ancestor = component.getParent();
				ancestor != null;
				ancestor = ancestor.getParent())
			{
				if (containsAllSectionLabels(ancestor))
				{
					return ancestor;
				}
			}
		}

		if (!(component instanceof Container))
		{
			return null;
		}
		for (Component child : ((Container) component).getComponents())
		{
			Container configPanel = findConfigPanel(child);
			if (configPanel != null)
			{
				return configPanel;
			}
		}
		return null;
	}

	private static boolean containsAllSectionLabels(Container container)
	{
		for (String section : REQUIRED_SECTIONS)
		{
			if (findLabel(container, section) == null)
			{
				return false;
			}
		}
		return true;
	}

	private static Container findSectionPanel(Container configPanel, String sectionName)
	{
		JLabel sectionLabel = findLabel(configPanel, sectionName);
		if (sectionLabel == null || sectionLabel.getParent() == null)
		{
			return null;
		}
		return sectionLabel.getParent().getParent();
	}

	private static JLabel findLabel(Component component, String text)
	{
		if (component instanceof JLabel && text.equals(((JLabel) component).getText()))
		{
			return (JLabel) component;
		}
		if (!(component instanceof Container))
		{
			return null;
		}
		for (Component child : ((Container) component).getComponents())
		{
			JLabel label = findLabel(child, text);
			if (label != null)
			{
				return label;
			}
		}
		return null;
	}

	private static Container findRow(Component component, String name)
	{
		if (!(component instanceof Container))
		{
			return null;
		}

		Container container = (Container) component;
		for (Component child : container.getComponents())
		{
			if (child instanceof JLabel && name.equals(((JLabel) child).getText()))
			{
				return container;
			}
		}
		for (Component child : container.getComponents())
		{
			Container row = findRow(child, name);
			if (row != null)
			{
				return row;
			}
		}
		return null;
	}

	private static boolean updateControl(Container row, Object value, ConfigField field)
	{
		if (value instanceof Boolean)
		{
			JCheckBox checkBox = (JCheckBox) findDescendant(row, child -> child instanceof JCheckBox);
			if (checkBox == null)
			{
				return false;
			}
			checkBox.setSelected((Boolean) value);
			return true;
		}

		if (value instanceof Number)
		{
			JSpinner spinner = (JSpinner) findDescendant(row, child -> child instanceof JSpinner);
			if (spinner == null)
			{
				return false;
			}
			Object spinnerValue = spinnerValue((Number) value, spinner.getValue());
			if (!Objects.equals(spinner.getValue(), spinnerValue))
			{
				spinner.setValue(spinnerValue);
			}
			return true;
		}

		if (value instanceof Color)
		{
			ColorJButton colorButton = (ColorJButton) findDescendant(
				row,
				child -> child instanceof ColorJButton);
			if (colorButton == null)
			{
				return false;
			}
			Color color = (Color) value;
			colorButton.setColor(color);
			String colorHex = field.alpha
				? ColorUtil.colorToAlphaHexCode(color)
				: ColorUtil.colorToHexCode(color);
			colorButton.setText("#" + colorHex.toUpperCase(Locale.ROOT));
			return true;
		}

		JComboBox<?> comboBox = (JComboBox<?>) findDescendant(
			row,
			child -> child instanceof JComboBox);
		if (comboBox != null)
		{
			Object item = findComboBoxItem(comboBox, value);
			if (item == null)
			{
				return false;
			}
			if (!Objects.equals(comboBox.getSelectedItem(), item))
			{
				comboBox.setSelectedItem(item);
			}
			return true;
		}

		if (value instanceof String)
		{
			JTextComponent text = (JTextComponent) findDescendant(
				row,
				child -> child instanceof JTextComponent);
			if (text != null)
			{
				text.setText((String) value);
				return true;
			}
		}
		// Font settings and hidden compatibility values have no inline value control to update.
		return false;
	}

	private static Object spinnerValue(Number value, Object currentValue)
	{
		if (currentValue instanceof Integer)
		{
			return value.intValue();
		}
		if (currentValue instanceof Long)
		{
			return value.longValue();
		}
		if (currentValue instanceof Float)
		{
			return value.floatValue();
		}
		return value.doubleValue();
	}

	private static Object findComboBoxItem(JComboBox<?> comboBox, Object value)
	{
		String expected = value instanceof Enum
			? ((Enum<?>) value).name()
			: String.valueOf(value);
		for (int index = 0; index < comboBox.getItemCount(); index++)
		{
			Object item = comboBox.getItemAt(index);
			if (Objects.equals(item, value)
				|| item instanceof Enum && expected.equals(((Enum<?>) item).name()))
			{
				return item;
			}
		}
		return null;
	}

	private static Component findDescendant(Component component, Predicate<Component> predicate)
	{
		if (predicate.test(component))
		{
			return component;
		}
		if (!(component instanceof Container))
		{
			return null;
		}
		for (Component child : ((Container) component).getComponents())
		{
			Component match = findDescendant(child, predicate);
			if (match != null)
			{
				return match;
			}
		}
		return null;
	}

	private static Map<String, ConfigField> createFields()
	{
		Map<String, ConfigField> fields = new HashMap<>();

		field(fields, HITSPLATS_SECTION, "Preset", CustomizeALotConfig.PRESET_KEY);
		field(fields, HITSPLATS_SECTION, "My hits only", CustomizeALotConfig.ONLY_DISPLAY_MINE_KEY);
		field(fields, HITSPLATS_SECTION, "Prioritize my hits", CustomizeALotConfig.PRIORITIZE_MINE_KEY);
		field(fields, HITSPLATS_SECTION, "Hide zero hits", CustomizeALotConfig.HIDE_ZERO_HITSPLATS_KEY);
		field(fields, HITSPLATS_SECTION, "Shape", CustomizeALotConfig.LAYOUT_SHAPE_KEY);
		field(fields, HITSPLATS_SECTION, "Direction", CustomizeALotConfig.LAYOUT_DIRECTION_KEY);
		field(fields, HITSPLATS_SECTION, "Behavior", CustomizeALotConfig.LAYOUT_BEHAVIOR_KEY);
		field(fields, HITSPLATS_SECTION, "Scale", CustomizeALotConfig.HITSPLAT_SCALE_PERCENT_KEY);
		field(fields, HITSPLATS_SECTION, "Min radius", CustomizeALotConfig.MIN_RADIUS_KEY);
		field(fields, HITSPLATS_SECTION, "Max radius", CustomizeALotConfig.MAX_RADIUS_KEY);
		field(fields, HITSPLATS_SECTION, "X spacing", CustomizeALotConfig.X_SPACING_KEY);
		field(fields, HITSPLATS_SECTION, "Y spacing", CustomizeALotConfig.Y_SPACING_KEY);
		field(fields, HITSPLATS_SECTION, "Font", CustomizeALotConfig.HITSPLAT_FONT_KEY);
		field(fields, HITSPLATS_SECTION, "Fade-in duration", CustomizeALotConfig.FADE_IN_DURATION_KEY);
		field(fields, HITSPLATS_SECTION, "Full opacity duration", CustomizeALotConfig.FULL_OPACITY_DURATION_KEY);
		field(fields, HITSPLATS_SECTION, "Fade-out duration", CustomizeALotConfig.FADE_OUT_DURATION_KEY);

		field(fields, HEALTH_BARS_SECTION, "Preset", CustomizeALotConfig.HEALTH_BAR_PRESET_KEY);
		field(fields, HEALTH_BARS_SECTION, "Legacy style", CustomizeALotConfig.HEALTH_BAR_STYLE_KEY);
		field(fields, HEALTH_BARS_SECTION, "Scale mode", CustomizeALotConfig.HEALTH_BAR_SCALE_MODE_KEY);
		field(fields, HEALTH_BARS_SECTION, "Scale", CustomizeALotConfig.HEALTH_BAR_SCALE_PERCENT_KEY);
		field(fields, HEALTH_BARS_SECTION, "Boss scale threshold", CustomizeALotConfig.HEALTH_BAR_SCALE_THRESHOLD_KEY);
		field(fields, HEALTH_BARS_SECTION, "Boss width scale", CustomizeALotConfig.HEALTH_BAR_LARGE_SCALE_PERCENT_KEY);
		field(fields, HEALTH_BARS_SECTION, "Boss height scale", CustomizeALotConfig.HEALTH_BAR_LARGE_HEIGHT_SCALE_PERCENT_KEY);
		field(fields, HEALTH_BARS_SECTION, "Width", CustomizeALotConfig.HEALTH_BAR_SOLID_WIDTH_KEY);
		field(fields, HEALTH_BARS_SECTION, "Height", CustomizeALotConfig.HEALTH_BAR_HEIGHT_KEY);
		field(fields, HEALTH_BARS_SECTION, "X offset", CustomizeALotConfig.HEALTH_BAR_X_OFFSET_KEY);
		field(fields, HEALTH_BARS_SECTION, "Y offset", CustomizeALotConfig.HEALTH_BAR_Y_OFFSET_KEY);
		field(fields, HEALTH_BARS_SECTION, "Fill direction", CustomizeALotConfig.HEALTH_BAR_FILL_DIRECTION_KEY);
		field(fields, HEALTH_BARS_SECTION, "Front coordinates", CustomizeALotConfig.HEALTH_BAR_FRONT_GRADIENT_COORDINATES_KEY);
		field(fields, HEALTH_BARS_SECTION, "Front gradient", CustomizeALotConfig.HEALTH_BAR_FRONT_GRADIENT_KEY);
		alphaField(fields, HEALTH_BARS_SECTION, "Front color", CustomizeALotConfig.HEALTH_BAR_FRONT_COLOR_KEY);
		alphaField(fields, HEALTH_BARS_SECTION, "Front second color", CustomizeALotConfig.HEALTH_BAR_FRONT_SECONDARY_COLOR_KEY);
		alphaField(fields, HEALTH_BARS_SECTION, "Poisoned front color", CustomizeALotConfig.HEALTH_BAR_POISONED_FRONT_COLOR_KEY);
		field(fields, HEALTH_BARS_SECTION, "Back coordinates", CustomizeALotConfig.HEALTH_BAR_BACK_GRADIENT_COORDINATES_KEY);
		field(fields, HEALTH_BARS_SECTION, "Back gradient", CustomizeALotConfig.HEALTH_BAR_BACK_GRADIENT_KEY);
		alphaField(fields, HEALTH_BARS_SECTION, "Back color", CustomizeALotConfig.HEALTH_BAR_BACK_COLOR_KEY);
		alphaField(fields, HEALTH_BARS_SECTION, "Back second color", CustomizeALotConfig.HEALTH_BAR_BACK_SECONDARY_COLOR_KEY);
		field(fields, HEALTH_BARS_SECTION, "Damage trail", CustomizeALotConfig.HEALTH_BAR_DAMAGE_TRAIL_ENABLED_KEY);
		alphaField(fields, HEALTH_BARS_SECTION, "Damage trail color", CustomizeALotConfig.HEALTH_BAR_DAMAGE_TRAIL_COLOR_KEY);
		field(fields, HEALTH_BARS_SECTION, "Damage trail hold", CustomizeALotConfig.HEALTH_BAR_DAMAGE_TRAIL_HOLD_KEY);
		field(fields, HEALTH_BARS_SECTION, "Damage trail drain", CustomizeALotConfig.HEALTH_BAR_DAMAGE_TRAIL_DRAIN_KEY);
		field(fields, HEALTH_BARS_SECTION, "HP segments", CustomizeALotConfig.HEALTH_BAR_SEGMENTS_ENABLED_KEY);
		field(fields, HEALTH_BARS_SECTION, "Segment HP source", CustomizeALotConfig.HEALTH_BAR_SEGMENT_VALUE_MODE_KEY);
		field(fields, HEALTH_BARS_SECTION, "HP per segment", CustomizeALotConfig.HEALTH_BAR_HITPOINTS_PER_SEGMENT_KEY);
		alphaField(fields, HEALTH_BARS_SECTION, "Segment color", CustomizeALotConfig.HEALTH_BAR_SEGMENT_COLOR_KEY);
		field(fields, HEALTH_BARS_SECTION, "Segment thickness", CustomizeALotConfig.HEALTH_BAR_SEGMENT_THICKNESS_KEY);
		alphaField(fields, HEALTH_BARS_SECTION, "Border color", CustomizeALotConfig.HEALTH_BAR_BORDER_COLOR_KEY);
		field(fields, HEALTH_BARS_SECTION, "Border thickness", CustomizeALotConfig.HEALTH_BAR_BORDER_THICKNESS_KEY);
		field(fields, HEALTH_BARS_SECTION, "Corner radius", CustomizeALotConfig.HEALTH_BAR_CORNER_RADIUS_KEY);

		field(fields, OVERHEAD_CHAT_SECTION, "Preset", CustomizeALotConfig.OVERHEAD_CHAT_PRESET_KEY);
		field(fields, OVERHEAD_CHAT_SECTION, "NPC overhead text", CustomizeALotConfig.SHOW_NPC_OVERHEAD_CHAT_KEY);
		field(fields, OVERHEAD_CHAT_SECTION, "Font", CustomizeALotConfig.OVERHEAD_CHAT_FONT_KEY);
		alphaField(fields, OVERHEAD_CHAT_SECTION, "Color", CustomizeALotConfig.OVERHEAD_CHAT_COLOR_KEY);
		field(fields, OVERHEAD_CHAT_SECTION, "Relationship colors", CustomizeALotConfig.OVERHEAD_CHAT_RELATIONSHIP_COLORS_KEY);
		alphaField(fields, OVERHEAD_CHAT_SECTION, "Friend color", CustomizeALotConfig.OVERHEAD_CHAT_FRIEND_COLOR_KEY);
		alphaField(fields, OVERHEAD_CHAT_SECTION, "Clan color", CustomizeALotConfig.OVERHEAD_CHAT_CLAN_COLOR_KEY);
		alphaField(fields, OVERHEAD_CHAT_SECTION, "Group iron color", CustomizeALotConfig.OVERHEAD_CHAT_GROUP_IRON_COLOR_KEY);
		field(fields, OVERHEAD_CHAT_SECTION, "Default Text Effect", CustomizeALotConfig.OVERHEAD_CHAT_EFFECT_KEY);
		field(fields, OVERHEAD_CHAT_SECTION, "Text shadow", CustomizeALotConfig.OVERHEAD_CHAT_SHADOW_KEY);
		alphaField(fields, OVERHEAD_CHAT_SECTION, "Shadow color", CustomizeALotConfig.OVERHEAD_CHAT_SHADOW_COLOR_KEY);
		field(fields, OVERHEAD_CHAT_SECTION, "X offset", CustomizeALotConfig.OVERHEAD_CHAT_X_OFFSET_KEY);
		field(fields, OVERHEAD_CHAT_SECTION, "Y offset", CustomizeALotConfig.OVERHEAD_CHAT_Y_OFFSET_KEY);

		field(fields, HEAD_ICONS_SECTION, "Preset", CustomizeALotConfig.HEAD_ICON_PRESET_KEY);
		field(fields, HEAD_ICONS_SECTION, "Prayer icons", CustomizeALotConfig.SHOW_PRAYER_ICONS_KEY);
		field(fields, HEAD_ICONS_SECTION, "Prayer background", CustomizeALotConfig.SHOW_PRAYER_ICON_BACKGROUND_KEY);
		field(fields, HEAD_ICONS_SECTION, "Skull icons", CustomizeALotConfig.SHOW_SKULL_ICONS_KEY);
		field(fields, HEAD_ICONS_SECTION, "NPC icons", CustomizeALotConfig.SHOW_NPC_ICONS_KEY);
		field(fields, HEAD_ICONS_SECTION, "Hint arrows", CustomizeALotConfig.SHOW_HINT_ARROWS_KEY);
		field(fields, HEAD_ICONS_SECTION, "Scale", CustomizeALotConfig.HEAD_ICON_SCALE_PERCENT_KEY);
		field(fields, HEAD_ICONS_SECTION, "X offset", CustomizeALotConfig.HEAD_ICON_X_OFFSET_KEY);
		field(fields, HEAD_ICONS_SECTION, "Y offset", CustomizeALotConfig.HEAD_ICON_Y_OFFSET_KEY);
		field(fields, HEAD_ICONS_SECTION, "Spacing", CustomizeALotConfig.HEAD_ICON_SPACING_KEY);

		return Collections.unmodifiableMap(fields);
	}

	private static void field(Map<String, ConfigField> fields, String section, String name, String key)
	{
		fields.put(key, new ConfigField(section, name, false));
	}

	private static void alphaField(Map<String, ConfigField> fields, String section, String name, String key)
	{
		fields.put(key, new ConfigField(section, name, true));
	}

	private static final class ConfigField
	{
		private final String section;
		private final String name;
		private final boolean alpha;

		private ConfigField(String section, String name, boolean alpha)
		{
			this.section = section;
			this.name = name;
			this.alpha = alpha;
		}
	}
}
