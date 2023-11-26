// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.layer.geoimage;

import static org.openstreetmap.josm.tools.I18n.marktr;
import static org.openstreetmap.josm.tools.I18n.tr;
import static org.openstreetmap.josm.tools.I18n.trn;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.OverlayLayout;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.data.ImageData;
import org.openstreetmap.josm.data.imagery.street_level.IImageEntry;
import org.openstreetmap.josm.gui.ExtendedDialog;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.gui.datatransfer.ClipboardUtils;
import org.openstreetmap.josm.gui.dialogs.DialogsPanel;
import org.openstreetmap.josm.gui.dialogs.ToggleDialog;
import org.openstreetmap.josm.gui.dialogs.layer.LayerVisibilityAction;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.gui.layer.LayerManager.LayerAddEvent;
import org.openstreetmap.josm.gui.layer.LayerManager.LayerChangeListener;
import org.openstreetmap.josm.gui.layer.LayerManager.LayerOrderChangeEvent;
import org.openstreetmap.josm.gui.layer.LayerManager.LayerRemoveEvent;
import org.openstreetmap.josm.gui.layer.MainLayerManager.ActiveLayerChangeEvent;
import org.openstreetmap.josm.gui.layer.MainLayerManager.ActiveLayerChangeListener;
import org.openstreetmap.josm.gui.layer.geoimage.IGeoImageLayer.ImageChangeListener;
import org.openstreetmap.josm.gui.layer.imagery.ImageryFilterSettings;
import org.openstreetmap.josm.gui.util.GuiHelper;
import org.openstreetmap.josm.gui.util.imagery.Vector3D;
import org.openstreetmap.josm.gui.widgets.HideableTabbedPane;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.ImageResource;
import org.openstreetmap.josm.tools.Logging;
import org.openstreetmap.josm.tools.PlatformManager;
import org.openstreetmap.josm.tools.Shortcut;
import org.openstreetmap.josm.tools.date.DateUtils;

/**
 * Dialog to view and manipulate geo-tagged images from a {@link GeoImageLayer}.
 * @implNote
 * The dialog has an {@code imagePanel} that {@link OverlayLayout overlays} an
 * {@link ImageDisplay} and a {@link JTabbedPane tabbed pane}.  The tabbed pane will be
 * visible only when images from more than one image layer are to be shown.  There will
 * be one ImageDisplay in each tab.  The ImageDisplay in the dialog will be reparented
 * to the first tab when the tabbed pane becomes visible.  The ImageDisplay in the last
 * open tab will be reparented to the dialog when the tabbed pane is hidden.
 */
