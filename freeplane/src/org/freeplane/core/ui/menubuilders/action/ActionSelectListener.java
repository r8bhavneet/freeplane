package org.freeplane.core.ui.menubuilders.action;

import org.freeplane.core.ui.AFreeplaneAction;
import org.freeplane.core.ui.menubuilders.generic.Entry;
import org.freeplane.core.ui.menubuilders.generic.EntryPopupListener;

public class ActionSelectListener implements EntryPopupListener {
	public void childEntriesWillBecomeVisible(final Entry submenu) {
		for (Entry target : submenu.children()) {
			final AFreeplaneAction action = target.getAction();
			if (action != null && action.checkSelectionOnPopup() && action.isEnabled())
				action.setSelected();
		}
	}

	public void childEntriesWillBecomeInvisible(final Entry target) {
	}
}