package io.github.iamthecodedecoded.banktabshortcuts;

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup("banktabshortcuts")
public interface BankTabShortcutsConfig extends Config
{
	@ConfigItem(keyName = "showTabNumbers", name = "Show tab numbers",
		description = "Show subtle shortcut numbers at the top-left of native bank tabs", position = 0)
	default boolean showTabNumbers()
	{
		return false;
	}

	@Alpha
	@ConfigItem(keyName = "numberColor", name = "Number color",
		description = "Color and opacity of the tab shortcut numbers", position = 1)
	default Color numberColor()
	{
		return new Color(210, 200, 175, 220);
	}

	@Range(min = 8, max = 16)
	@ConfigItem(keyName = "numberSize", name = "Number size",
		description = "Font size of the tab shortcut numbers (8-16)", position = 2)
	default int numberSize()
	{
		return 10;
	}
}
