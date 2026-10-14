package io.github.iamthecodedecoded.banktabshortcuts;

import com.google.inject.Inject;
import com.google.inject.Provides;
import java.awt.event.KeyEvent;
import net.runelite.api.Client;
import net.runelite.api.ScriptID;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@PluginDescriptor(
	name = "Bank Tab Shortcuts",
	description = "Navigate native bank tabs with Ctrl+0 through Ctrl+9, Ctrl+Tab, and optional tab numbers",
	tags = {"bank", "tabs", "keyboard", "hotkeys", "shortcuts"}
)
public class BankTabShortcutsPlugin extends Plugin implements KeyListener
{
	private static final Logger LOG = LoggerFactory.getLogger(BankTabShortcutsPlugin.class);

	private static final int[] BANK_TAB_COUNT_VARBITS =
	{
		VarbitID.BANK_TAB_1,
		VarbitID.BANK_TAB_2,
		VarbitID.BANK_TAB_3,
		VarbitID.BANK_TAB_4,
		VarbitID.BANK_TAB_5,
		VarbitID.BANK_TAB_6,
		VarbitID.BANK_TAB_7,
		VarbitID.BANK_TAB_8,
		VarbitID.BANK_TAB_9
	};

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private KeyManager keyManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private BankTabNumbersOverlay numbersOverlay;

	@Provides
	BankTabShortcutsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(BankTabShortcutsConfig.class);
	}

	// Only immutable, client-thread-produced data crosses into the AWT key listener.
	private volatile int availableTabs;
	private volatile Object activation;

	@Override
	protected void startUp()
	{
		availableTabs = 0;
		activation = new Object();
		keyManager.registerKeyListener(this);
		overlayManager.add(numbersOverlay);
	}

	@Override
	protected void shutDown()
	{
		activation = null;
		availableTabs = 0;
		keyManager.unregisterKeyListener(this);
		overlayManager.remove(numbersOverlay);
	}

	@Subscribe
	public void onClientTick(ClientTick event)
	{
		if (activation == null)
		{
			return;
		}

		try
		{
			int tabs = 0;
			Widget bankItems = client.getWidget(InterfaceID.Bankmain.ITEMS);
			if (bankItems != null && !bankItems.isHidden())
			{
				tabs = 1; // Bit zero is the all-items view.
				int[] counts = readTabItemCounts();
				for (int i = 0; i < counts.length; i++)
				{
					if (counts[i] > 0)
					{
						tabs |= 1 << (i + 1);
					}
				}
			}
			availableTabs = tabs;
		}
		catch (RuntimeException exception)
		{
			availableTabs = 0;
			LOG.error("BTS_BANK_STATE_FAILED", exception);
		}
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		if (event.getGroupId() == InterfaceID.BANKMAIN)
		{
			availableTabs = 0;
		}
	}

	@Override
	public void keyPressed(KeyEvent event)
	{
		int request = BankTabNavigator.requestFor(event);
		Object currentActivation = activation;
		int tabs = availableTabs;
		if (event.isConsumed() || request == BankTabNavigator.NONE || currentActivation == null || tabs == 0)
		{
			return;
		}

		if (request >= 0 && request <= BankTabNavigator.TAB_COUNT && (tabs & (1 << request)) == 0)
		{
			return;
		}
		if ((request == BankTabNavigator.NEXT || request == BankTabNavigator.PREVIOUS) && (tabs & ~1) == 0)
		{
			return;
		}

		event.consume();
		clientThread.invoke(() ->
		{
			if (activation == currentActivation)
			{
				navigate(request);
			}
		});
	}

	@Override
	public void keyTyped(KeyEvent event)
	{
	}

	@Override
	public void keyReleased(KeyEvent event)
	{
	}

	private int[] readTabItemCounts()
	{
		int[] counts = new int[BANK_TAB_COUNT_VARBITS.length];
		for (int i = 0; i < BANK_TAB_COUNT_VARBITS.length; i++)
		{
			counts[i] = client.getVarbitValue(BANK_TAB_COUNT_VARBITS[i]);
		}
		return counts;
	}

	private void navigate(int request)
	{
		try
		{
			if (activation == null || availableTabs == 0)
			{
				return;
			}

			Widget bankItems = client.getWidget(InterfaceID.Bankmain.ITEMS);
			if (bankItems == null || bankItems.isHidden())
			{
				return;
			}

			int target = BankTabNavigator.targetFor(
				request,
				client.getVarbitValue(VarbitID.BANK_CURRENTTAB),
				readTabItemCounts());
			if (target == BankTabNavigator.NONE)
			{
				return;
			}

			Object[] bankBuildArgs = bankItems.getOnInvTransmitListener();
			if (bankBuildArgs == null)
			{
				LOG.warn("BTS_BANK_REBUILD_LISTENER_MISSING request={}", request);
				return;
			}

			client.setVarbit(VarbitID.BANK_CURRENTTAB, target);
			client.runScript(ScriptID.MESSAGE_LAYER_CLOSE, 1, 1, 0);
			client.runScript(bankBuildArgs);
		}
		catch (RuntimeException exception)
		{
			LOG.error("BTS_NAVIGATION_FAILED request={}", request, exception);
		}
	}
}
