/*
 *  Freeplane - mind map editor
 *  Copyright (C) 2008 Joerg Mueller, Daniel Polansky, Christian Foltin, Dimitry Polivaev
 *
 *  This file is modified by Dimitry Polivaev in 2008.
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
package org.freeplane.view.swing.map;

import java.awt.AWTKeyStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.dnd.Autoscroll;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.RoundRectangle2D;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.FileNotFoundException;
import java.net.URI;
import java.util.AbstractList;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.Vector;
import java.util.stream.Stream;

import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;

import org.freeplane.core.extension.Configurable;
import org.freeplane.core.extension.HighlightedElements;
import org.freeplane.core.io.xml.TreeXmlReader;
import org.freeplane.core.resources.IFreeplanePropertyListener;
import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.ui.IUserInputListenerFactory;
import org.freeplane.core.ui.components.UITools;
import org.freeplane.core.ui.svgicons.GraphicsHints;
import org.freeplane.core.util.ColorUtils;
import org.freeplane.core.util.LogUtils;
import org.freeplane.features.attribute.AttributeController;
import org.freeplane.features.attribute.ModelessAttributeController;
import org.freeplane.features.edge.EdgeColorsConfigurationFactory;
import org.freeplane.features.filter.Filter;
import org.freeplane.features.highlight.NodeHighlighter;
import org.freeplane.features.link.ConnectorModel;
import org.freeplane.features.link.ConnectorShape;
import org.freeplane.features.link.Connectors;
import org.freeplane.features.link.LinkController;
import org.freeplane.features.link.NodeLinkModel;
import org.freeplane.features.link.NodeLinks;
import org.freeplane.features.map.IMapChangeListener;
import org.freeplane.features.map.IMapSelection;
import org.freeplane.features.map.INodeChangeListener;
import org.freeplane.features.map.INodeView;
import org.freeplane.features.map.MapChangeEvent;
import org.freeplane.features.map.MapController;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeChangeEvent;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.map.NodeRelativePath;
import org.freeplane.features.map.NodeSubtrees;
import org.freeplane.features.map.SummaryNode;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.ModeController;
import org.freeplane.features.nodestyle.NodeCss;
import org.freeplane.features.nodestyle.NodeStyleController;
import org.freeplane.features.note.NoteController;
import org.freeplane.features.print.FitMap;
import org.freeplane.features.styles.LogicalStyleController.StyleOption;
import org.freeplane.features.styles.MapStyle;
import org.freeplane.features.styles.MapStyleModel;
import org.freeplane.features.styles.MapViewLayout;
import org.freeplane.features.text.TextController;
import org.freeplane.features.url.UrlManager;
import org.freeplane.view.swing.features.filepreview.IViewerFactory;
import org.freeplane.view.swing.features.filepreview.ScalableComponent;
import org.freeplane.view.swing.features.filepreview.ViewerController;
import org.freeplane.view.swing.map.MapViewScrollPane.MapViewPort;
import org.freeplane.view.swing.map.link.ConnectorView;
import org.freeplane.view.swing.map.link.EdgeLinkView;
import org.freeplane.view.swing.map.link.ILinkView;

/**
 * This class represents the view of a whole MindMap (in analogy to class
 * JTree).
 */
public class MapView extends JPanel implements Printable, Autoscroll, IMapChangeListener, IFreeplanePropertyListener, Configurable {

	private static final int ROOT_NODE_COMPONENT_INDEX = 0;
	private static final String UNFOLD_ON_NAVIGATION = "unfold_on_navigation";
	private final MapScroller mapScroller;
	private MapViewLayout layoutType;
	private boolean paintConnectorsBehind;
	private Filter filter;

	public static boolean isElementHighlighted(final Component c, final Object element) {
		final MapView mapView = (MapView) SwingUtilities.getAncestorOfClass(MapView.class, c);
		if (mapView == null)
			return false;
		final HighlightedElements highlightedElements = mapView.getExtension(HighlightedElements.class);
		if (highlightedElements == null)
			return false;
		else {
			return highlightedElements.isContained(element);
		}
	}

	public MapViewLayout getLayoutType() {
		return layoutType;
	}

	protected void setLayoutType(final MapViewLayout layoutType) {
		if(this.layoutType != layoutType) {
			this.layoutType = layoutType;
			if(outlineViewFitsWindowWidth())
				currentRootView.updateAll();
		}
	}

	private boolean showNotes;

	boolean showNotes() {
		return showNotes;
	}

	private void setShowNotes() {
		final boolean showNotes= NoteController.getController(getModeController()).showNotesInMap(getModel());
		if(this.showNotes == showNotes){
			return;
		}
		this.showNotes = showNotes;
		getRoot().updateAll();
	}

	private PaintingMode paintingMode = null;

	@Override
	public void refresh() {
		repaint();
	}

	private class MapSelection implements IMapSelection {
		@Override
		public void centerNode(final NodeModel node) {
			final boolean slowScroll = false;
			centerNode(node, slowScroll);
		}

		@Override
		public void centerNodeSlowly(final NodeModel node) {
			final boolean slowScroll = true;
			centerNode(node, slowScroll);
		}

		private void centerNode(final NodeModel node, final boolean slowScroll) {
			final NodeView nodeView = getNodeView(node);
			if (nodeView != null) {
				mapScroller.scrollNode(nodeView, ScrollingDirective.SCROLL_NODE_TO_CENTER, slowScroll);
			}
		}

		@Override
		public void moveNodeTo(final NodeModel node, final NodePosition position) {
			final boolean slowScroll = false;
			moveNodeTo(node, position, slowScroll);
		}

		@Override
		public void slowlyMoveNodeTo(final NodeModel node, final NodePosition position) {
			final boolean slowScroll = true;
			moveNodeTo(node, position, slowScroll);
		}

		private void moveNodeTo(final NodeModel node, final NodePosition position, final boolean slowScroll) {
			final NodeView nodeView = getNodeView(node);
			if (nodeView != null) {
				mapScroller.scrollNode(nodeView, ScrollingDirective.of(position), slowScroll);
			}
		}

		@Override
		public NodeModel getSelected() {
			final NodeView selected = MapView.this.getSelected();
			return selected != null ? selected.getModel() : null;
		}

		@Override
		public NodeModel getSelectionRoot() {
			final NodeView root = MapView.this.getRoot();
			return root != null ? root.getModel() : null;
		}

		@Override
		public Set<NodeModel> getSelection() {
			return getSelectedNodes();
		}


		@Override
		public List<NodeModel> getOrderedSelection() {
			return getOrderedSelectedNodes();
        }
		@Override
		public List<NodeModel> getSortedSelection(final boolean differentSubtrees) {
			return getSelectedNodesSortedByY(differentSubtrees);
		}

		@Override
		public boolean isSelected(final NodeModel node) {
			if (! getModel().equals(node.getMap()))
				return false;
			final NodeView nodeView = getNodeView(node);
			return nodeView != null && MapView.this.isSelected(nodeView);
		}

		@Override
		public void preserveRootNodeLocationOnScreen() {
            MapView.this.preserveRootNodeLocationOnScreen();
		}

        @Override
        public void preserveSelectedNodeLocationOnScreen() {
            MapView.this.preserveSelectedNodeLocation();
        }

        @Override
        public void preserveNodeLocationOnScreen(NodeModel node) {
            final NodeView nodeView = getNodeView(node);
            MapView.this.preserveNodeLocationOnScreen(nodeView);
        }

        @Override
        public void preserveNodeLocationOnScreen(final NodeModel node, final float horizontalPoint, final float verticalPoint) {
            final NodeView nodeView = getNodeView(node);
            MapView.this.preserveNodeLocationOnScreen(nodeView, horizontalPoint, verticalPoint);
        }

		@Override
		public void scrollNodeTreeToVisible(final NodeModel  node) {
			final NodeView nodeView = getNodeView(node);
			if(nodeView != null)
				mapScroller.scrollNodeTreeToVisible(nodeView);
		}


		@Override
		public void makeTheSelected(final NodeModel node) {
			final NodeView nodeView = getNodeView(node);
			if (nodeView != null) {
				addSelected(nodeView, false);
			}
		}

		@Override
		public void scrollNodeToVisible(final NodeModel node) {
			mapScroller.scrollNodeToVisible(getNodeView(node));
		}

		@Override
		public void selectAsTheOnlyOneSelected(final NodeModel node) {
			if(node.isVisible(filter))
				display(node);
			final NodeView nodeView = getNodeView(node);
			if (nodeView != null) {
				MapView.this.selectAsTheOnlyOneSelected(nodeView);
			}
		}

		@Override
		public void selectBranch(final NodeModel node, final boolean extend) {
			if(! extend)
				selectAsTheOnlyOneSelected(node);
			addBranchToSelection(getNodeView(node));
		}

		@Override
		public void selectContinuous(final NodeModel node) {
			MapView.this.selectContinuous(getNodeView(node));
		}

		@Override
		public void selectRoot() {
			final NodeModel rootNode = currentRootView.getModel();
			selectAsTheOnlyOneSelected(rootNode);
			mapScroller.scrollToRootNode();
		}

		@Override
		public void setSiblingMaxLevel(final int nodeLevel) {
			MapView.this.setSiblingMaxLevel(nodeLevel);
		}

		@Override
		public int size() {
			return getSelection().size();
		}

		@Override
		public void toggleSelected(final NodeModel node) {
			display(node);
			MapView.this.toggleSelected(getNodeView(node));
		}

        @Override
		public void replaceSelection(final NodeModel[] nodes) {
            if(nodes.length == 0)
                return;
            final ArrayList<NodeView> views = new ArrayList<NodeView>(nodes.length);
            for(final NodeModel node : nodes) {
            	if(node != null && node.isVisible(filter)){
            		display(node);
            		final NodeView nodeView = getNodeView(node);
            		if (nodeView != null) {
            			views.add(nodeView);
            		}
            	}
            }
            if(! views.isEmpty())
            	MapView.this.replaceSelection(views.toArray(new NodeView[]{}));
        }

		@Override
		public List<String> getOrderedSelectionIds() {
			final List<NodeModel> orderedSelection = getOrderedSelection();
			final ArrayList<String> ids = new ArrayList<>(orderedSelection.size());
			for(final NodeModel node :orderedSelection)
				ids.add(node.getID());
			return ids;
		}

	    @Override
        public Filter getFilter() {
	        return filter;
	    }

	    @Override
        public void setFilter(Filter filter) {
	        MapView.this.filter = filter;
	    }


	}

	private class Selection {
		final private Set<NodeView> selectedSet = new LinkedHashSet<NodeView>();
		final private List<NodeView> selectedList = new ArrayList<NodeView>();
		private NodeView selectedNode = null;
		private NodeView selectionStart = null;
		private NodeView selectionEnd = null;

		public Selection() {
		}

