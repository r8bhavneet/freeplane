/*
 *  Freeplane - mind map editor
 *  Copyright (C) 2009 Dimitry
 *
 *  This file author is Dimitry
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.freeplane.main.mindmapmode.stylemode;

import java.awt.event.ActionEvent;

import javax.swing.JOptionPane;

import org.freeplane.core.ui.AFreeplaneAction;
import org.freeplane.core.ui.components.UITools;
import org.freeplane.core.undo.IActor;
import org.freeplane.core.util.TextUtils;
import org.freeplane.features.icon.mindmapmode.MIconController.Keys;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.map.mindmapmode.MMapController;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.styles.IStyle;
import org.freeplane.features.styles.LogicalStyleKeys;
import org.freeplane.features.styles.MapStyleModel;
import org.freeplane.features.styles.StyleFactory;
import org.freeplane.features.styles.StyleTranslatedObject;

/**
 * @author Dimitry Polivaev
 * 02.10.2009
 */
public class NewUserStyleAction extends AFreeplaneAction {

    private final boolean copySelectionStyle;

    public NewUserStyleAction(boolean copySelectionStyle) {
		super(copySelectionStyle ? "NewUserStyleFromSelectionAction" : "NewUserStyleAction");
        this.copySelectionStyle = copySelectionStyle;
	}

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public void actionPerformed(final ActionEvent e) {
		final String styleName = JOptionPane.showInputDialog(UITools.getCurrentRootComponent(),
				TextUtils.getText("enter_new_style_name"));
		if (styleName == null) {
			return;
		}
		final Controller controller = Controller.getCurrentController();
		final NodeModel selectedNode = controller.getSelection().getSelected();
		final MapModel map = controller.getMap();
		final MapStyleModel styleModel = MapStyleModel.getExtension(map);
		final IStyle style = StyleFactory.create(styleName);
		if (null != styleModel.getStyleNode(style)) {
			UITools.errorMessage(TextUtils.getText("style_already_exists"));
			return;
		}
		final MMapController mapController = (MMapController) Controller.getCurrentModeController().getMapController();
		final NodeModel newNode = new NodeModel(map);
        newNode.setUserObject(style);
        if(copySelectionStyle) {
            Controller.getCurrentModeController().copyExtensions(LogicalStyleKeys.NODE_STYLE, selectedNode, newNode);
            Controller.getCurrentModeController().copyExtensions(Keys.ICONS, selectedNode, newNode);
        }
		NodeModel userStyleParentNode = styleModel.getStyleNodeGroup(map, MapStyleModel.STYLES_USER_DEFINED);
		if(userStyleParentNode == null){
			userStyleParentNode = new NodeModel(map);
			userStyleParentNode.setUserObject(new StyleTranslatedObject(MapStyleModel.STYLES_USER_DEFINED));
			mapController.insertNode(userStyleParentNode, map.getRootNode(), false, false, true);

		}
		mapController.insertNode(newNode, userStyleParentNode, false, false, true);
		mapController.select(newNode);
		final IActor actor = new IActor() {
			public void undo() {
				styleModel.removeStyleNode(newNode);
			}

			public String getDescription() {
				return "NewStyle";
			}

			public void act() {
				styleModel.addStyleNode(newNode);
			}
		};
		Controller.getCurrentModeController().execute(actor, map);
	}
}
