// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.mappaint.styleelement;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

import javax.swing.ImageIcon;

import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.mappaint.MapPaintStyles;
import org.openstreetmap.josm.gui.mappaint.StyleSource;
import org.openstreetmap.josm.gui.util.GuiHelper;
import org.openstreetmap.josm.tools.ColorHelper;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.ImageResource;
import org.openstreetmap.josm.tools.Logging;

/**
 * An image that will be displayed on the map.
 */
public class MapImage {

    private static final int MAX_SIZE = 48;

    /**
     * ImageIcon can change while the image is loading.
     */
    private Image img;
    private ImageResource imageResource;

    /**
     * The alpha (opacity) value of the image. It is multiplied to the image alpha channel.
     * Range: 0...255
     */
    public int alpha = 255;
    /**
     * The name of the image that should be displayed. It is given to the {@link ImageProvider}
     */
    public String name;
    /**
     * The StyleSource that registered the image
     */
    public StyleSource source;
    /**
     * A flag indicating that the image should automatically be scaled to the right size.
     */
    public boolean autoRescale;
    /**
     * The width of the image, as set by MapCSS
     */
    public int width = -1;
    /**
     * The height of the image, as set by MapCSS
     */
    public int height = -1;
    /**
     * The x offset of the anchor of this image
     */
    public int offsetX;
    /**
     * The y offset of the anchor of this image
     */
    public int offsetY;

    private boolean temporary;

    /**
     * A cache that holds a disabled (gray) version of this image
     */
    private BufferedImage disabledImgCache;

    /**
     * Creates a new {@link MapImage}
     * @param name The image name
     * @param source The style source that requests this image
     */
    public MapImage(String name, StyleSource source) {
        this(name, source, true);
    }

    /**
     * Creates a new {@link MapImage}
     * @param name The image name
     * @param source The style source that requests this image
     * @param autoRescale A flag indicating to automatically adjust the width/height of the image
     */
    public MapImage(String name, StyleSource source, boolean autoRescale) {
        this.name = name;
        this.source = source;
        this.autoRescale = autoRescale;
    }

    /**
     * Get the image associated with this MapImage object.
     *
     * @param disabled {@code} true to request disabled version, {@code false} for the standard version
     * @return the image
     */
    public Image getImage(boolean disabled) {
        if (disabled) {
            return getDisabled();
        } else {
            return getImage();
        }
    }

    /**
     * Get the image resource associated with this MapImage object.
     * This method blocks until the image resource has been loaded.
     * @return the image resource
     */
    public ImageResource getImageResource() {
        if (imageResource == null) {
            try {
                // load and wait for the image resource
                loadImageResource().get();
            } catch (ExecutionException | InterruptedException e) {
                Logging.warn(e);
                Thread.currentThread().interrupt();
            }
        }
        return imageResource;
    }

    private Image getDisabled() {
        if (disabledImgCache != null)
            return disabledImgCache;
        if (img == null)
            getImage(); // fix #7498 ?
        // This should fix #21919: NPE due to disabledImgCache being null (race condition with #loadImage())
        synchronized (this) {
            Image disImg = GuiHelper.getDisabledImage(img);
            if (disImg instanceof BufferedImage) {
                disabledImgCache = (BufferedImage) disImg;
            } else {
                disabledImgCache = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_ARGB);
                Graphics g = disabledImgCache.getGraphics();
                g.drawImage(disImg, 0, 0, null);
                g.dispose();
            }
        }
        return disabledImgCache;
    }

    private Image getImage() {
        if (img != null)
            return img;
        temporary = false;
        loadImage();
        synchronized (this) {
            if (img == null) {
                img = ImageProvider.get("clock").getImage();
                temporary = true;
            }
        }
        return img;
    }

    private CompletableFuture<Void> load(Consumer<? super ImageResource> action) {
        return new ImageProvider(name)
                .setDirs(MapPaintStyles.getIconSourceDirs(source))
                .setId("mappaint."+source.getPrefName())
                .setArchive(source.zipIcons)
                .setInArchiveDir(source.getZipEntryDirName())
                .setOptional(true)
                .getResourceAsync(action);
    }

    /**
     * Loads image resource and actual rescaled image.
     * @return the future of the requested image
     * @see #loadImageResource
     */
    private CompletableFuture<Void> loadImage() {
        return load(result -> {
            synchronized (this) {
                imageResource = result;
                if (result == null) {
                    source.logWarning(tr("Failed to locate image ''{0}''", name));
                    ImageIcon noIcon = MapPaintStyles.getNoIconIcon(source);
                    img = noIcon == null ? null : noIcon.getImage();
                } else {
                    img = result.getImageIcon(new Dimension(width, height)).getImage();
                    if (img != null && mustRescale(img)) {
                        // Scale down large images to 16x16 pixels if no size is explicitly specified
                        img = result.getImageIconBounded(ImageProvider.ImageSizes.MAP.getImageDimension()).getImage();
                    }
                }
                if (temporary) {
                    disabledImgCache = null;
                    MapView mapView = MainApplication.getMap().mapView;
                    mapView.preferenceChanged(null); // otherwise repaint is ignored, because layer hasn't changed
                    mapView.repaint();
                }
                temporary = false;
            }
        });
    }

    /**
     * Loads image resource only.
     * @return the future of the requested image resource
     * @see #loadImage
     */
    private CompletableFuture<Void> loadImageResource() {
        return load(result -> {
            synchronized (this) {
                imageResource = result;
                if (result == null) {
                    source.logWarning(tr("Failed to locate image ''{0}''", name));
                }
            }
        });
    }

    /**
     * Gets the image width
     * @return The real image width
     */
    public int getWidth() {
        return getImage().getWidth(null);
    }

    /**
     * Gets the image height
     * @return The real image height
     */
    public int getHeight() {
        return getImage().getHeight(null);
    }

    /**
     * Gets the alpha value the image should be multiplied with
     * @return The value in range 0..1
     */
    public float getAlphaFloat() {
        return ColorHelper.int2float(alpha);
    }

    /**
     * Determines if image is not completely loaded and {@code getImage()} returns a temporary image.
     * @return {@code true} if image is not completely loaded and getImage() returns a temporary image
     */
    public boolean isTemporary() {
        return temporary;
    }

    private boolean mustRescale(Image image) {
        return autoRescale && width == -1 && image.getWidth(null) > MAX_SIZE
             && height == -1 && image.getHeight(null) > MAX_SIZE;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        MapImage mapImage = (MapImage) obj;
        return alpha == mapImage.alpha &&
                autoRescale == mapImage.autoRescale &&
                width == mapImage.width &&
                height == mapImage.height &&
                Objects.equals(name, mapImage.name) &&
                Objects.equals(source, mapImage.source);
    }

    @Override
    public int hashCode() {
        return Objects.hash(alpha, name, source, autoRescale, width, height);
    }

    @Override
    public String toString() {
        return name;
    }
}
