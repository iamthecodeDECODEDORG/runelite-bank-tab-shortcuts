package io.github.iamthecodedecoded.banktabshortcuts;

import com.google.inject.Inject;
import java.awt.event.KeyEvent;
import net.runelite.api.Client;
import net.runelite.api.ScriptID;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@PluginDescriptor(
	name = "Bank Tab Shortcuts",
	description = "Navigate native bank tabs with Ctrl+1 through Ctrl+9 and Ctrl+Tab shortcuts",
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

	private volatile boolean bankOpen;

	@Override
	protected void startUp()
	{
		Widget bankItems = client.getWidget(InterfaceID.Bankmain.ITEMS);
		bankOpen = bankItems != null && !bankItems.isHidden();
		keyManager.registerKeyListener(this);
	}

	@Override
	protected void shutDown()
	{
		keyManager.unregisterKeyListener(this);
		bankOpen = false;
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() == InterfaceID.BANKMAIN)
		{
			bankOpen = true;
		}
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		if (event.getGroupId() == InterfaceID.BANKMAIN)
		{
			bankOpen = false;
		}
	}

	@Override
	public void keyPressed(KeyEvent event)
	{
		int request = BankTabNavigator.requestFor(event);
		if (request == BankTabNavigator.NONE || !bankOpen)
		{
			return;
		}

		Widget bankItems = client.getWidget(InterfaceID.Bankmain.ITEMS);
		if (bankItems == null || bankItems.isHidden())
		{
			return;
		}

		int availableTarget = BankTabNavigator.targetFor(
			request,
			client.getVarbitValue(VarbitID.BANK_CURRENTTAB),
			readTabItemCounts());
		if (availableTarget == BankTabNavigator.NONE)
		{
			return;
		}

		event.consume();
		clientThread.invoke(() -> navigate(request));
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
			if (!bankOpen)
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