		private void select(final NodeView node) {
			final NodeView[] oldSelecteds = selection.toArray();
			clear();
			selectedSet.add(node);
			selectedList.add(node);
			selectedNode = node;
			selectionEnd = selectionStart = node;
			addSelectionForHooks(node);
			onSelectionChange(node);
			for (final NodeView oldSelected : oldSelecteds) {
				if (oldSelected != null && oldSelected != node) {
					onSelectionChange(oldSelected);
				}
			}
		}

		private boolean add(final NodeView node) {
			if(selectedNode == null){
				select(node);
				return true;
			}
			else{
				if(selectedSet.add(node)){
					selectedList.add(node);
					onSelectionChange(node);
					return true;
				}
				return false;
			}
		}

		private void addSelectionForHooks(final NodeView node) {
			if(! isSelected())
				return;
			final ModeController modeController = getModeController();
			final MapController mapController = modeController.getMapController();
			final NodeModel model = node.getModel();
			mapController.onSelect(model);
		}

		private void clear() {
			if (selectedNode != null) {
				removeSelectionForHooks(selectedNode);
				selectedNode = null;
				selectedSet.clear();
				selectedList.clear();
				selectionEnd = selectionStart = null;
			}
		}

		private boolean contains(final NodeView node) {
			return selectedSet.contains(node);
		}

		public Set<NodeView> getSelection() {
			return Collections.unmodifiableSet(selectedSet);
		}

		private boolean deselect(final NodeView node) {
			if(selectionStart == node)
				selectionEnd = selectionStart = null;
			else if (selectionEnd  == node)
				selectionEnd = selectionStart;
			final boolean selectedChanged = selectedNode != null && selectedNode.equals(node);
			if (selectedChanged) {
				removeSelectionForHooks(node);
			}
			if (selectedSet.remove(node)){
				final int last = selectedList.size() - 1;
				if(selectedList.get(last) .equals(node))
					selectedList.remove(last);
				else
					selectedList.remove(node);
				onSelectionChange(node);
				if(selectedChanged) {
	                if (size() > 0) {
	                	selectedNode = selectedSet.iterator().next();
	                	addSelectionForHooks(selectedNode);
	                }
	                else{
	                	selectedNode = null;
	                }
                }
				return true;
			}
			return false;
		}

		private void removeSelectionForHooks(final NodeView node) {
			if (node.getModel() == null || ! isSelected()) {
				return;
			}
			getModeController().getMapController().onDeselect(node.getModel());
		}

		private int size() {
			return selectedSet.size();
		}

		private void replace(final NodeView[] newSelection) {
            if(newSelection.length == 0)
                return;
            final boolean selectedChanges = ! newSelection[0].equals(selectedNode);
            if (selectedChanges) {
            	if(selectedNode != null)
            		removeSelectionForHooks(selectedNode);
            	selectedNode = newSelection[0];
            }
            NodeView[] nodesAddedToSelection = Stream.of(newSelection)
                .filter(view -> ! selectedSet.contains(view))
                .toArray(NodeView[]::new);
            final NodeView[] oldSelection = selectedSet.toArray(new NodeView[selectedSet.size()]);
            selectedSet.clear();
            selectedList.clear();
            for(final NodeView view : newSelection)
                if (selectedSet.add(view))
                	selectedList.add(view);
			if(!selectedSet.contains(selectionStart))
				selectionEnd = selectionStart = selectedNode;
			else if (!selectedSet.contains(selectionEnd))
				selectionEnd = selectionStart;

            for(final NodeView view : nodesAddedToSelection)
                onSelectionChange(view);
            if (selectedChanges) {
                addSelectionForHooks(selectedNode);
            }
            for(final NodeView view : oldSelection)
                if (!selectedSet.contains(view))
                	onSelectionChange(view);
        }

		public NodeView[] toArray() {
	        return selectedList.toArray(new NodeView[selectedList.size()]);
        }

		private List<NodeView> getSelectedList() {
	        return selectedList;
        }

		private Set<NodeView> getSelectedSet() {
	        return selectedSet;
        }

		public NodeView getSelectionStart() {
			return selectionStart;
		}

		public void setSelectionStart(final NodeView node) {
			selectionEnd = selectionStart = node;
		}
		public NodeView getSelectionEnd() {
			return selectionEnd;
		}

		public void setSelectionEnd(final NodeView selectionEnd) {
			this.selectionEnd = selectionEnd;
		}

	}

	private static final int margin = 20;
	static boolean printOnWhiteBackground;
	static private IFreeplanePropertyListener propertyChangeListener;
	public static final String RESOURCES_SELECTED_NODE_COLOR = "standardselectednodecolor";
	public static final String RESOURCES_SELECTED_NODE_RECTANGLE_COLOR = "standardselectednoderectanglecolor";
	private static final String SPOTLIGHT_BACKGROUND_COLOR = "spotlight_background_color";
	private static final String PRESENTATION_DIMMER_TRANSPARENCY = "presentation_dimmer_transparency";
	private static final String HIDE_SINGLE_END_CONNECTORS = "hide_single_end_connectors".intern();
	private static final String SHOW_CONNECTORS_PROPERTY = "show_connectors".intern();
	private static final String SHOW_CONNECTOR_LINES = "true".intern();
	private static final String HIDE_CONNECTOR_LINES = "false".intern();
	private static final String SOME_CONNECTORS_PROPERTY = "connector_";

	private static final String HIDE_CONNECTORS = "never".intern();
	private static final String SHOW_CONNECTORS_FOR_SELECTION = "for_selection".intern();
	private static final String SHOW_ICONS_PROPERTY = "show_icons";
	private static final String OUTLINE_VIEW_FITS_WINDOW_WIDTH = "outline_view_fits_window_width";
	private static final String OUTLINE_HGAP_PROPERTY = "outline_hgap";

	static private final PropertyChangeListener repaintOnClientPropertyChangeListener = new PropertyChangeListener() {
		@Override
		public void propertyChange(final PropertyChangeEvent evt) {
			final MapView source = (MapView) evt.getSource();
			source.repaint();
		}
	};

	private static final long serialVersionUID = 1L;
	static private boolean drawsRectangleForSelection;
	static private Color selectionRectangleColor;
	/** Used to identify a right click onto a link curve. */
	private Vector<ILinkView> arrowLinkViews;
	private Color background = null;
	private JComponent backgroundComponent;
	private Rectangle boundingRectangle = null;
	private FitMap fitMap = FitMap.USER_DEFINED;
	private boolean isPreparedForPrinting = false;
	private boolean isPrinting = false;
	private final ModeController modeController;
	final private MapModel model;

	private NodeView currentRootView = null;
	private NodeView currentRootParentView = null;
	private NodeView mapRootView = null;

	private boolean selectedsValid = true;
	final private Selection selection = new Selection();
	private int siblingMaxLevel;
	private float zoom = 1F;
	private Font noteFont;
    private Font detailFont;
    private int detailHorizontalAlignment;
    private Color detailForeground;
    private Color detailBackground;
    private NodeCss detailCss;
    private int noteHorizontalAlignment;
    private Color noteForeground;
    private Color noteBackground;
    private NodeCss noteCss;
	private static String showConnectorsPropertyValue;
	private static boolean hideSingleEndConnectorsPropertyValue;
	private String showConnectors;
	private boolean hideSingleEndConnectors;
	private static boolean showIcons;
	private boolean fitToViewport;
	private static Color spotlightBackgroundColor;
	private static int outlineHGap;
	private static boolean outlineViewFitsWindowWidth;

	final private ComponentAdapter viewportSizeChangeListener;
	private final INodeChangeListener connectorChangeListener;
	private boolean allowsCompactLayout;
	private static final String INLINE_EDITOR_ACTIVE = "inline_editor_active";
    public static final String SPOTLIGHT_ENABLED = "spotlight";
	private boolean scrollsViewAfterLayout = true;
	

	static {
	    final ResourceController resourceController = ResourceController.getResourceController();
	    final String drawCircle = resourceController.getProperty(
	            ResourceController.RESOURCE_DRAW_RECTANGLE_FOR_SELECTION);
	    MapView.drawsRectangleForSelection = TreeXmlReader.xmlToBoolean(drawCircle);
	    final String printOnWhite = resourceController
	            .getProperty("printonwhitebackground");
	    MapView.printOnWhiteBackground = TreeXmlReader.xmlToBoolean(printOnWhite);
	    final int alpha = 255 - resourceController.getIntProperty(PRESENTATION_DIMMER_TRANSPARENCY, 0x70);
	    resourceController.setDefaultProperty(SPOTLIGHT_BACKGROUND_COLOR, ColorUtils.colorToRGBAString(new Color(0, 0, 0, alpha)));
	    spotlightBackgroundColor = resourceController.getColorProperty(SPOTLIGHT_BACKGROUND_COLOR);
	    hideSingleEndConnectorsPropertyValue = resourceController.getBooleanProperty(HIDE_SINGLE_END_CONNECTORS);
	    showConnectorsPropertyValue = resourceController.getProperty(SHOW_CONNECTORS_PROPERTY).intern();
	    showIcons = resourceController.getBooleanProperty(SHOW_ICONS_PROPERTY);
	    outlineHGap = resourceController.getLengthProperty(OUTLINE_HGAP_PROPERTY);
	    outlineViewFitsWindowWidth = resourceController.getBooleanProperty(OUTLINE_VIEW_FITS_WINDOW_WIDTH);

	    createPropertyChangeListener();
	}