public class ImageViewerDialog extends ToggleDialog // CHECKSTYLE.OFF: FinalClass
        implements ChangeListener, PropertyChangeListener, ImageChangeListener, LayerChangeListener, ActiveLayerChangeListener {
    private static final String GEOIMAGE_FILLER = marktr("Geoimage: {0}");
    private static final String DIALOG_FOLDER = "dialogs";
    private static final String CENTRE_PREF = "geoimage.viewer.centre.on.image";
    private static final List<? extends IImageEntry<?>> NO_IMAGES = Collections.emptyList();

    // Only one instance of that class is present at one time
    private static volatile ImageViewerDialog dialog;

    /** The center panel that holds the ImageDisplay */
    private final JPanel imagePanel = new JPanel();
    /** The toolbar below the image  */
    private final JToolBar toolbar = new JToolBar();
    /** The layer tab (used to select images when multiple layers provide images, makes for easy switching) */
    private final JTabbedPane tabbedPane = new JTabbedPane();
    /** Pointer to the viewer if the tabbed pane is not visible */
    private ImageDisplay2 imageDisplay0 = createImageDisplay(null);

    JToggleButton tbCentre;

    static void createInstance() {
        if (dialog != null)
            throw new IllegalStateException("ImageViewerDialog instance was already created");
        dialog = new ImageViewerDialog();
    }

    /**
     * Replies the unique instance of this dialog
     * @return the unique instance
     */
    public static ImageViewerDialog getInstance() {
        synchronized (ImageViewerDialog.class) {
            if (dialog == null)
                createInstance();
            MapFrame map = MainApplication.getMap();
            if (map != null && map.getToggleDialog(ImageViewerDialog.class) == null) {
                map.addToggleDialog(dialog);
            }
        }
        return dialog;
    }

    /**
     * Check if there is an instance for the {@link ImageViewerDialog}
     * @return {@code true} if there is a static singleton instance of {@link ImageViewerDialog}
     * @since 18613
     */
    public static boolean hasInstance() {
        return dialog != null;
    }

    /**
     * Destroy the current dialog
     */
    private static void destroyInstance() {
        dialog = null;
    }

    private ImageViewerDialog() {
        super(tr("Geotagged Images"), "geoimage", tr("Display geotagged images"), Shortcut.registerShortcut("tools:geotagged",
        tr("Windows: {0}", tr("Geotagged Images")), KeyEvent.VK_Y, Shortcut.DIRECT), 200);
        setup();
    }

    void setup() {
        tbCentre = new JToggleButton(new ImageCenterViewAction());
        tbCentre.setSelected(Config.getPref().getBoolean(CENTRE_PREF, false));

        toolbar.setFloatable(false);
        toolbar.setBorderPainted(false);
        toolbar.setOpaque(false);

        toolbar.add(new JButton(new ImageFirstAction()));
        toolbar.add(new JButton(new ImagePreviousAction()));
        toolbar.add(new JButton(new ImageNextAction()));
        toolbar.add(new JButton(new ImageLastAction()));

        toolbar.addSeparator();

        toolbar.add(new JButton(new UndoNavAction()));

        toolbar.addSeparator();

        toolbar.add(tbCentre);
        toolbar.add(new JButton(new ImageZoomAction()));

        toolbar.addSeparator();

        toolbar.add(new JButton(new ImageDeleteAction()));
        toolbar.add(new JButton(new ImageRemoveFromDiskAction()));

        toolbar.addSeparator();

        toolbar.add(new JButton(new ImageCopyPathAction()));
        toolbar.add(new JButton(new ImageOpenExternalAction()));

        toolbar.addSeparator();

        toolbar.add(new JButton(new ImageFilterAction()));

        toolbar.addSeparator();

        toolbar.add(new JButton(new DockAction()));

        // this will show either the tabbedPane or the imageDisplay0
        imagePanel.setLayout(new OverlayLayout(imagePanel));
        imagePanel.add(imageDisplay0);
        imagePanel.add(tabbedPane);
        tabbedPane.setVisible(false);

        JPanel toolbarPanel = new JPanel();
        toolbarPanel.setAlignmentX(CENTER_ALIGNMENT);
        toolbarPanel.add(toolbar);

        createLayout(imagePanel, false, null);
        add(toolbarPanel, BorderLayout.SOUTH);

        MainApplication.getLayerManager().addActiveLayerChangeListener(this);
        MainApplication.getLayerManager().addLayerChangeListener(this);
        for (IGeoImageLayer geoLayer: getGeoLayers()) {
            addLayerListeners(geoLayer);
        }
        tabbedPane.addChangeListener(this);
        tabbedPane.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        tbCentre.setSelected(Config.getPref().getBoolean(CENTRE_PREF, false));
    }

    private ImageDisplay2 createImageDisplay(IGeoImageLayer geoLayer) {
        ImageDisplay2 imageDisplay2 = new ImageDisplay2(geoLayer, new ImageryFilterSettings());
        imageDisplay2.mayInterruptIfRunning = true;
        return imageDisplay2;
    }

    @Override
    public void destroy() {
        cancelLoadingImage();
        MainApplication.getLayerManager().removeActiveLayerChangeListener(this);
        MainApplication.getLayerManager().removeLayerChangeListener(this);
        for (IGeoImageLayer geoLayer: getGeoLayers()) {
            removeLayerListeners(geoLayer);
        }
        tabbedPane.removeChangeListener(this);
        for (Component c : toolbar.getComponents()) {
            if (c instanceof AbstractButton) {
                // make actions garbage-collectable
                ((AbstractButton) c).setAction(null);
            }
        }
        // will destroy all destroyable children of dialog
        super.destroy();
        destroyInstance();
    }

    /**
     * Adds a tab for the given layer.
     * <p>
     * If there is only one tab, we hide the tabbed pane altogether to save screen real estate.
     * This method may fire stateChanged.
     */
    @SuppressWarnings("fallthrough")
    void addTabForLayer(IGeoImageLayer geoLayer) {
        int tabCount = tabbedPane.getTabCount();
        ImageDisplay2 imageDisplay;
        switch (tabCount) {
        case 0:
            imageDisplay0.geoLayer = geoLayer;
            imageDisplay = imageDisplay0;
            // Our first tab: insert the tab but hide the tab pane
            tabbedPane.insertTab(null, null, null, null, tabCount);
            tabbedPane.setVisible(false);
            break;
        case 1:
            // Our second tab: reparent the image display
            tabbedPane.setComponentAt(0, imageDisplay0);
            imageDisplay0 = null;
            // then fall thru to insert the tab
        default:
            imageDisplay = createImageDisplay(geoLayer);
            tabbedPane.insertTab(null, null, imageDisplay, null, tabCount);
            tabbedPane.setVisible(true);
        }
        tabbedPane.setTabComponentAt(tabCount, new CloseableTab(imageDisplay));
    }

    void removeTabForLayer(IGeoImageLayer geoLayer) {
        int index = getTabIndexFor(geoLayer);
        if (index != -1) {
            ImageDisplay2 imageDisplay = (ImageDisplay2) tabbedPane.getComponentAt(index);
            if (imageDisplay != null) {
                imageDisplay.destroy();
            }
            tabbedPane.removeTabAt(index);
            if (tabbedPane.getTabCount() == 1) {
                imageDisplay0 = (ImageDisplay2) tabbedPane.getComponentAt(0);
                // Otherwise, when we reparent the tab content the tabbed pane will
                // automagically remove the corresponding tab, but we want to keep
                // the tab alive, albeit without content and hidden.
                tabbedPane.setComponentAt(0, null);
                imagePanel.add(imageDisplay0); // reparent
                tabbedPane.setVisible(false);
            }
            updateDialogTitle();
            updateToolbar();
            updateMap();
        }
    }

    /**
     * Sort the tabs according to the OSM layer order.
     */
    private void sortTabs() {
        int n = 0;
        IGeoImageLayer selectedlayer = getSelectedTabLayer();
        // Note: we get the layers in reverse order, that is the layer with the
        // highest id first. This is the same order as displayed in the Layers
        // dialog, so I guess its alright.
        for (IGeoImageLayer geoLayer : getGeoLayers()) {
            int index = getTabIndexFor(geoLayer);
            if (index != -1) {
                if (index != n) {
                    ImageDisplay2 imageDisplay = getImageDisplayAt(index);
                    CloseableTab closeableTab = getCloseableTabAt(index);
                    tabbedPane.insertTab(
                        null,
                        tabbedPane.getIconAt(index),
                        imageDisplay,
                        tabbedPane.getToolTipTextAt(index),
                        n
                    );
                    tabbedPane.setTabComponentAt(n, closeableTab);
                }
                ++n;
            }
        }
        setSelectedTabLayer(selectedlayer);
    }

    /**
     * Returns a list of all geo layers in the system
     */
    List<IGeoImageLayer> getGeoLayers() {
        /*
        return MainApplication.getLayerManager().getLayers().stream()
            .filter(IGeoImageLayer.class::isInstance)
            .map(IGeoImageLayer.class::cast)
            .collect(Collectors.toList());
        */
        List<IGeoImageLayer> l = new ArrayList<>();
        MainApplication.getLayerManager().getLayers().forEach(layer -> {
            if (layer instanceof IGeoImageLayer)
                l.add((IGeoImageLayer) layer);
        });
        return l;
    }

    /**
     * Returns a list of the layers in all tabs
     */
    List<IGeoImageLayer> getTabLayers() {
        List<IGeoImageLayer> l = new ArrayList<>();
        for (int index = 0; index < tabbedPane.getTabCount(); ++index) {
            l.add(getImageDisplayAt(index).geoLayer);
        }
        return l;
    }

    /**
     * Returns the layer of the selected tab
     * @return the layer or null
     */
    IGeoImageLayer getSelectedTabLayer() {
        int index = tabbedPane.getSelectedIndex();
        if (index != -1) {
            return getImageDisplayAt(index).geoLayer;
        }
        return null;
    }

    /**
     * Returns the ImageDisplay at the given index.
     * @param index teh given index
     * @return the ImageDisplay or null
     */
    ImageDisplay2 getImageDisplayAt(int index) {
        if (index == 0 && imageDisplay0 != null) {
            return imageDisplay0;
        }
        Component c = tabbedPane.getComponentAt(index);
        if (c instanceof ImageDisplay2)
            return (ImageDisplay2) c;
        return null;
    }

    CloseableTab getCloseableTabAt(int index) {
        Component c = tabbedPane.getTabComponentAt(index);
        if (c instanceof CloseableTab)
            return (CloseableTab) c;
        return null;
    }

    /**
     * Selects the tab with the given layer.
     * <p>
     * If there is no tab with the given layer or the given layer is null then no tab is
     * selected.
     *
     * @param layer the given layer or null
     */
    void setSelectedTabLayer(IGeoImageLayer layer) {
        int index = -1;
        if (layer != null) {
            index = getTabIndexFor(layer);
        }
        tabbedPane.setSelectedIndex(index);
    }

    /**
     * Returns the tab index for the given layer
     * @param layer the given layer
     * @return the tab index or -1 if not found
     */
    int getTabIndexFor(IGeoImageLayer layer) {
        if (imageDisplay0 != null) {
            return layer.equals(imageDisplay0.geoLayer) ? 0 : -1;
        }
        for (int i = 0; i < tabbedPane.getTabCount(); ++i) {
            if (getImageDisplayAt(i).geoLayer.equals(layer))
                return i;
        }
        return -1;
    }

    /**
     * Returns the ImageDisplay in the selected tab
     * @return the ImageDisplay or null
     */
    ImageDisplay2 getSelectedImageDisplay() {
        if (imageDisplay0 != null) {
            return imageDisplay0;
        }
        int index = tabbedPane.getSelectedIndex();
        if (index != -1) {
            return getImageDisplayAt(index);
        }
        return null;
    }

    /**
     * Returns the images that are currently selected into the viewer or an empty list
     */
    List<? extends IImageEntry<?>> getSelectedImages() {
        ImageDisplay2 imageDisplay = getSelectedImageDisplay();
        if (imageDisplay != null) {
            return imageDisplay.getImages();
        }
        return NO_IMAGES;
    }

    /**
     * Returns the image that is currently selected into the viewer or null.
     * <p>
     * This returns an image iff only one image is selected.
     */
    IImageEntry<?> getSelectedImage() {
        return getUniqueImage(getSelectedImages());
    }

    static IImageEntry<?> getFirstImage(List<? extends IImageEntry<?>> images) {
        if (!images.isEmpty())
            return images.get(0);
        return null;
    }

    static IImageEntry<?> getUniqueImage(List<? extends IImageEntry<?>> images) {
        if (images.size() == 1)
            return images.get(0);
        return null;
    }

    /**
     * Returns the OSM layer an image is in or null.
     * <p>
     * This method remedies a seriuos deficiency of the IImageEntry interface.
     * @return the layer or null
     */
    IGeoImageLayer getLayer(IImageEntry<?> iImageEntry) {
        // First try
        if (iImageEntry instanceof ImageEntry) {
            ImageEntry imageEntry = (ImageEntry) iImageEntry;
            Layer layer = imageEntry.getDataSet().getLayer();
            if (layer instanceof IGeoImageLayer) {
                return (IGeoImageLayer) layer;
            }
        }
        // Second try
        if (iImageEntry instanceof RemoteEntry) {
            return null;
            /*
            RemoteEntry remoteEntry = (RemoteEntry) iImageEntry;
            Layer layer = remoteEntry.getLayer(); // There's no way to get the layer ??
            if (layer instanceof IGeoImageLayer) {
                return (IGeoImageLayer) layer;
            }
            */
        }
        // Stupid IImageEntry interface! Search all geo layers for this image
        for (IGeoImageLayer geoLayer : getGeoLayers()) {
            if (geoLayer.containsImage(iImageEntry))
                return geoLayer;
        }
        return null;
    }

    /**
     * An ImageDisplay that lets you retrieve the current images and keeps an undo
     * stack.
     */
    class ImageDisplay2 extends ImageDisplay {
        /** The layer of the images */
        transient IGeoImageLayer geoLayer;
        /** The currently selected images. */
        transient List<? extends IImageEntry<?>> images = NO_IMAGES;
        /** Undo stack of max. 10 images (memory hog) */
        transient Deque<IImageEntry<?>> undoStack = new ArrayDeque<>();
        /** The filter settings (sharpness etc.) */
        transient ImageryFilterSettings imageryFilterSettings;

        ImageDisplay2(IGeoImageLayer geoLayer, ImageryFilterSettings imageryFilterSettings) {
            super(imageryFilterSettings);
            this.geoLayer = geoLayer;
            this.imageryFilterSettings = imageryFilterSettings;
        }

        @Override
        public void destroy() {
            undoStack.clear();
            super.destroy();
        }

        String getLabel() {
            if (geoLayer != null)
                return ((Layer) geoLayer).getLabel();
            return "";
        }

        /**
         * Set the image(s) to display.
         * <p>
         * There may be none, one, or more than one image selected in the layer.  We
         * display an image only if exactly one image is selected, else we tell the
         * user.
         */
        public void setImages(List<? extends IImageEntry<?>> newImages) {
            this.images = newImages != null ? newImages : NO_IMAGES;
            if (images.isEmpty()) {
                setEmptyText("No image");
                setOsdText("");
                super.setImage(null);
                return;
            } else if (images.size() > 1) {
                setEmptyText("Multiple images selected");
                setOsdText("");
                super.setImage(null);
                return;
            }
            IImageEntry<?> entry = images.get(0);
            if (entry != null)
                updateOsd(entry);
            super.setImage(entry);
        }

        public IImageEntry<?> getImage() {
            return images.isEmpty() ? null : images.get(0);
        }

        public List<? extends IImageEntry<?>> getImages() {
            return images;
        }

        /**
         * Updates the On-Screen-Display (EXIF information of shown picture)
         */
        private void updateOsd(IImageEntry<?> entry) {
            StringBuilder osd = new StringBuilder(entry.getDisplayName());
            if (entry.getElevation() != null) {
                osd.append(tr("\nAltitude: {0} m", Math.round(entry.getElevation())));
            }
            if (entry.getSpeed() != null) {
                osd.append(tr("\nSpeed: {0} km/h", Math.round(entry.getSpeed())));
            }
            if (entry.getExifImgDir() != null) {
                osd.append(tr("\nDirection {0}\u00b0", Math.round(entry.getExifImgDir())));
            }

            DateTimeFormatter dtf = DateUtils.getDateTimeFormatter(FormatStyle.SHORT, FormatStyle.MEDIUM)
                    // Set timezone to UTC since UTC is assumed when parsing the EXIF timestamp,
                    // see see org.openstreetmap.josm.tools.ExifReader.readTime(com.drew.metadata.Metadata)
                    .withZone(ZoneOffset.UTC);

            if (entry.hasExifTime()) {
                osd.append(tr("\nEXIF time: {0}", dtf.format(entry.getExifInstant())));
            }
            if (entry.hasGpsTime()) {
                osd.append(tr("\nGPS time: {0}", dtf.format(entry.getGpsInstant())));
            }
            Optional.ofNullable(entry.getIptcCaption()).map(s -> tr("\nCaption: {0}", s)).ifPresent(osd::append);
            Optional.ofNullable(entry.getIptcHeadline()).map(s -> tr("\nHeadline: {0}", s)).ifPresent(osd::append);
            Optional.ofNullable(entry.getIptcKeywords()).map(s -> tr("\nKeywords: {0}", s)).ifPresent(osd::append);
            Optional.ofNullable(entry.getIptcObjectName()).map(s -> tr("\nObject name: {0}", s)).ifPresent(osd::append);

            setOsdText(osd.toString());
        }

        void storeUndo(IImageEntry<?> entry) {
            if (entry != null && entry != undoStack.peekFirst())
                undoStack.addFirst(entry);
            // keep only 10 entries until we figure out how to clear the big image buffers
            while (undoStack.size() > 10) {
                undoStack.pollLast();
            }
        }

        boolean canUndo() {
            return !undoStack.isEmpty();
        }

        IImageEntry<?> undo() {
            return undoStack.pollFirst();
        }
    }

    /**
     * Displays a single image for the given layer.
     * @param ignoredData the image data
     * @param entry image entry
     * @see #displayImages
     * @deprecated Call {@link #displayImages(IGeoImageLayer, List)} instead
     */
    @Deprecated
    public void displayImage(ImageData ignoredData, ImageEntry entry) {
        displayImages(Collections.singletonList(entry));
    }

    /**
     * Displays a single image for the given layer.
     * @param entry image entry
     * @see #displayImages
     * @deprecated Call {@link #displayImages(IGeoImageLayer, List)} instead
     */
    @Deprecated
    public void displayImage(IImageEntry<?> entry) {
        this.displayImages(Collections.singletonList(entry));
    }

    /**
     * Displays the given images.
     * <p>
     * We have to maintain this for compatibility as this is called from different
     * places to open the dialog with some images preloaded.
     * <p>
     * This is also called when the selected image changes in some geolayer by
     * {@link IImageEntry#selectImage}.  That the low-level IImageEntry interface should
     * call into the high-level ImageViewerDialog is crazy as this dialog can easily
     * listen to {@link IGeoImageLayer.ImageChangeListener#imageChanged imageChanged}
     * events instead.  And that's what this dialog actually does.
     * <p>
     * FIXME: {@link IImageEntry} tries to be an image and a collection of images at the
     * same time and should be refactored into two separate interfaces:
     * <ul>
     * <li>
     * {@code IImageEntry} that represents an image.  The following functions should be
     * added: a function to get the collection, one to get the layer, and one to clear
     * temporary image buffers (unread the image).
     * <li>
     * {@code IImageCollection<? extends IImageEntry>} that represents an image
     * collection.  {@code IImageCollection} should fire {@link
     * org.openstreetmap.josm.data.osm.DataSelectionListener.SelectionChangeEvent
     * SelectionChangeEvents} on navigation and provide the usual listener registration
     * functions.  It should NOT depend on ImageDisplay.  The following functions should
     * be added: get the selected images, set the selected images.
     * </ul>
     *
     * @param newImages the image entries to display in the dialog
     * @since 18246
     * @deprecated Call {@link #displayImages(IGeoImageLayer, List)} instead
     */
    @Deprecated
    public void displayImages(List<? extends IImageEntry<?>> newImages) {
        imageChanged(getLayer(getFirstImage(newImages)), null, newImages);
    }

    /**
     * Displays the given images.
     * <p>
     * This function expects you to provide the image layer, which is impossible to get
     * reliably from an {@code IImageEntry<?>} in the current implementation.
     *
     * @param geoLayer the image layer
     * @param newImages the images to display
     */
    public void displayImages(IGeoImageLayer geoLayer, List<? extends IImageEntry<?>> newImages) {
        imageChanged(geoLayer, null, newImages);
    }

    /**
     * Opens or expands the image viewer dialog to make sure it is visible.
     */
    void ensureDialogVisible() {
        if (!isDialogShowing()) {
            setIsDocked(false); // always open a detached window when an image is clicked and dialog is closed
            showDialog();
        } else if (isDocked && isCollapsed) {
            expand();
            dialogsPanel.reconstruct(DialogsPanel.Action.COLLAPSED_TO_DEFAULT, this);
        }
    }

    /**
     * Set a new image in the viewer or clear it
     * @param newImage The new entry or null
     */
    private void setImages(List<? extends IImageEntry<?>> newImages) {
        ImageDisplay2 imageDisplay = getSelectedImageDisplay();
        if (imageDisplay != null) {
            imageDisplay.setImages(newImages);
        }
    }

    /**
     * Creates a tab for a layer if the tab does not yet exist.
     * @param entry the given layer
     */
    void ensureTabForLayer(IGeoImageLayer geoLayer) {
        if (getTabIndexFor(geoLayer) == -1) {
            addTabForLayer(geoLayer);
        }
    }

    /**
     * Updates the status of all buttons in the toolbar.
     * <p>
     * This is a workaround for JosmAction not listening to imageChanged.
     */
    private void updateToolbar() {
        for (Component c : toolbar.getComponents()) {
            if (c instanceof AbstractButton) {
                Action action = ((AbstractButton) c).getAction();
                if (action instanceof SideButtonAction)
                    ((SideButtonAction) action).updateEnabledState();
            }
        }
    }

    /**
     * Selects the selected image in the map layer and pans the map to the location of
     * the selected image if that preference is set.
     */
    private void updateMap() {
        List<? extends IImageEntry<?>> selectedImages = getSelectedImages();
        // FIXME: select all images when IImageEntry<?> will be fixed to allow this.
        IImageEntry<?> onlyImage = getUniqueImage(selectedImages);
        if (onlyImage != null) {
            onlyImage.selectImage(null, onlyImage);
        }
        if (isCenterView()) {
            IImageEntry<?> firstImage = getFirstImage(selectedImages);
            if (firstImage != null) {
                panTo(firstImage);
            }
        }
    }

    /**
     * Updates the title of the dialog.
     */
    private void updateDialogTitle() {
        String baseTitle = tr("Geotagged Images");
        String extTitle = "";
        int index;
        if ((index = tabbedPane.getSelectedIndex()) != -1) {
            baseTitle = getImageDisplayAt(index).getLabel();
        }
        IImageEntry<?> currentEntry = getUniqueImage(getSelectedImages());
        if (currentEntry != null && !currentEntry.getDisplayName().isEmpty()) {
            extTitle = " - " + currentEntry.getDisplayName();
        }
        this.setTitle(baseTitle + extTitle);
    }

    /**
     * Pan the map to center the given image
     */
    void panTo(IImageEntry<?> entry) {
        if (entry != null && MainApplication.isDisplayingMapView() && entry.getPos() != null) {
            MainApplication.getMap().mapView.zoomTo(entry.getPos());
        }
    }

    /**
     * Called when dialog docked or collapsed state has changed.
     */
    @Override
    protected void stateChanged() {
        super.stateChanged();
        updateToolbar();
    }

    /**
     * Returns whether an image is currently displayed
     * @return If image is currently displayed
     */
    public boolean hasImage() {
        return getCurrentImage() != null;
    }

    /**
     * Returns the currently displayed image.
     * @return Currently displayed image or {@code null}
     * @since 18246 (signature)
     */
    public static IImageEntry<?> getCurrentImage() {
        return getUniqueImage(getInstance().getSelectedImages());
    }

    /**
     * Returns the rotation of the currently displayed image.
     * @param entry The entry to get the rotation for. May be {@code null}.
     * @return the rotation of the currently displayed image, or {@code null}
     * @since 18263
     */
    public Vector3D getRotation(IImageEntry<?> entry) {
        ImageDisplay imageDisplay = getSelectedImageDisplay();
        if (imageDisplay != null)
            return imageDisplay.getRotation(entry);
        return null;
    }

    /**
     * Returns whether the center view is currently active.
     * @return {@code true} if the center view is active, {@code false} otherwise
     * @since 9416
     */
    public boolean isCenterView() {
        return tbCentre.isEnabled() && tbCentre.isSelected();
    }

    /**
     * Enables (or disables) the "Center view" button.
     * @param value {@code true} to enable the button, {@code false} otherwise
     * @return the old enabled value. Can be used to restore the original enable state
     */
    public boolean setCentreEnabled(boolean value) {
        final boolean wasEnabled = isCenterView();
        tbCentre.setEnabled(value);
        tbCentre.getAction().actionPerformed(new ActionEvent(tbCentre, 0, null));
        return wasEnabled;
    }

    /**
     * Reload the image. Call this if you load a low-resolution image first, and then get a high-resolution image, or
     * if you know that the image has changed on disk.
     * @since 18591
     */
    public void refresh() {
        if (SwingUtilities.isEventDispatchThread()) {
            ImageDisplay2 imageDisplay = getSelectedImageDisplay();
            if (imageDisplay != null) {
                setImages(imageDisplay.getImages());
            }
        } else {
            GuiHelper.runInEDT(this::refresh);
        }
    }

    /**
     * Cancels all loading of images.
     */
    private void cancelLoadingImage() {
        ImageDisplay2 imageDisplay = getSelectedImageDisplay();
        if (imageDisplay != null && imageDisplay.imageLoadingFuture != null) {
            imageDisplay.imageLoadingFuture.cancel(true);
        }
    }

    /*************/
    /* Listeners */
    /*************/

    /**
     * Listener to image selection changes.
     * <p>
     * This is called when the selection changes because the user clicked on a geoimage
     * icon in the map.  (Or for any other selection change initiated by the geoimage
     * layer.)
     * <p>
     * @implNote
     * {@code oldImages} is not used and can be null.
     */
    @Override
    public void imageChanged(IGeoImageLayer geoLayer, List<? extends IImageEntry<?>> oldImages,
            List<? extends IImageEntry<?>> newImages) {

        ensureDialogVisible();
        ensureTabForLayer(geoLayer);
        setSelectedTabLayer(geoLayer);
        setImages(newImages);
        // getSelectedImageDisplay().storeUndo(entry); // we get duplicated events from Mapillary plugin
        updateDialogTitle();
        updateToolbar();
    }

    /**
     * Listener to tab selection changed.
     * <p>
     * This may also fire on tab added or removed iff the selected tab or the index of
     * the selected tab have changed.  But there's no guarantee they will.
     */
    @Override
    public void stateChanged(ChangeEvent e) {
        updateDialogTitle();
        updateToolbar();
        updateMap();
    }

    /*
     * Listeners to layer changes
     */

    /**
     * Adds the listeners to a geo layer
     * <p>
     * We must keep listeners on *all* geo layers, not only on those we have in open
     * tabs because if the user clicks on a layer in the map that we don't have yet in a
     * tab, we want a new tab to open for it.
     *
     * @param geoLayer the layer
     */
    void addLayerListeners(IGeoImageLayer geoLayer) {
        geoLayer.addImageChangeListener(this);
        ((Layer) geoLayer).addPropertyChangeListener(this);
    }

    /**
     * Removes the listeners from a geo layer
     */
    void removeLayerListeners(IGeoImageLayer geoLayer) {
        geoLayer.removeImageChangeListener(this);
        ((Layer) geoLayer).removePropertyChangeListener(this);
    }

    @Override
    public void layerAdded(LayerAddEvent e) {
        if (e.getAddedLayer() instanceof IGeoImageLayer) {
            addLayerListeners((IGeoImageLayer) e.getAddedLayer());
        }
    }

    @Override
    public void layerRemoving(LayerRemoveEvent e) {
        if (e.getRemovedLayer() instanceof IGeoImageLayer) {
            removeLayerListeners((IGeoImageLayer) e.getRemovedLayer());
            removeTabForLayer((IGeoImageLayer) e.getRemovedLayer());
        }
    }

    @Override
    public void layerOrderChanged(LayerOrderChangeEvent e) {
        sortTabs();
    }

    @Override
    public void activeOrEditLayerChanged(ActiveLayerChangeEvent e) {
        if (e.getSource().getActiveLayer() instanceof IGeoImageLayer) {
            setSelectedTabLayer((IGeoImageLayer) e.getSource().getActiveLayer());
        }
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        // update tab and dialog titles if layer name changes
        if (Layer.NAME_PROP.equals(evt.getPropertyName())) {
            updateDialogTitle();
            // revalidate all tabs to accomodate new title length
            // (all because its easier)
            for (int index = 0; index < tabbedPane.getTabCount(); index++) {
                tabbedPane.getTabComponentAt(index).revalidate();
            }
            tabbedPane.repaint();
        }
        // Use Layer.VISIBLE_PROP here if we decide to do something when layer visibility changes
    }

    /***************/
    /* The actions */
    /***************/

    /**
     * An action that puts a SIDEBUTTON-sized icon in the button instead of a TOOLBAR-sized one.
     */
    private abstract class SideButtonAction extends JosmAction {
        SideButtonAction(String name, ImageProvider icon, String tooltip, Shortcut shortcut,
                boolean registerInToolbar, String toolbarId) {
            super(name, icon, tooltip, shortcut, registerInToolbar, toolbarId, true);

            putValue(LARGE_ICON_KEY, icon.getResource()
                .getImageIcon(ImageProvider.ImageSizes.SIDEBUTTON.getImageDimension()));
        }

        @Override
        protected void updateEnabledState() { // NOSONAR Override to make it accessible from here
            super.updateEnabledState();
        }
    }

    private abstract class ImageNavigationAction extends SideButtonAction {
        enum What { FIRST, PREV, NEXT, LAST }

        What what;

        ImageNavigationAction(String name, ImageProvider icon, String tooltip, Shortcut shortcut,
                boolean registerInToolbar, String toolbarId, What what) {
            super(name, icon, tooltip, shortcut, registerInToolbar, toolbarId);
            this.what = what;
        }

        @Override
        public void actionPerformed(ActionEvent event) {
            final IImageEntry<?> entry = getSelectedImage();
            if (entry != null) {
                getSelectedImageDisplay().storeUndo(entry);
                IImageEntry<?> newImage;
                switch (what) {
                    case FIRST: newImage = entry.getFirstImage(); break;
                    case PREV: newImage = entry.getPreviousImage(); break;
                    case NEXT: newImage = entry.getNextImage(); break;
                    default: newImage = entry.getLastImage(); break; // make javac happy
                }
                entry.selectImage(null, newImage);
                updateMap();
            }
        }

        @Override
        protected void updateEnabledState() {
            final IImageEntry<?> entry = getSelectedImage();
            if (entry == null) {
                this.setEnabled(false);
                return;
            }
            switch (what) {
                case FIRST:
                case PREV:
                    this.setEnabled(entry.getPreviousImage() != null);
                    break;
                case NEXT:
                case LAST:
                    this.setEnabled(entry.getNextImage() != null);
            }
        }
    }

    private class ImageFirstAction extends ImageNavigationAction {
        ImageFirstAction() {
            super(null, new ImageProvider(DIALOG_FOLDER, "first"), tr("First"), Shortcut.registerShortcut(
                    "geoimage:first", tr(GEOIMAGE_FILLER, tr("Show first Image")), KeyEvent.VK_HOME, Shortcut.DIRECT),
                  false, null, What.FIRST);
        }
    }

    private class ImagePreviousAction extends ImageNavigationAction {
        ImagePreviousAction() {
            super(null, new ImageProvider(DIALOG_FOLDER, "previous"), tr("Previous"), Shortcut.registerShortcut(
                    "geoimage:previous", tr(GEOIMAGE_FILLER, tr("Show previous Image")), KeyEvent.VK_PAGE_UP, Shortcut.DIRECT),
                  false, null, What.PREV);
        }
    }

    private class ImageNextAction extends ImageNavigationAction {
        ImageNextAction() {
            super(null, new ImageProvider(DIALOG_FOLDER, "next"), tr("Next"), Shortcut.registerShortcut(
                    "geoimage:next", tr(GEOIMAGE_FILLER, tr("Show next Image")), KeyEvent.VK_PAGE_DOWN, Shortcut.DIRECT),
                  false, null, What.NEXT);
        }
    }

    private class ImageLastAction extends ImageNavigationAction {
        ImageLastAction() {
            super(null, new ImageProvider(DIALOG_FOLDER, "last"), tr("Last"), Shortcut.registerShortcut(
                    "geoimage:last", tr(GEOIMAGE_FILLER, tr("Show last Image")), KeyEvent.VK_END, Shortcut.DIRECT),
                  false, null, What.LAST);
        }
    }

    private class UndoNavAction extends SideButtonAction {
        UndoNavAction() {
            super(null, new ImageProvider("undo"), tr("Undo the last navigation"), null,
                  false, null);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            ImageDisplay2 imageDisplay = getSelectedImageDisplay();
            if (imageDisplay != null) {
                IImageEntry<?> newEntry = imageDisplay.undo();
                if (newEntry != null) {
                    newEntry.selectImage(null, newEntry);
                    updateEnabledState();
                    updateMap();
                }
            }
        }

        @Override
        protected void updateEnabledState() {
            ImageDisplay2 imageDisplay = getSelectedImageDisplay();
            if (imageDisplay != null) {
                setEnabled(imageDisplay.canUndo());
            } else {
                setEnabled(false);
            }
        }
    }

    private class ImageCenterViewAction extends SideButtonAction {
        ImageCenterViewAction() {
            super(null, new ImageProvider("dialogs/autoscale", "selection"), tr("Center view"), null,
                  false, null);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            final JToggleButton button = (JToggleButton) e.getSource();
            boolean centerView = isCenterView();
            Config.getPref().putBoolean(CENTRE_PREF, centerView);
            if (centerView)
                panTo(getSelectedImage());
        }
    }

    private class ImageZoomAction extends SideButtonAction {
        ImageZoomAction() {
            super(null, new ImageProvider(DIALOG_FOLDER, "zoom-best-fit"), tr("Zoom best fit and 1:1"), null,
                  false, null);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            ImageDisplay imageDisplay = getSelectedImageDisplay();
            if (imageDisplay != null)
                imageDisplay.zoomBestFitOrOne();
        }

        @Override
        protected void updateEnabledState() {
            setEnabled(getSelectedImage() != null);
        }
    }

    private class ImageDeleteAction extends SideButtonAction {
        ImageDeleteAction() {
            super(null, new ImageProvider(DIALOG_FOLDER, "delete"), tr("Remove photo from layer"), Shortcut.registerShortcut(
                    "geoimage:deleteimagefromlayer", tr(GEOIMAGE_FILLER, tr("Remove photo from layer")), KeyEvent.VK_DELETE, Shortcut.SHIFT),
                  false, null);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            IImageEntry<?> imageEntry = getSelectedImage();
            if (imageEntry != null && imageEntry.isRemoveSupported()) {
                imageEntry.remove();
            }
        }

        @Override
        protected void updateEnabledState() {
            // FIXME enable if multiple images selected
            IImageEntry<?> imageEntry = getSelectedImage();
            setEnabled(imageEntry != null && imageEntry.isRemoveSupported());
        }
    }

    private class ImageRemoveFromDiskAction extends SideButtonAction {
        ImageRemoveFromDiskAction() {
            super(null, new ImageProvider(DIALOG_FOLDER, "geoimage/deletefromdisk"), tr("Delete image file from disk"),
                    Shortcut.registerShortcut("geoimage:deletefilefromdisk",
                            tr(GEOIMAGE_FILLER, tr("Delete image file from disk")), KeyEvent.VK_DELETE, Shortcut.CTRL_SHIFT),
                    false, null);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            IImageEntry<?> currentEntry = getSelectedImage();
            if (currentEntry != null) {
                List<IImageEntry<?>> toDelete = currentEntry instanceof ImageEntry ?
                        new ArrayList<>(((ImageEntry) currentEntry).getDataSet().getSelectedImages())
                        : Collections.singletonList(currentEntry);
                int size = toDelete.size();

                int result = new ExtendedDialog(
                        MainApplication.getMainFrame(),
                        tr("Delete image file from disk"),
                        tr("Cancel"), tr("Delete"))
                        .setButtonIcons("cancel", "dialogs/geoimage/deletefromdisk")
                        .setContent(new JLabel("<html><h3>"
                                + trn("Delete the file from disk?",
                                      "Delete {0} files from disk?", size, size)
                                + "</h3><p>" + trn("The image file will be permanently lost!",
                                              "The image files will be permanently lost!", size) + "</p></html>",
                                ImageProvider.get("dialogs/geoimage/deletefromdisk"), SwingConstants.LEADING))
                        .toggleEnable("geoimage.deleteimagefromdisk")
                        .setCancelButton(1)
                        .setDefaultButton(2)
                        .showDialog()
                        .getValue();

                if (result == 2) {
                    final List<ImageData> imageDataCollection = toDelete.stream().filter(ImageEntry.class::isInstance)
                            .map(ImageEntry.class::cast).map(ImageEntry::getDataSet).distinct().collect(Collectors.toList());
                    for (IImageEntry<?> delete : toDelete) {
                        // We have to be able to remove the image from the layer and the image from its storage location
                        // If either are false, then don't remove the image.
                        if (delete.isRemoveSupported() && delete.isDeleteSupported() && delete.remove() && delete.delete()) {
                            Logging.info("File {0} deleted.", delete.getFile());
                        } else {
                            JOptionPane.showMessageDialog(
                                    MainApplication.getMainFrame(),
                                    tr("Image file could not be deleted."),
                                    tr("Error"),
                                    JOptionPane.ERROR_MESSAGE
                                    );
                        }
                    }
                    imageDataCollection.forEach(data -> {
                        data.notifyImageUpdate();
                        data.updateSelectedImage();
                    });
                }
            }
        }

        @Override
        protected void updateEnabledState() {
            // FIXME enable if multiple images selected
            IImageEntry<?> imageEntry = getSelectedImage();
            setEnabled(imageEntry != null && imageEntry.isDeleteSupported() && imageEntry.isRemoveSupported());
        }
    }

    private class ImageCopyPathAction extends SideButtonAction {
        ImageCopyPathAction() {
            super(null, new ImageProvider("copy"), tr("Copy image path"), Shortcut.registerShortcut(
                    "geoimage:copypath", tr(GEOIMAGE_FILLER, tr("Copy image path")), KeyEvent.VK_C, Shortcut.ALT_CTRL_SHIFT),
                  false, null);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            IImageEntry<?> currentEntry = getSelectedImage();
            if (currentEntry != null) {
                ClipboardUtils.copyString(String.valueOf(currentEntry.getImageURI()));
            }
        }

        @Override
        protected void updateEnabledState() {
            setEnabled(getSelectedImage() != null);
        }
    }

    private class ImageOpenExternalAction extends SideButtonAction {
        ImageOpenExternalAction() {
            super(null, new ImageProvider("external-link"), tr("Open image in external viewer"), null, false, null);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            IImageEntry<?> currentEntry = getSelectedImage();
            if (currentEntry != null && currentEntry.getImageURI() != null) {
                try {
                    PlatformManager.getPlatform().openUrl(currentEntry.getImageURI().toURL().toExternalForm());
                } catch (IOException ex) {
                    Logging.error(ex);
                }
            }
        }

        @Override
        protected void updateEnabledState() {
            setEnabled(getSelectedImage() != null);
        }
    }

    /**
     * When the dialog is closed by the system menu, really close it and do not dock it.
     */
    @Override
    protected boolean dockWhenClosingDetachedDlg() {
        return false;
    }

    private class DockAction extends SideButtonAction {
        DockAction() {
            super(null, new ImageProvider(DIALOG_FOLDER, "collapse"), tr("Move dialog to the side pane"), null,
                  false, null);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            detachedDialog.dispose();
            dock();
            if (isDialogInCollapsedView()) {
                setContentVisible(false);
                dialogsPanel.reconstruct(DialogsPanel.Action.ELEMENT_SHRINKS, null);
            } else {
                dialogsPanel.reconstruct(DialogsPanel.Action.INVISIBLE_TO_DEFAULT, ImageViewerDialog.this);
            }
        }

        @Override
        protected void updateEnabledState() {
            setEnabled(!isDocked);
        }
    }

    public List<ImageryFilterSettings> filterSupplier() {
        ImageDisplay2 imageDisplay = getSelectedImageDisplay();
        if (imageDisplay != null)
            return Collections.singletonList(imageDisplay.imageryFilterSettings);
        return Collections.emptyList();
    }

    private class ImageFilterAction extends LayerVisibilityAction {
        ImageFilterAction() {
            super(Collections::emptyList, ImageViewerDialog.this::filterSupplier);
            putValue(Action.LARGE_ICON_KEY,
                ImageResource.getAttachedImageResource(this)
                    .getImageIcon(ImageProvider.ImageSizes.SIDEBUTTON.getImageDimension()));
            putValue(SHORT_DESCRIPTION, tr("Image Filters"));
        }

        @Override
        public void updateEnabledState() {
            setEnabled(getSelectedImage() != null);
        }
    }

    /**
     * A tab title renderer for {@link HideableTabbedPane} that allows us to close tabs.
     */
    private class CloseableTab extends JPanel implements ActionListener {
        ImageDisplay2 imageDisplay;

        /**
         * Create a new {@link CloseableTab}.
         * @param tabbedPane The parent to add property change listeners to. It should be a {@link HideableTabbedPane} in most cases.
         * @param closeAction The action to run to close the tab. You probably want to call {@link JTabbedPane#removeTabAt(int)}
         *                    at the very least.
         */
        CloseableTab(ImageDisplay2 imageDisplay) {
            super(new FlowLayout(FlowLayout.LEFT, 10, 0));
            this.imageDisplay = imageDisplay;
            setOpaque(false);

            JLabel label = new JLabel() {
                @Override
                public String getText() {
                    return imageDisplay.getLabel();
                }
            };
            add(label);

            // JButton close = new JButton(ImageProvider.get("misc", "close"));
            JButton close = new JButton("×");
            close.setFont(close.getFont().deriveFont(Font.BOLD));
            close.setBorderPainted(false);
            close.addActionListener(this);
            add(close);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            removeTabForLayer(imageDisplay.geoLayer);
        }
    }
}
