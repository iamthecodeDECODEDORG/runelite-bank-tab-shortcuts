package io.github.iamthecodedecoded.banktabshortcuts;

import com.google.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.Arrays;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

final class BankTabNumbersOverlay extends Overlay
{
	// Native tab click-targets occupy children 10..19 (All, then tabs 1..9).
	private static final int FIRST_TAB_CHILD = 10;
	private final Client client;
	private final BankTabShortcutsConfig config;

	@Inject
	BankTabNumbersOverlay(Client client, BankTabShortcutsConfig config)
	{
		this.client = client;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.MANUAL);
		drawAfterLayer(InterfaceID.Bankmain.TABS);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showTabNumbers())
		{
			return null;
		}
		Widget items = client.getWidget(InterfaceID.Bankmain.ITEMS);
		Widget tabs = client.getWidget(InterfaceID.Bankmain.TABS);
		if (items == null || items.isHidden() || tabs == null || tabs.isHidden())
		{
			return null;
		}
		Widget all = tabs.getChild(FIRST_TAB_CHILD);
		if (all == null || all.isHidden())
		{
			return null;
		}

		// RuneLite renders overlays on the client thread; never retain widget references.
		for (int tab = 0; tab <= BankTabNavigator.TAB_COUNT; tab++)
		{
			if (tab > 0 && client.getVarbitValue(VarbitID.BANK_TAB_1 + tab - 1) <= 0)
			{
				continue;
			}
			Widget widget = tabs.getChild(FIRST_TAB_CHILD + tab);
			if (widget == null || widget.isHidden())
			{
				continue;
			}
			String[] actions = widget.getActions();
			if (actions != null && Arrays.asList(actions).contains("New tab"))
			{
				continue;
			}
			Rectangle bounds = widget.getBounds();
			if (bounds.width <= 0 || bounds.height <= 0)
			{
				continue;
			}
			Graphics2D label = (Graphics2D) graphics.create();
			try
			{
				label.clip(tabs.getBounds());
				label.clip(bounds);
				label.setFont(FontManager.getRunescapeSmallFont().deriveFont((float) Math.max(8, Math.min(16, config.numberSize()))));
				int x = bounds.x + 2;
				int y = bounds.y + 2 + label.getFontMetrics().getAscent();
				String text = Integer.toString(tab);
				label.setColor(new Color(0, 0, 0, config.numberColor().getAlpha() / 2));
				label.drawString(text, x + 1, y + 1);
				label.setColor(config.numberColor());
				label.drawString(text, x, y);
			}
			finally
			{
				label.dispose();
			}
		}
		return null;
	}
}