	public MapView(final MapModel model, final ModeController modeController) {
		super();
		this.model = model;
		this.modeController = modeController;
		mapScroller = new MapScroller(this);
		filter = Filter.createTransparentFilter();
		final String name = model.getTitle();
		setName(name);
		setAutoscrolls(true);
		setLayout(new MindMapLayout());
		final NoteController noteController = NoteController.getController(getModeController());
		showNotes= noteController != null && noteController.showNotesInMap(getModel());
        updateContentStyle();
        initRoot();
		setBackground(requiredBackground());
		final MapStyleModel mapStyleModel = MapStyleModel.getExtension(model);
		zoom = mapStyleModel.getZoom();
		layoutType = mapStyleModel.getMapViewLayout();
		final IUserInputListenerFactory userInputListenerFactory = getModeController().getUserInputListenerFactory();
		addMouseListener(userInputListenerFactory.getMapMouseListener());
		addMouseMotionListener(userInputListenerFactory.getMapMouseListener());
		addMouseWheelListener(userInputListenerFactory.getMapMouseWheelListener());
		setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, emptyNodeViewSet());
		setFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS, emptyNodeViewSet());
		setFocusTraversalKeys(KeyboardFocusManager.UP_CYCLE_TRAVERSAL_KEYS, emptyNodeViewSet());
		viewportSizeChangeListener = new ComponentAdapter() {
		    boolean firstRun = true;
			@Override
			public void componentResized(final ComponentEvent e) {
			    if(firstRun) {
			        loadBackgroundImage();
			        firstRun = false;
			    }
				if (fitToViewport) {
					adjustBackgroundComponentScale();
				}
				if(usesLayoutSpecificMaxNodeWidth()) {
					currentRootView.updateAll();
					repaint();
				}
			}
		};
		final MapStyle mapStyle = getModeController().getExtension(MapStyle.class);
		final String fitToViewportAsString = mapStyle.getPropertySetDefault(model, MapStyle.FIT_TO_VIEWPORT);
		fitToViewport = Boolean.parseBoolean(fitToViewportAsString);
		allowsCompactLayout = mapStyle.allowsCompactLayout(model);
		connectorChangeListener = new INodeChangeListener() {
			@Override
			public void nodeChanged(final NodeChangeEvent event) {
				if(NodeLinks.CONNECTOR.equals(event.getProperty()) &&
						event.getNode().getMap().equals(getModel()))
					repaint();
			}
		};
		addPropertyChangeListener(SPOTLIGHT_ENABLED, repaintOnClientPropertyChangeListener);
		if(ResourceController.getResourceController().getBooleanProperty("activateSpotlightByDefault"))
		    putClientProperty(SPOTLIGHT_ENABLED, Boolean.TRUE);
	}

    public void replaceSelection(final NodeView[] views) {
        selection.replace(views);
        if(views.length > 0)
        	views[0].requestFocusInWindow();
    }

    // generics trickery
	private Set<AWTKeyStroke> emptyNodeViewSet() {
	    return Collections.emptySet();
    }

	/*
	 * (non-Javadoc)
	 * @see java.awt.dnd.Autoscroll#autoscroll(java.awt.Point)
	 */
	@Override
	public void autoscroll(final Point cursorLocn) {
		final Rectangle r = new Rectangle((int) cursorLocn.getX() - MapView.margin, (int) cursorLocn.getY()
		        - MapView.margin, 1 + 2 * MapView.margin, 1 + 2 * MapView.margin);
		scrollRectToVisible(r);
	}

	boolean frameLayoutCompleted() {
		final Frame frame = JOptionPane.getFrameForComponent(this);
		final Insets frameInsets = frame.getInsets();
		final Component rootPane = frame.getComponent(0);
		final boolean frameLayoutCompleted = rootPane.getWidth() == frame.getWidth() - frameInsets.left - frameInsets.right
				&& rootPane.getHeight() == frame.getHeight() - frameInsets.top - frameInsets.bottom;
		return frameLayoutCompleted;
	}


	@Override
    public void addNotify() {
	    super.addNotify();
	    modeController.getMapController().addUINodeChangeListener(connectorChangeListener);
	    getParent().addComponentListener(viewportSizeChangeListener);
    }

	@Override
    public void removeNotify() {
		modeController.getMapController().removeNodeChangeListener(connectorChangeListener);
		getParent().removeComponentListener(viewportSizeChangeListener);
	    super.removeNotify();
    }

	boolean isLayoutCompleted() {
	    final JViewport viewPort = (JViewport) getParent();
		final Dimension visibleDimension = viewPort.getExtentSize();
		return visibleDimension.width > 0;
    }

	static private void createPropertyChangeListener() {
		MapView.propertyChangeListener = new IFreeplanePropertyListener() {
			@Override
			public void propertyChanged(final String propertyName, final String newValue, final String oldValue) {
				final Component c = Controller.getCurrentController().getMapViewManager().getMapViewComponent();
				if (!(c instanceof MapView)) {
					return;
				}
				final MapView mapView = (MapView) c;
				if (propertyName.equals(RESOURCES_SELECTED_NODE_COLOR)) {
					mapView.repaintSelecteds();
					return;
				}
				if (propertyName.equals(RESOURCES_SELECTED_NODE_RECTANGLE_COLOR)) {
					mapView.repaintSelecteds();
					return;
				}
				if (propertyName.equals(ResourceController.RESOURCE_DRAW_RECTANGLE_FOR_SELECTION)) {
					MapView.drawsRectangleForSelection = TreeXmlReader.xmlToBoolean(newValue);
					mapView.repaintSelecteds();
					return;
				}
				if (propertyName.equals("printonwhitebackground")) {
					MapView.printOnWhiteBackground = TreeXmlReader.xmlToBoolean(newValue);
					return;
				}
				if (propertyName.equals(SPOTLIGHT_BACKGROUND_COLOR)) {
					MapView.spotlightBackgroundColor = ColorUtils.stringToColor(newValue);
					mapView.repaint();
					return;
				}
				if (propertyName.equals(HIDE_SINGLE_END_CONNECTORS)) {
					MapView.hideSingleEndConnectorsPropertyValue = ResourceController.getResourceController().getBooleanProperty(HIDE_SINGLE_END_CONNECTORS);
					mapView.repaint();
					return;
				}
                if (propertyName.equals(SHOW_CONNECTORS_PROPERTY)) {
                    MapView.showConnectorsPropertyValue = ResourceController.getResourceController().getProperty(SHOW_CONNECTORS_PROPERTY).intern();
                    mapView.repaint();
                    return;
                }
                if (propertyName.startsWith(SOME_CONNECTORS_PROPERTY)) {
                    MapView.showConnectorsPropertyValue = ResourceController.getResourceController().getProperty(SHOW_CONNECTORS_PROPERTY).intern();
                    mapView.repaint();
                    return;
                }
				if (propertyName.equals(SHOW_ICONS_PROPERTY)) {
					MapView.showIcons = ResourceController.getResourceController().getBooleanProperty(SHOW_ICONS_PROPERTY);
					mapView.updateIconsRecursively(mapView.getRoot());
					mapView.repaint();
					return;
				}
				if (propertyName.equals(OUTLINE_HGAP_PROPERTY)) {
					MapView.outlineHGap = ResourceController.getResourceController().getLengthProperty(OUTLINE_HGAP_PROPERTY);
					if (mapView.isOutlineLayoutSet()) {
						mapView.getRoot().updateAll();
						mapView.repaint();
					}
					return;
				}

				if(propertyName.equals(OUTLINE_VIEW_FITS_WINDOW_WIDTH)) {
					outlineViewFitsWindowWidth = ResourceController.getResourceController().getBooleanProperty(OUTLINE_VIEW_FITS_WINDOW_WIDTH);
					if (mapView.isOutlineLayoutSet()) {
						mapView.getRoot().updateAll();
						mapView.repaint();
					}
					return;
				}
			}
		};
		ResourceController.getResourceController().addPropertyChangeListener(MapView.propertyChangeListener);
	}

	public void deselect(final NodeView newSelected) {
		if (selection.contains(newSelected) && selection.deselect(newSelected) && newSelected.getParent() != null) {
			onSelectionChange(newSelected);
		}
	}

	private void onSelectionChange(final NodeView node) {
		if(! node.isShowing())
			return;
		node.update();
		if(SHOW_CONNECTORS_FOR_SELECTION == showConnectors)
			repaint(getVisibleRect());
		else
			node.repaintSelected();
	}

    public Object detectView(final Point p) {
        if (arrowLinkViews == null) {
            return null;
        }
        for (int i = 0; i < arrowLinkViews.size(); ++i) {
            final ILinkView arrowView = arrowLinkViews.get(i);
            if (arrowView.detectCollision(p, true)) {
                return arrowView;
            }
        }
        for (int i = 0; i < arrowLinkViews.size(); ++i) {
            final ILinkView arrowView = arrowLinkViews.get(i);
            if (arrowView.detectCollision(p, false)) {
                return arrowView;
            }
        }
        return null;
    }

    public Object detectObject(final Point p) {
        Object view = detectView(p);
        if(view instanceof ILinkView)
            return ((ILinkView)view).getModel();
        return null;
    }

	/**
	 * Call preparePrinting() before printing and endPrinting() after printing
	 * to minimize calculation efforts
	 */
	public void endPrinting() {
		if (!isPreparedForPrinting)
			return;
		isPrinting = false;
		updatePrintedNodes();
		isPreparedForPrinting = false;
		if (MapView.printOnWhiteBackground) {
			setBackground(background);
		}
	}

	/*
	 * (non-Javadoc)
	 * @see java.awt.dnd.Autoscroll#getAutoscrollInsets()
	 */
	@Override
	public Insets getAutoscrollInsets() {
		final Container parent = getParent();
		if (parent == null) {
			return new Insets(0, 0, 0, 0);
		}
		final Rectangle outer = getBounds();
		final Rectangle inner = parent.getBounds();
		return new Insets(inner.y - outer.y + MapView.margin, inner.x - outer.x + MapView.margin, outer.height
		        - inner.height - inner.y + outer.y + MapView.margin, outer.width - inner.width - inner.x + outer.x
		        + MapView.margin);
	}

	public Rectangle getInnerBounds() {
		final Rectangle innerBounds = currentRootView.getBounds();
		final Rectangle maxBounds = new Rectangle(0, 0, getWidth(), getHeight());
		if(arrowLinkViews != null)
			for (int i = 0; i < arrowLinkViews.size(); ++i) {
				final ILinkView arrowView = arrowLinkViews.get(i);
				arrowView.increaseBounds(innerBounds);
			}
		return innerBounds.intersection(maxBounds);
	}

	public IMapSelection getMapSelection() {
		return new MapSelection();
	}

	public ModeController getModeController() {
		return modeController;
	}

	public MapModel getModel() {
		return model;
	}

	public Point getNodeContentLocation(final NodeView nodeView) {
		final Point contentXY = new Point(0, 0);
		UITools.convertPointToAncestor(nodeView.getContent(), contentXY, this);
		return contentXY;
	}

	private NodeView getNodeView(final Object o) {
        if(! (o instanceof NodeModel))
			return null;
		final NodeView nodeView = getNodeView((NodeModel)o);
		return nodeView;
    }

	private NodeView getDisplayedNodeView(NodeModel node) {
		NodeView nodeView = getNodeView(node);
		return currentRootView == mapRootView 
				||  nodeView != null && isAncestorOf(nodeView) ? nodeView : null;
	}

	public NodeView getNodeView(final NodeModel node) {
		if (node == null) {
			return null;
		}
		for (final INodeView iNodeView : node.getViewers()) {
			if(! (iNodeView instanceof NodeView)){
				continue;
			}
			final NodeView candidateView = (NodeView) iNodeView;
			if (candidateView.getMap() == this) {
				return candidateView;
			}
		}
		final NodeView root = getRoot();
		if(root.getModel().equals(node))
			return root;
		else
			return null;
	}

	/*
	 * (non-Javadoc)
	 * @see javax.swing.JComponent#getPreferredSize()
	 */
	@Override
	public Dimension getPreferredSize() {
		return getLayout().preferredLayoutSize(this);
	}

	public NodeView getRoot() {
		return currentRootView;
	}

	public NodeView getSelected() {
		if(! selectedsValid) {
			final NodeView node = selection.selectedNode;
	        if (node == null || ! SwingUtilities.isDescendingFrom(node, this))
		        validateSelecteds();
            else {
	            final JComponent content = node.getContent();
	            if (content == null || ! content.isVisible())
	                validateSelecteds();
            }
        }
		return selection.selectedNode;
	}

	public Set<NodeModel> getSelectedNodes() {
		validateSelecteds();
		return new AbstractSet<NodeModel>() {

			@Override
			public int size() {
				return selection.size();
			}

			@Override
            public boolean contains(final Object o) {
                final NodeView nodeView = getNodeView(o);
                if(nodeView == null)
                	return false;
                return selection.contains(nodeView);
            }

			@Override
            public boolean add(final NodeModel o) {
				final NodeView nodeView = getNodeView(o);
				if(nodeView == null)
					return false;
				return selection.add(nodeView);
            }

			@Override
            public boolean remove(final Object o) {
				final NodeView nodeView = getNodeView(o);
				if(nodeView == null)
					return false;
				return selection.deselect(nodeView);
            }

			@Override
            public Iterator<NodeModel> iterator() {
				return new Iterator<NodeModel>() {
					final Iterator<NodeView> i = selection.getSelectedSet().iterator();

					@Override
					public boolean hasNext() {
	                    return i.hasNext();
                    }

					@Override
					public NodeModel next() {
	                    return i.next().getModel();
                    }

					@Override
					public void remove() {
	                    i.remove();
                    }

				};
            }
		};
	}

	public List<NodeModel> getOrderedSelectedNodes() {
		validateSelecteds();
		return new AbstractList<NodeModel>(){

			@Override
            public boolean add(final NodeModel o) {
				final NodeView nodeView = getNodeView(o);
				if(nodeView == null)
					return false;
				return selection.add(nodeView);
            }



			@Override
            public boolean contains(final Object o) {
                final NodeView nodeView = getNodeView(o);
                if(nodeView == null)
                	return false;
                return selection.contains(nodeView);
            }



			@Override
            public boolean remove(final Object o) {
				final NodeView nodeView = getNodeView(o);
				if(nodeView == null)
					return false;
				return selection.deselect(nodeView);
            }

			@Override
            public NodeModel get(final int index) {
	            return selection.getSelectedList().get(index).getModel();
            }

			@Override
            public int size() {
	            return selection.size();
            }
		};
    }

	/**
	 * @param differentSubtrees
	 * @return an ArrayList of MindMapNode objects. If both ancestor and
	 *         descendant node are selected, only the ancestor ist returned
	 */
	ArrayList<NodeModel> getSelectedNodesSortedByY(final boolean differentSubtrees) {
		validateSelecteds();
		final TreeSet<NodeModel> sortedNodes = new TreeSet<NodeModel>(NodeRelativePath.comparator());
		for (final NodeView view : selection.getSelectedSet()) {
			if (! ( differentSubtrees  && viewBelongsToSelectedSubtreeOrItsClone(view))) {
				sortedNodes.add(view.getModel());
			}
		}
		if(differentSubtrees){
			return NodeSubtrees.getUniqueSubtreeRoots(sortedNodes);
		}
		else
			return new ArrayList<NodeModel>(sortedNodes);
	}

	private boolean viewBelongsToSelectedSubtreeOrItsClone(final NodeView view) {
		final HashSet<NodeModel> selectedNodesWithClones = new HashSet<NodeModel>();
		for (final NodeView selectedView : selection.getSelectedList())
			for(final NodeModel clone : selectedView.getModel().subtreeClones())
				selectedNodesWithClones.add(clone);

	    for (Component parent = view.getParent(); parent instanceof NodeView; parent = parent.getParent()) {
	    	if (selectedNodesWithClones.contains(((NodeView)parent).getModel())) {
	    		return true;
	    	}
	    }
	    return false;
    }

	/**
	 * @return
	 */
	public Collection<NodeView> getSelection() {
		validateSelecteds();
		return selection.getSelection();
	}

	public int getSiblingMaxLevel() {
		return siblingMaxLevel;
	}

	/**
	 * Returns the size of the visible part of the view in view coordinates.
	 */
	public Dimension getViewportSize() {
		final JViewport mapViewport = (JViewport) getParent();
		return mapViewport == null ? null : mapViewport.getSize();
	}

	private NodeView getVisibleLeft(final NodeView oldSelected) {
		NodeView newSelected = oldSelected;
		if (isRoot(oldSelected)) {
			newSelected = oldSelected.getPreferredVisibleChild(isOutlineLayoutSet(), true);
		}
		else if (!oldSelected.isLeft()) {
			newSelected = getVisibleSummarizedOrParentView(oldSelected);
		}
		else {
			if (oldSelected.isFolded() && unfoldsOnNavigation()) {
				getModeController().getMapController().unfold(oldSelected.getModel(), filter);
				return oldSelected;
			}
			newSelected = oldSelected.getPreferredVisibleChild(isOutlineLayoutSet(), true);
			while (newSelected != null && !newSelected.getModel().hasVisibleContent(filter)) {
				newSelected = newSelected.getPreferredVisibleChild(isOutlineLayoutSet(), true);
			}
			if(newSelected == null)
				newSelected = getVisibleSummaryView(oldSelected);
		}
		return newSelected;
	}

	private boolean unfoldsOnNavigation() {
		return ResourceController.getResourceController().getBooleanProperty(UNFOLD_ON_NAVIGATION);
	}

	boolean isOutlineLayoutSet() {
		return layoutType.equals(MapViewLayout.OUTLINE);
	}

	protected NodeView getVisibleSummarizedOrParentView(final NodeView view) {
			return view.getVisibleSummarizedOrParentView();
	}

	private NodeView getVisibleSummaryView(final NodeView node) {
	    if(isRoot(node))
	    	return null;
	    final int currentSummaryLevel = SummaryNode.getSummaryLevel(node.getModel());
		int level = currentSummaryLevel;
		final int requiredSummaryLevel = level + 1;
	    final NodeView parent = node.getParentView();
	    for (int i = 1 + getIndex(node);i < parent.getComponentCount();i++){
	    	final Component component = parent.getComponent(i);
	    	if(! (component instanceof NodeView))
	    		break;
	    	final NodeView next = (NodeView) component;
	    	if(next.isLeft() != node.isLeft())
	    		continue;
	    	if(next.isSummary())
	    		level++;
	    	else
	    		level = 0;
	    	if(level == requiredSummaryLevel){
	    		if(next.getModel().hasVisibleContent(filter))
	    			return next;
	    		final NodeView preferredVisibleChild = next.getPreferredVisibleChild(isOutlineLayoutSet(), next.isLeft());
	    		if(preferredVisibleChild != null)
	    			return preferredVisibleChild;
	    		break;
	    	}
	    	if(level == currentSummaryLevel && SummaryNode.isFirstGroupNode(next.getModel()))
	    		break;
	    }
	    return getVisibleSummaryView(parent);
    }

	int getIndex(final NodeView node) {
	    final NodeView parent = node.getParentView();
	    for(int i = 0; i < parent.getComponentCount(); i++){
	    	if(parent.getComponent(i).equals(node))
	    		return i;
	    }
	    return -1;
    }

	private NodeView getVisibleRight(final NodeView oldSelected) {
		NodeView newSelected = oldSelected;
		final NodeModel oldModel = oldSelected.getModel();
		if (isRoot(oldSelected)) {
			newSelected = oldSelected.getPreferredVisibleChild(isOutlineLayoutSet(), false);
		}
		else if (oldSelected.isLeft()) {
			newSelected = getVisibleSummarizedOrParentView(oldSelected);
		}
		else {
			if (oldSelected.isFolded() && unfoldsOnNavigation()) {
				getModeController().getMapController().unfoldAndScroll(oldModel, filter);
				if(oldSelected.getModel().hasVisibleContent(filter))
					return oldSelected;
			}
			newSelected = oldSelected.getPreferredVisibleChild(isOutlineLayoutSet(), false);
			while (newSelected != null && !newSelected.getModel().hasVisibleContent(filter)) {
				newSelected = newSelected.getPreferredVisibleChild(isOutlineLayoutSet(), false);
			}
			if(newSelected == null)
				newSelected = getVisibleSummaryView(oldSelected);
		}
		return newSelected;
	}

	public float getZoom() {
		return zoom;
	}

	public int getZoomed(final int number) {
		return (int) Math.ceil(number * zoom);
	}

	public int getZoomed(final double number) {
		return (int) Math.ceil(number * zoom);
	}


	private void initRoot() {
		mapRootView = currentRootView = NodeViewFactory.getInstance().newNodeView(getModel().getRootNode(), this, this, ROOT_NODE_COMPONENT_INDEX);
		mapScroller.setAnchorView(currentRootView);
	}

	public boolean isPrinting() {
		return isPrinting;
	}

	public boolean isSelected(final NodeView n) {
		if(isPrinting || (! selectedsValid &&
				(selection.selectedNode == null || ! SwingUtilities.isDescendingFrom(selection.selectedNode, this)  || ! selection.selectedNode.getContent().isVisible())))
			return false;
		return selection.contains(n);
	}

	/**
	 * Add the node to the selection if it is not yet there, making it the
	 * focused selected node.
	 */
	void addSelected(final NodeView newSelected, final boolean scroll) {
		if(newSelected.isContentVisible()){
			selection.add(newSelected);
			if(scroll)
				mapScroller.scrollNodeToVisible(newSelected);
		}
	}

	@Override
	public void mapChanged(final MapChangeEvent event) {
		final Object property = event.getProperty();
		if (property.equals(MapStyle.RESOURCES_BACKGROUND_COLOR)) {
			setBackground(requiredBackground());
			return;
		}
		if (property.equals(MapStyle.MAP_STYLES)){
	        // set default font for notes:
	        updateContentStyle();
	        repaint();
		}
		if (property.equals(MapStyle.MAP_STYLES) && event.getMap().equals(model)
		        || property.equals(ModelessAttributeController.ATTRIBUTE_VIEW_TYPE)
		        || property.equals(Filter.class)
		        || property.equals(UrlManager.MAP_URL)) {
			setBackground(requiredBackground());
			getRoot().updateAll();
			return;
		}
		if(property.equals(AttributeController.SHOW_ICON_FOR_ATTRIBUTES)
				||property.equals(NoteController.SHOW_NOTE_ICONS))
			updateIconsRecursively(getRoot());
		if(property.equals(NoteController.SHOW_NOTES_IN_MAP))
			setShowNotes();
		if (property.equals(MapStyle.RESOURCES_BACKGROUND_IMAGE)) {
			final String fitToViewportAsString = MapStyle.getController(modeController).getPropertySetDefault(model,
			    MapStyle.FIT_TO_VIEWPORT);
			setFitToViewport(Boolean.parseBoolean(fitToViewportAsString));
			loadBackgroundImage();
		}
		if (property.equals(MapStyle.ALLOW_COMPACT_LAYOUT)) {
			final MapStyle mapStyle = getModeController().getExtension(MapStyle.class);
			allowsCompactLayout = mapStyle.allowsCompactLayout(model);
			getRoot().updateAll();
			revalidate();
			repaint();
		}
		if (property.equals(MapStyle.FIT_TO_VIEWPORT)) {
			final String fitToViewportAsString = MapStyle.getController(modeController).getPropertySetDefault(model,
			    MapStyle.FIT_TO_VIEWPORT);
			setFitToViewport(Boolean.parseBoolean(fitToViewportAsString));
			adjustBackgroundComponentScale();
		}
		if(property.equals(EdgeColorsConfigurationFactory.EDGE_COLOR_CONFIGURATION_PROPERTY)){
			getRoot().updateAll();
			repaint();
		}
	}

	private void setFitToViewport(boolean fitToViewport) {
	    this.fitToViewport = fitToViewport;
	    updateBackground();
    }

    private void loadBackgroundImage() {
 		final MapStyle mapStyle = getModeController().getExtension(MapStyle.class);
		backgroundComponent = null;
		updateBackground();
		final URI uri = mapStyle.getBackgroundImage(model);
		if (uri != null) {
			final ViewerController vc = getModeController().getExtension(ViewerController.class);
			if(vc != null) {
				final IViewerFactory factory = vc.getViewerFactory();
				assignViewerToBackgroundComponent(factory, uri);
			}
		}
		repaint();
    }

    private void assignViewerToBackgroundComponent(final IViewerFactory factory, final URI uri) {
    	try {
			if (fitToViewport) {
			    final JViewport vp = (JViewport) getParent();
			    final Dimension viewPortSize = vp.getVisibleRect().getSize();
			    JComponent viewer = (JComponent) factory.createViewer(uri, viewPortSize, () -> getParent().repaint());
			    setBackgroundComponent(viewer);

			}
            else {
                JComponent viewer = (JComponent) factory.createViewer(uri, zoom, () -> getParent().repaint());
                setBackgroundComponent(viewer);
            }
			if(backgroundComponent == null) {
				LogUtils.warn("no viewer created for " + uri);
				return;
			}
		}
		catch (final FileNotFoundException e1) {
			LogUtils.warn(e1);
		}
		catch (final Exception e1) {
			LogUtils.severe(e1);
		}
	}

    private void setBackgroundComponent(JComponent viewer) {
        this.backgroundComponent = viewer;
        updateBackground();
    }

   private void updateBackground() {
        MapViewPort viewport = (MapViewPort) getParent();
        if(viewport != null) {
            if(fitToViewport) {
                viewport.setBackground(getBackground());
                viewport.setBackgroundComponent(backgroundComponent);
                setOpaque(backgroundComponent != null);

            } else {
                viewport.setBackgroundComponent(null);
                setOpaque(true);
            }

        }
        setOpaque(! (fitToViewport && backgroundComponent != null));
    }



   @Override
   public void setBackground(Color background) {
       super.setBackground(background);
       updateBackground();
   }

    private void updateIconsRecursively(final NodeView node) {
    	final MainView mainView = node.getMainView();
    	if(mainView == null)
    		return;
		mainView.updateIcons(node);
    	for(int i = 0; i < node.getComponentCount(); i++){
    		final Component component = node.getComponent(i);
    		if(component instanceof NodeView)
    		updateIconsRecursively((NodeView) component);
    	}
    }

	private void updateContentStyle() {
        final NodeStyleController style = Controller.getCurrentModeController().getExtension(NodeStyleController.class);
        final MapModel map = getModel();
        final MapStyleModel model = MapStyleModel.getExtension(map);
        final NodeModel detailStyleNode = model.getStyleNodeSafe(MapStyleModel.DETAILS_STYLE);
        detailFont = UITools.scale(style.getFont(detailStyleNode, StyleOption.FOR_UNSELECTED_NODE));
        detailBackground = style.getBackgroundColor(detailStyleNode, StyleOption.FOR_UNSELECTED_NODE);
        detailForeground = style.getColor(detailStyleNode, StyleOption.FOR_UNSELECTED_NODE);
        detailHorizontalAlignment = style.getHorizontalTextAlignment(detailStyleNode, StyleOption.FOR_UNSELECTED_NODE).swingConstant;
        detailCss = style.getStyleSheet(detailStyleNode, StyleOption.FOR_UNSELECTED_NODE);

        final NodeModel noteStyleNode = model.getStyleNodeSafe(MapStyleModel.NOTE_STYLE);
        noteFont = UITools.scale(style.getFont(noteStyleNode, StyleOption.FOR_UNSELECTED_NODE));
        noteBackground = style.getBackgroundColor(noteStyleNode, StyleOption.FOR_UNSELECTED_NODE);
        noteForeground = style.getColor(noteStyleNode, StyleOption.FOR_UNSELECTED_NODE);
        noteHorizontalAlignment = style.getHorizontalTextAlignment(noteStyleNode, StyleOption.FOR_UNSELECTED_NODE).swingConstant;
        noteCss = style.getStyleSheet(noteStyleNode, StyleOption.FOR_UNSELECTED_NODE);
        updateSelectionColors();
    }

	public boolean selectLeft(final boolean continious) {
		final NodeView selected = getSelected();
		final NodeView newSelected = getVisibleLeft(selected);
		return selectRightOrLeft(newSelected, continious);
    }

	private boolean selectRightOrLeft(final NodeView newSelected, final boolean continious) {
	    if (newSelected == null) {
	    	return false;
		}
		if(continious){
			if(newSelected.isParentOf(getSelected())){
				selectAsTheOnlyOneSelected(newSelected);
				addBranchToSelection(newSelected);
			}
			else{
				addBranchToSelection(getSelected());
			}
		}
		else
			selectAsTheOnlyOneSelected(newSelected);
		return true;
    }

	public boolean selectRight(final boolean continious) {
		final NodeView selected = getSelected();
		final NodeView newSelected = getVisibleRight(selected);
		return selectRightOrLeft(newSelected, continious);
    }


	public boolean selectUp(final boolean continious) {
		return selectSibling(continious, false, false);
	}

	private boolean selectSibling(final boolean continious, final boolean page, final boolean down) {
		final NodeView oldSelectionEnd = selection.getSelectionEnd();
		if(oldSelectionEnd == null)
			return false;
		NodeView nextSelected = oldSelectionEnd;
		{
			final NodeView nextVisibleSibling = getNextVisibleSibling(nextSelected, down);
			if (nextSelected == nextVisibleSibling)
				return false;
			nextSelected = nextVisibleSibling;
		}
		if(page){
			NodeView sibling = nextSelected;
			for(;;)  {
				sibling = getNextVisibleSibling(sibling, down);
				final boolean noNextNodeFound = sibling == nextSelected;
				if(noNextNodeFound
						|| sibling.getParentView() != nextSelected.getParentView()
						|| sibling.isSelected() && sibling.getParentView() != oldSelectionEnd.getParentView()
						)
					break;
				nextSelected = sibling;
			}
			if(nextSelected.isSelected() && nextSelected.getParentView() == oldSelectionEnd.getParentView())
				nextSelected = getNextVisibleSibling(nextSelected, down);
		}
		if(continious){
			final NodeView selectionStart = selection.getSelectionStart();
			selectAsTheOnlyOneSelected(selectionStart);
			final Boolean selectsDown = selectsDown(selectionStart, nextSelected);
			if(selectsDown != null){
				NodeView node = selectionStart;
				do{
					NodeView nextVisibleSibling = getNextVisibleSibling(node, selectsDown);
					if(node == nextVisibleSibling) {
						selectAsTheOnlyOneSelected(nextSelected);
						LogUtils.severe("Can not select next visible sibling in continious selection, endless loop");
						break;
					}
					node = nextVisibleSibling;
					addSelected(node, false);
				}while(node != nextSelected);
				selection.setSelectionEnd(nextSelected);
				mapScroller.scrollNodeToVisible(nextSelected);
			}
		}
		else
			selectAsTheOnlyOneSelected(nextSelected);
		return true;
    }

	private Boolean selectsDown(final NodeView first, final NodeView second) {
		if(first == second)
			return null;
		NodeView node = first;
		for(final boolean down : new boolean[]{true, false}){
			for(;;){
				final NodeView nextVisibleSibling = getNextVisibleSibling(node, down);
				if(node == nextVisibleSibling)
					break;
				node = nextVisibleSibling;
				if(node == second)
					return down;
			}
		}
		return null;
    }

	public NodeView getNextVisibleSibling(final NodeView node, final boolean down) {
	    return down ? node.getNextVisibleSibling() : node.getPreviousVisibleSibling();
    }

	public boolean selectDown(final boolean continious) {
		return selectSibling(continious, false, true);
	}

	public boolean selectPageDown(final boolean continious) {
		return selectSibling(continious, true, true);
    }

	public boolean selectPageUp(final boolean continious) {
		return selectSibling(continious, true, false);
    }

	/*****************************************************************
	 ** P A I N T I N G **
	 *****************************************************************/
	/*
	 * (non-Javadoc)
	 * @see javax.swing.JComponent#paint(java.awt.Graphics)
	 */
	@Override
	public void paint(final Graphics g) {
		if (!isPrinting && isPreparedForPrinting){
			isPreparedForPrinting = false;
			EventQueue.invokeLater(new Runnable() {
				@Override
				public void run() {
					endPrinting();
					repaint();
				}
			});
			return;
		}

		final Graphics2D g2 = (Graphics2D) g.create();
		try {
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
			if(! isPrinting && g2.getRenderingHint(GraphicsHints.CACHE_ICONS) != null) {
				g2.setRenderingHint(GraphicsHints.CACHE_ICONS, Boolean.TRUE);
			}
			Controller.getCurrentController().getMapViewManager().setTextRenderingHint(g2);
			if (containsExtension(Connectors.class)){
				hideSingleEndConnectors = false;
				showConnectors = SHOW_CONNECTOR_LINES;
				paintConnectorsBehind = false;
			}
			else {
				hideSingleEndConnectors = hideSingleEndConnectorsPropertyValue;
				showConnectors = showConnectorsPropertyValue;
				paintConnectorsBehind = ResourceController.getResourceController().getBooleanProperty(
						"paint_connectors_behind");
			}
			super.paint(g2);
		}
		finally {
			paintingMode = null;
			g2.dispose();
		}
	}

	public void paintOverview(Graphics2D g) {
		g.setRenderingHint(GraphicsHints.CACHE_ICONS, Boolean.FALSE);
		isPrinting = true;
		isPreparedForPrinting = true;
		updatePrintedSelectedNodes();
		super.print(g);
		isPrinting = false;
		updatePrintedSelectedNodes();
		isPreparedForPrinting = false;
	}

	@Override
	protected void paintComponent(final Graphics g) {
		super.paintComponent(g);
		if (backgroundComponent != null && ! fitToViewport) {
			paintBackgroundComponent(g);
		}
	}

	private void paintBackgroundComponent(final Graphics g) {
	    final Graphics backgroundGraphics = g.create();
	    try {
	    	setBackgroundComponentLocation(backgroundGraphics);
	    	backgroundComponent.paint(backgroundGraphics);
	    }
	    finally {
	    	backgroundGraphics.dispose();
	    }
    }

	private void setBackgroundComponentLocation(final Graphics g) {
		if (! fitToViewport) {
			final Point centerPoint = getRootCenterPoint();
			final Point backgroundImageTopLeft = getBackgroundImageTopLeft(centerPoint);
			g.translate(backgroundImageTopLeft.x, backgroundImageTopLeft.y);
		}
	}

	private Point getRootCenterPoint() {
		final Point centerPoint = new Point(getRoot().getMainView().getWidth() / 2,
		    getRoot().getMainView().getHeight() / 2);
		UITools.convertPointToAncestor(getRoot().getMainView(), centerPoint, this);
		return centerPoint;
	}

	private Point getBackgroundImageTopLeft(final Point centerPoint) {
		final int x = centerPoint.x - (backgroundComponent.getWidth() / 2);
		final int y = centerPoint.y - (backgroundComponent.getHeight() / 2);
		return new Point(x, y);
	}

	@Override
	protected void paintChildren(final Graphics g) {
	    final PaintingMode paintModes[];
	    if(paintConnectorsBehind)
	    	paintModes = new PaintingMode[]{
	    		PaintingMode.CLOUDS,
	    		PaintingMode.LINKS, PaintingMode.NODES, PaintingMode.SELECTED_NODES
	    		};
	    else
	    	paintModes = new PaintingMode[]{
	    		PaintingMode.CLOUDS,
	    		PaintingMode.NODES, PaintingMode.SELECTED_NODES, PaintingMode.LINKS
	    		};
	    final Graphics2D g2 = (Graphics2D) g;
	    paintChildren(g2, paintModes);
	    if(isSpotlightEnabled())
	    	paintDimmer(g2, paintModes);
		paintSelecteds(g2);
		highlightEditor(g2);
    }

	public boolean isSpotlightEnabled() {
		return Boolean.TRUE == getClientProperty(MapView.SPOTLIGHT_ENABLED);
	}

	private void paintChildren(final Graphics2D g2, final PaintingMode[] paintModes) {
	    for(final PaintingMode paintingMode : paintModes){
	    	this.paintingMode = paintingMode;
			switch(paintingMode){
	    		case LINKS:
	    			if(HIDE_CONNECTORS != showConnectors)
	    				paintConnectors(g2);
	    			break;
				default:
					super.paintChildren(g2);
			}
	    }
    }


	private void paintDimmer(final Graphics2D g2, final PaintingMode[] paintModes) {
		final Color color = g2.getColor();
		try{
			final Color dimmer = spotlightBackgroundColor;
			g2.setColor(dimmer);
			g2.fillRect(0, 0, getWidth(), getHeight());
		}
		finally{
			g2.setColor(color);
		}
		for (final NodeView selected : getSelection()) {
			highlightSelected(g2, selected, paintModes);
		}
    }

	private void highlightEditor(final Graphics2D g2) {
	    final Component editor = getComponent(0);
		if(editor instanceof NodeView)
	    	return;
	    final java.awt.Shape oldClip = g2.getClip();
	    try{
	    	g2.setClip(editor.getX(), editor.getY(), editor.getWidth(), editor.getHeight());
	    	super.paintChildren(g2);
	    }
	    finally{
	    	g2.setClip(oldClip);
	    }

    }

	protected PaintingMode getPaintingMode() {
		return paintingMode;
	}

	private void paintConnectors(final Collection<? extends NodeLinkModel> links, final Graphics2D graphics,
	                        final HashSet<ConnectorModel> alreadyPaintedLinks) {
		final Font font = graphics.getFont();
		try {
			final Iterator<? extends NodeLinkModel> linkIterator = links.iterator();
			while (linkIterator.hasNext()) {
				final NodeLinkModel next = linkIterator.next();
				if (!(next instanceof ConnectorModel)) {
					continue;
				}
				final ConnectorModel ref = (ConnectorModel) next;
				if (alreadyPaintedLinks.add(ref)) {
					final NodeModel target = ref.getTarget();
					if (target == null) {
						continue;
					}
					final NodeModel source = ref.getSource();
					final NodeView sourceView = getDisplayedNodeView(source);
					final NodeView targetView = getDisplayedNodeView(target);
					final ILinkView arrowLink;
					final boolean areBothNodesVisible = sourceView != null && targetView != null
							&& source.hasVisibleContent(filter) && target.hasVisibleContent(filter);
					final boolean showConnector = SHOW_CONNECTOR_LINES == showConnectors
							|| HIDE_CONNECTOR_LINES == showConnectors
							|| SHOW_CONNECTORS_FOR_SELECTION == showConnectors && (sourceView != null && sourceView.isSelected()
							|| targetView != null && targetView.isSelected());
					if(showConnector) {
						LinkController linkController = LinkController.getController(getModeController());
                        if (areBothNodesVisible
                                && (
                                ConnectorShape.EDGE_LIKE.equals(linkController.getShape(ref)) && ! ref.isSelfLink()
                                || sourceView.getMap().getLayoutType() == MapViewLayout.OUTLINE))
							arrowLink = new EdgeLinkView(ref, getModeController(), sourceView, targetView);
						else if(areBothNodesVisible || ! hideSingleEndConnectors)
							arrowLink = new ConnectorView(ref, sourceView, targetView, getBackground());
						else
							break;
						arrowLink.paint(graphics);
						arrowLinkViews.add(arrowLink);
					}
				}
			}
		}
		finally {
			graphics.setFont(font);
		}
	}

	private void paintConnectors(final Graphics2D graphics) {
		arrowLinkViews = new Vector<ILinkView>();
		final Object renderingHint = getModeController().getController().getMapViewManager().setEdgesRenderingHint(
		    graphics);
		if(hasNodeLinks())
			paintConnectors(currentRootView, graphics, new HashSet<ConnectorModel>());
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, renderingHint);
	}

	private void paintConnectors(final NodeView source, final Graphics2D graphics, final HashSet<ConnectorModel> alreadyPaintedConnectors) {
		final NodeModel node = source.getModel();
		final Collection<? extends NodeLinkModel> outLinks = getLinksFrom(node);
		paintConnectors(outLinks, graphics, alreadyPaintedConnectors);
		final Collection<? extends NodeLinkModel> inLinks = getLinksTo(node);
		paintConnectors(inLinks, graphics, alreadyPaintedConnectors);
		final int nodeViewCount = source.getComponentCount();
		for (int i = 0; i < nodeViewCount; i++) {
			final Component component = source.getComponent(i);
			if (!(component instanceof NodeView)) {
				continue;
			}
			final NodeView child = (NodeView) component;
			if (!isPrinting) {
				if(!child.isSubtreeVisible())
					continue;
				final Rectangle bounds = SwingUtilities.convertRectangle(source, child.getBounds(), this);
				final JViewport vp = (JViewport) getParent();
				final Rectangle viewRect = vp.getViewRect();
				viewRect.x -= viewRect.width;
				viewRect.y -= viewRect.height;
				viewRect.width *= 3;
				viewRect.height *= 3;
				if (!viewRect.intersects(bounds)) {
					continue;
				}
			}
			paintConnectors(child, graphics, alreadyPaintedConnectors);
		}
	}

	private boolean hasNodeLinks() {
		return LinkController.getController(getModeController()).hasNodeLinks(getModel(), this);
	}

	private Collection<? extends NodeLinkModel> getLinksTo(final NodeModel node) {
		return LinkController.getController(getModeController()).getLinksTo(node, this);
	}

	private Collection<? extends NodeLinkModel> getLinksFrom(final NodeModel node) {
		return LinkController.getController(getModeController()).getLinksFrom(node, this);
	}

	private void paintSelecteds(final Graphics2D g) {
		if (!MapView.drawsRectangleForSelection || isPrinting()) {
			return;
		}
		final Color c = g.getColor();
		final Stroke s = g.getStroke();
		g.setColor(getSelectionRectangleColor());
		g.setStroke(NodeHighlighter.DEFAULT_STROKE);
		final Object renderingHint = getModeController().getController().getMapViewManager().setEdgesRenderingHint(g);
		for (final NodeView selected : getSelection()) {
			paintSelectionRectangle(g, selected);
		}
		g.setColor(c);
		g.setStroke(s);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, renderingHint);
	}

	private void updateSelectionColors() {
	    ResourceController resourceController = ResourceController.getResourceController();
	    selectionRectangleColor = ColorUtils.stringToColor(resourceController.getProperty(
	            MapView.RESOURCES_SELECTED_NODE_RECTANGLE_COLOR));
	}

	private RoundRectangle2D.Float getRoundRectangleAround(final NodeView selected, int gap, final int arcw) {
		final JComponent content = selected.getContent();
		final Point contentLocation = new Point();
		UITools.convertPointToAncestor(content, contentLocation, this);
		gap -= 1;
		final RoundRectangle2D.Float roundRectClip = new RoundRectangle2D.Float(
			contentLocation.x - gap, contentLocation.y - gap,
			content.getWidth() + 2 * gap, content.getHeight() + 2 * gap, arcw, arcw);
		return roundRectClip;
	}

	private void paintSelectionRectangle(final Graphics2D g, final NodeView selected) {
		if (Boolean.TRUE.equals(selected.getMainView().getClientProperty("inline_editor_active"))) {
			return;
		}
		final RoundRectangle2D.Float roundRectClip = getRoundRectangleAround(selected, 4, 15);
		g.draw(roundRectClip);
	}

	private void highlightSelected(final Graphics2D g, final NodeView selected, final PaintingMode[] paintedModes) {
		final java.awt.Shape highlightClip;
		if (MapView.drawsRectangleForSelection)
			highlightClip = getRoundRectangleAround(selected, 4, 15);
		else
			highlightClip = getRoundRectangleAround(selected, 4, 2);
		final java.awt.Shape oldClip = g.getClip();
		final Rectangle oldClipBounds = g.getClipBounds();
		try{
			g.setClip(highlightClip);
			if(oldClipBounds != null)
				g.clipRect(oldClipBounds.x, oldClipBounds.y, oldClipBounds.width, oldClipBounds.height);
			final Rectangle clipBounds = highlightClip.getBounds();
			final Color color = g.getColor();
			g.setColor(getBackground());
			g.fillRect(clipBounds.x, clipBounds.y, clipBounds.width, clipBounds.height);
			g.setColor(color);
			paintChildren(g, paintedModes);
		}
		finally{
			g.setClip(oldClip);
		}
    }

	/**
	 * Call preparePrinting() before printing and endPrinting() after printing
	 * to minimize calculation efforts
	 */
	public void preparePrinting() {
		isPrinting = true;
		if (!isPreparedForPrinting) {
			isPreparedForPrinting = true;
			updatePrintedNodes();
			if (MapView.printOnWhiteBackground) {
				background = getBackground();
				setBackground(Color.WHITE);
			}
			fitMap = FitMap.valueOf();
			if (backgroundComponent != null && fitMap == FitMap.BACKGROUND) {
				boundingRectangle = getBackgroundImageInnerBounds();
			}
			else {
				boundingRectangle = getInnerBounds();
			}
		}
	}

	private void updatePrintedNodes() {
		if (zoom == 1f) {
			getRoot().updateAll();
			synchronized (getTreeLock()) {
				validateTree();
			}
		} else
			updatePrintedSelectedNodes();
	}

	private void updatePrintedSelectedNodes() {
		if(! drawsRectangleForSelection){
			selection.selectedSet.forEach(NodeView::update);
			synchronized (getTreeLock()) {
				validateTree();
			}
		}
	}

	private Rectangle getBackgroundImageInnerBounds() {
		final Point centerPoint = getRootCenterPoint();
		final Point backgroundImageTopLeft = getBackgroundImageTopLeft(centerPoint);
		return new Rectangle(backgroundImageTopLeft.x, backgroundImageTopLeft.y, backgroundComponent.getWidth(), backgroundComponent.getHeight());
	}

	@Override
	public void print(final Graphics g) {
		try {
			preparePrinting();
			super.print(g);
		}
		finally {
			isPrinting = false;
		}
	}

	public void render(final Graphics g1, final Rectangle source, final Rectangle target) {
		final Graphics2D g = (Graphics2D) g1;
		final AffineTransform old = g.getTransform();
		final double scaleX = (0.0 + target.width) / source.width;
		final double scaleY = (0.0 + target.height) / source.height;
		final double zoom;
		if(scaleX < scaleY){
			zoom = scaleX;
		}
		else{
			zoom = scaleY;
		}
		final AffineTransform tr2 = new AffineTransform(old);
		tr2.translate(target.getWidth() / 2, target.getHeight() / 2);
		tr2.scale(zoom, zoom);
		tr2.translate(-source.getX()- (source.getWidth() ) / 2, -source.getY()- (source.getHeight()) / 2);
		g.setTransform(tr2);
		final Rectangle clipBounds = g1.getClipBounds();
		g1.clipRect(source.x, source.y, source.width, source.height);
		print(g1);
		g.setTransform(old);
		g1.setClip(clipBounds.x, clipBounds.y, clipBounds.width, clipBounds.height);
	}

	@Override
	public int print(final Graphics graphics, final PageFormat pageFormat, final int pageIndex) {
		double userZoomFactor = ResourceController.getResourceController().getDoubleProperty("user_zoom", 1);
		userZoomFactor = Math.max(0, userZoomFactor);
		userZoomFactor = Math.min(2, userZoomFactor);
		if ((fitMap == FitMap.PAGE || fitMap == FitMap.BACKGROUND) && pageIndex > 0) {
			return Printable.NO_SUCH_PAGE;
		}
		final Graphics2D g2 = (Graphics2D) graphics.create();
		preparePrinting();
		final double zoomFactor;
		final double imageableX = pageFormat.getImageableX();
		final double imageableY = pageFormat.getImageableY();
		final double imageableWidth = pageFormat.getImageableWidth();
		final double imageableHeight = pageFormat.getImageableHeight();
		g2.clipRect((int)imageableX, (int)imageableY, (int)imageableWidth, (int)imageableHeight);
		final double mapWidth = boundingRectangle.getWidth();
		final double mapHeight = boundingRectangle.getHeight();
		if (fitMap == FitMap.PAGE || fitMap == FitMap.BACKGROUND) {
			final double zoomFactorX = imageableWidth / mapWidth;
			final double zoomFactorY = imageableHeight / mapHeight;
			zoomFactor = Math.min(zoomFactorX, zoomFactorY) * 0.99;
		}
		else {
			if (fitMap == FitMap.WIDTH) {
				zoomFactor = imageableWidth / mapWidth * 0.99;
			}
			else if (fitMap == FitMap.HEIGHT) {
				zoomFactor = imageableHeight / mapHeight * 0.99;
			}
			else {
				zoomFactor = userZoomFactor / UITools.FONT_SCALE_FACTOR;
			}
			final int nrPagesInWidth = (int) Math.ceil(zoomFactor * mapWidth
				/ imageableWidth);
			final int nrPagesInHeight = (int) Math.ceil(zoomFactor * mapHeight
				/ imageableHeight);
			if (pageIndex >= nrPagesInWidth * nrPagesInHeight) {
				return Printable.NO_SUCH_PAGE;
			}
			final int yPageCoord = (int) Math.floor(pageIndex / nrPagesInWidth);
			final int xPageCoord = pageIndex - yPageCoord * nrPagesInWidth;
			g2.translate(-imageableWidth * xPageCoord, -imageableHeight * yPageCoord);
		}
		g2.translate(imageableX, imageableY);
		g2.scale(zoomFactor, zoomFactor);
		final double mapX = boundingRectangle.getX();
		final double mapY = boundingRectangle.getY();
		g2.translate(-mapX, -mapY);
		print(g2);
		g2.dispose();
		return Printable.PAGE_EXISTS;
	}

	private void repaintSelecteds() {
	    updateSelectionColors();
		for (final NodeView selected : getSelection()) {
			onSelectionChange(selected);
		}
	}

	private Color requiredBackground() {
		final MapStyle mapStyle = getModeController().getExtension(MapStyle.class);
		final Color mapBackground = mapStyle.getBackground(model);
		return mapBackground;
	}

	void revalidateSelecteds() {
		selectedsValid = false;
	}

	/**
	 * Select the node, resulting in only that one being selected.
	 */
	public void selectAsTheOnlyOneSelected(final NodeView newSelected) {
		final NodeModel node = newSelected.getModel();
		if(node.isHiddenSummary())
			throw new AssertionError("select invisible node");
		selectAsTheOnlyOneSelected(newSelected, true);
		setSiblingMaxLevel(newSelected.getModel().getNodeLevel(filter));
	}

	public void selectAsTheOnlyOneSelected(final NodeView newSelected, final boolean requestFocus) {
		if (requestFocus) {
			newSelected.requestFocusInWindow();
		}
        if (ResourceController.getResourceController().getBooleanProperty("center_selected_node")) {
            mapScroller.scrollNode(newSelected, ScrollingDirective.SCROLL_NODE_TO_CENTER, ResourceController.getResourceController().getBooleanProperty("slow_scroll_selected_node"));
        }
        else {
            mapScroller.scrollNodeToVisible(newSelected);
        }
		if(selection.size() == 1 && getSelected().equals(newSelected)){
			return;
		}
		selection.select(newSelected);
		Container selectionParent = newSelected.getParent();
		if (selectionParent instanceof NodeView) {
			((NodeView) selectionParent).setPreferredChild(newSelected);
		}
	}

	/**
	 * Select the node and his descendants. On extend = false clear up the
	 * previous selection. if extend is false, the past selection will be empty.
	 * if yes, the selection will extended with this node and its children
	 */
	private void addBranchToSelection(final NodeView newlySelectedNodeView) {
		if (newlySelectedNodeView.isContentVisible()) {
			addSelected(newlySelectedNodeView, false);
		}
		for (final NodeView target : newlySelectedNodeView.getChildrenViews()) {
			addBranchToSelection(target);
		}
	}

	void selectContinuous(final NodeView newSelected) {
		final NodeView selectionStart = selection.getSelectionStart();
		final NodeView selectionEnd = selection.getSelectionEnd();
		final NodeView parentView = newSelected.getParentView();
		final boolean left = newSelected.isLeft();
		if(isRoot(newSelected)
				|| selectionStart == null || selectionEnd == null
				|| parentView != selectionStart.getParentView() || parentView != selectionEnd.getParentView()
				|| left != selectionStart.isLeft() || newSelected.isLeft() != selectionEnd.isLeft()){
			selection.setSelectionStart(newSelected);
			if(!newSelected.isSelected())
				selection.add(newSelected);
			mapScroller.scrollNodeToVisible(newSelected);
			return;
		}

		boolean selectionFound = false;
		boolean selectionRequired = false;
		for (final NodeView child : parentView.getChildrenViews()){
			if(child.isLeft() == left){
				final boolean onOldSelectionMargin = child == selectionStart || child == selectionEnd;
				final boolean selectionFoundNow = ! selectionFound && onOldSelectionMargin;
				selectionFound = selectionFound || selectionFoundNow;

				final boolean onNewSelectionMargin = child == selectionStart || child == newSelected;
				final boolean selectionRequiredNow = ! selectionRequired && onNewSelectionMargin;
				selectionRequired = selectionRequired || selectionRequiredNow;

				if(selectionRequired && ! selectionFound && child.getModel().hasVisibleContent(filter))
					selection.add(child);
				else if(! selectionRequired && selectionFound)
					selection.deselect(child);

				if(selectionFound && (selectionStart == selectionEnd || ! selectionFoundNow && onOldSelectionMargin))
					selectionFound = false;
				if(selectionRequired && (selectionStart == newSelected ||  ! selectionRequiredNow && onNewSelectionMargin))
					selectionRequired = false;
			}
		}
		selection.setSelectionEnd(newSelected);
		mapScroller.scrollNodeToVisible(newSelected);

	}

	public void setMoveCursor(final boolean isHand) {
		final int requiredCursor = isHand ? Cursor.MOVE_CURSOR : Cursor.DEFAULT_CURSOR;
		if (getCursor().getType() != requiredCursor) {
			setCursor(requiredCursor != Cursor.DEFAULT_CURSOR ? new Cursor(requiredCursor) : null);
		}
	}

	void setSiblingMaxLevel(final int level) {
		siblingMaxLevel = level;
	}

    public void setZoom(final float zoom) {
        if(this.zoom != zoom) {
            this.zoom = zoom;
            scrollsViewAfterLayout = true;
            mapScroller.anchorToNode(getSelected(), CENTER_ALIGNMENT, CENTER_ALIGNMENT);
            getRoot().updateAll();
            adjustBackgroundComponentScale();
        }
    }

    public void setZoom(final float zoom, Point keptPoint) {
        if(this.zoom != zoom) {
            this.zoom = zoom;
            NodeView selected = getSelected();
            MainView mainView = selected.getMainView();
            float referenceWidth = mainView.getWidth();
            float referenceHeight = mainView.getHeight();
            Point mainViewLocation = new Point();
            UITools.convertPointToAncestor(mainView, mainViewLocation, this);
            float x = referenceWidth > 0 ? (keptPoint.x - mainViewLocation.x) / referenceWidth : 0;
            float y = referenceHeight > 0 ? (keptPoint.y - mainViewLocation.y) / referenceHeight : 0;
            scrollsViewAfterLayout = true;
            mapScroller.anchorToNode(selected, x, y);
            getRoot().updateAll();
            adjustBackgroundComponentScale();
        }
    }

	private void adjustBackgroundComponentScale() {
		if (backgroundComponent != null) {
			if (fitToViewport) {
				final JViewport vp = (JViewport) getParent();
				final Dimension viewPortSize = vp.getVisibleRect().getSize();
				((ScalableComponent) backgroundComponent).setFinalViewerSize(viewPortSize);
			}
			else {
				((ScalableComponent) backgroundComponent).setMaximumComponentSize(getPreferredSize());
				((ScalableComponent) backgroundComponent).setFinalViewerSize(zoom);
			}
            SwingUtilities.invokeLater(this::repaint);
		}
	}

	/**
	 * Add the node to the selection if it is not yet there, remove it
	 * otherwise.
	 */
	private void toggleSelected(final NodeView nodeView) {
		if (isSelected(nodeView)) {
			if(selection.size() > 1)
				selection.deselect(nodeView);
		}
		else {
			selection.setSelectionStart(nodeView);
			selection.add(nodeView);
			mapScroller.scrollNodeToVisible(nodeView);
		}
	}

	private void validateSelecteds() {
		if (selectedsValid) {
			return;
		}
		selectedsValid = true;
		final NodeView selectedView = getSelected();
		if(selectedView == null){
			final NodeView root = getRoot();
			selectAsTheOnlyOneSelected(root);
			mapScroller.scrollToRootNode();
			return;
		}
		final NodeModel selectedNode = selectedView.getModel();
		final ArrayList<NodeView> selectedNodes = new ArrayList<NodeView>(getSelection().size());
		for (final NodeView nodeView : getSelection()) {
			if (nodeView != null) {
				selectedNodes.add(nodeView);
			}
		}
		selection.clear();
		for (final NodeView nodeView : selectedNodes) {
			if (nodeView.isContentVisible()) {
				selection.add(nodeView);
			}
		}
		if (getSelected() != null) {
			return;
        }
		for(NodeModel node = selectedNode.getParentNode(); node != null; node = node.getParentNode()){
			final NodeView newNodeView = getNodeView(node);
			if(newNodeView != null && newNodeView.isContentVisible() ){
				selectAsTheOnlyOneSelected(newNodeView);
				return;
			}
		}
		selectAsTheOnlyOneSelected(getRoot());
	}

	/*
	 * (non-Javadoc)
	 * @see java.awt.Container#validateTree()
	 */
	@Override
	protected void validateTree() {
		if(isDisplayable()) {
			validateSelecteds();
			getRoot().validateTree();
			super.validateTree();
		}
	}

	public void repaintVisible() {
		final JViewport vp = (JViewport) getParent();
		repaint(vp.getViewRect());
	}

	@Override
	public void propertyChanged(final String propertyName, final String newValue, final String oldValue) {
		if(propertyName.equals(TextController.MARK_TRANSFORMED_TEXT))
			UITools.repaintAll(getRoot());
	}

	public void selectVisibleAncestorOrSelf(NodeView preferred) {
		while(! preferred.getModel().hasVisibleContent(filter))
			preferred = preferred.getParentView();
		selectAsTheOnlyOneSelected(preferred);
    }

    public Font getNoteFont() {
        return noteFont;
    }

    public Color getNoteForeground() {
        return noteForeground;
    }

    public Color getNoteBackground() {
        return noteBackground;
    }



	public NodeCss getDetailCss() {
		return detailCss;
	}

	public NodeCss getNoteCss() {
		return noteCss;
	}

	public int getNoteHorizontalAlignment() {
		return noteHorizontalAlignment;
	}

   public Font getDetailFont() {
        return detailFont;
    }

    public Color getDetailForeground() {
        return detailForeground;
    }

    public Color getDetailBackground() {
        return detailBackground;
    }

	public int getDetailHorizontalAlignment() {
		return detailHorizontalAlignment;
	}

	public boolean isSelected() {
	    return Controller.getCurrentController().getMapViewManager().getMapViewComponent() == MapView.this;
    }

	void selectIfSelectionIsEmpty(final NodeView nodeView) {
		if(selection.selectedNode == null)
			selectAsTheOnlyOneSelected(nodeView);
    }

	public static MapView getMapView(final Component component) {
    	if(component instanceof MapView)
    		return (MapView) component;
    	return (MapView) SwingUtilities.getAncestorOfClass(MapView.class, component);
    }

	public void select() {
		getModeController().getController().getMapViewManager().changeToMapView(this);
    }

	@Override
    public void setSize(final int width, final int height) {
		final boolean sizeChanged = getWidth() != width || getHeight() != height;
		if(sizeChanged) {
			super.setSize(width, height);
			validate();
		}
    }

	void scrollViewAfterLayout() {
		if(isDisplayable()) {
			if(scrollsViewAfterLayout ) {
				scrollsViewAfterLayout  = false;
				mapScroller.scrollView();
			}
			else
				setAnchorContentLocation();
//				mapScroller.updateAnchorContentLocation();
		}
	}
	public void scrollBy(final int x, final int y) {
		mapScroller.scrollBy(x, y);
	}

	public void scrollNodeToVisible(final NodeView node) {
		mapScroller.scrollNodeToVisible(node);
	}

	public void setAnchorContentLocation() {
		mapScroller.setAnchorContentLocation();
	}

	public void preserveRootNodeLocationOnScreen() {
	    mapScroller.anchorToRoot();
	}

	public void preserveSelectedNodeLocation() {
		if(selectedsValid)
			preserveNodeLocationOnScreen(getSelected());
    }

    public void preserveNodeLocationOnScreen(NodeView nodeView) {
        int horizontalPoint = nodeView.isLeft() ? 1 : 0;
        preserveNodeLocationOnScreen(nodeView, horizontalPoint, 0);
    }

    public void preserveNodeLocationOnScreen(final NodeView nodeView, final float horizontalPoint, final float verticalPoint) {
		mapScroller.anchorToNode(nodeView, horizontalPoint, verticalPoint);
	}


	public void display(final NodeModel node) {
		NodeModel currentRoot = currentRootView.getModel();
		if(currentRoot != node && ! node.isDescendantOf(currentRoot))
			restoreRootNode();
		final NodeView nodeView = getNodeView(node);
		if(nodeView != null)
			return;
		final NodeModel parentNode = node.getParentNode();
		if(parentNode == null)
			return;
		display(parentNode);
		final NodeView parentView = getNodeView(parentNode);
		if(parentView == null)
			return;
		parentView.setFolded(false);
	}

	public boolean showsConnectorLines() {
		return HIDE_CONNECTOR_LINES != showConnectors;
	}

	public boolean showsIcons() {
		return showIcons;
	}

	int getLayoutSpecificMaxNodeWidth() {
		return usesLayoutSpecificMaxNodeWidth() ? Math.max(0, getViewportSize().width - 10 * getZoomed(outlineHGap)) : 0;
	}

	public boolean usesLayoutSpecificMaxNodeWidth() {
		return isOutlineLayoutSet() && outlineViewFitsWindowWidth();
	}

	private boolean outlineViewFitsWindowWidth() {
		return outlineViewFitsWindowWidth;
	}

    public Filter getFilter() {
        return filter;
    }

    static public Color getSelectionRectangleColor() {
        return selectionRectangleColor;
    }

    static public boolean drawsRectangleForSelection() {
        return drawsRectangleForSelection;
    }

    public void onEditingStarted(ZoomableLabel label) {
    	if(label instanceof MainView) {
    		label.putClientProperty(MapView.INLINE_EDITOR_ACTIVE, Boolean.TRUE);
			if (MapView.drawsRectangleForSelection) {
				repaintSelecteds();
			}
		}
    }

	public void onEditingFinished(ZoomableLabel label) {
    	if(label instanceof MainView) {
    		label.putClientProperty(MapView.INLINE_EDITOR_ACTIVE, null);
			if (MapView.drawsRectangleForSelection) {
				repaintSelecteds();
			}
		}
	}

	boolean allowsCompactLayout() {
		return allowsCompactLayout;
	}

	@Override
	public void invalidate() {
		if(! currentRootView.isValid() && ! isPreparedForPrinting)
			scrollsViewAfterLayout = true;
		super.invalidate();
	}

	boolean isRoot(NodeView nodeView) {
		return nodeView == currentRootView;
	}

	public void setRootNode(NodeModel node) {
		if(currentRootView.getModel() == node)
			return;
		NodeView nodeView = getNodeView(node);
		setRootNode(nodeView);
	}

	void restoreRootNode() {
		restoreRootNode(-1);
	}


	int calculateComponentIndex(Container parent, int index) {
		if(parent == currentRootParentView 
				&& index >= calculateCurrentRootNodePosition())
			return index - 1;
		else
			return index;
	}

	private int calculateCurrentRootNodePosition() {
		NodeModel currentRoot = currentRootView.getModel();
		NodeModel currentParent = currentRootParentView.getModel();
		return currentParent.getIndex(currentRoot);
	}

	void restoreRootNode(int index) {
		if(currentRootView == mapRootView)
			return;
		remove(ROOT_NODE_COMPONENT_INDEX);
		add(mapRootView, ROOT_NODE_COMPONENT_INDEX);
		if(! currentRootParentView.isFolded()) {
			currentRootParentView.add(currentRootView, 
					index >= 0 ? index : calculateCurrentRootNodePosition());
		}
		currentRootView = mapRootView;
		currentRootParentView = null;
	}
	
	private void setRootNode(NodeView newRootView) {
		if(currentRootView == newRootView)
			return;
		if(newRootView == mapRootView)
			preserveNodeLocationOnScreen(currentRootView);
		else
			preserveNodeLocationOnScreen(newRootView);
		
		NodeView lastSelectedNode;
		if(currentRootView != newRootView)
			lastSelectedNode = newRootView;
		else
			lastSelectedNode = selection.selectedNode;
		restoreRootNode();
		if(currentRootView != newRootView) {
			currentRootView = newRootView;
			currentRootParentView = newRootView.getParentView();
			selectAsTheOnlyOneSelected(newRootView);
			remove(ROOT_NODE_COMPONENT_INDEX);
			add(newRootView, ROOT_NODE_COMPONENT_INDEX);
		}
		lastSelectedNode.requestFocusInWindow();
		revalidate();
		repaint();
	}
}
