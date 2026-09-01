package io.github.iamthecodedecoded.banktabshortcuts;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class BankTabShortcutsPluginTestClient
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(BankTabShortcutsPlugin.class);
		RuneLite.main(args);
	}
}
