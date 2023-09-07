// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.io;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.openstreetmap.josm.data.Version;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.gui.tagging.TagTableModel;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.Utils;

/**
 * A model for the upload dialog
 *
 * @since 18173
 */
public class UploadDialogModel extends TagTableModel {
    /** the "created_by" changeset OSM key */
    private static final String CREATED_BY = "created_by";
    /** the "comment" changeset OSM key */
    public static final String COMMENT = "comment";
    /** the "source" changeset OSM key */
    public static final String SOURCE = "source";
    /** the user-agent */
    private final String agent = Version.getInstance().getAgentString(false);
    /** whether to extract hashtags from comment */
    private final boolean hashtags = Config.getPref().getBoolean("upload.changeset.hashtags", true);

    /**
     * Constructor
     */
    public UploadDialogModel() {
        super(null);
    }

    @Override
    public void ensureTags() {
        super.ensureTags();
        // careful! never delete any tags here or you'll loop
        if (hashtags) {
            String hashTag = findHashTags(getValue(COMMENT));
            if (hashTag != null)
                put("hashtags", hashTag);
        }
        // add/update "created_by"
        final String createdBy = getValue(CREATED_BY);
        if (createdBy.isEmpty()) {
            put(CREATED_BY, agent);
        } else if (!createdBy.contains(agent)) {
            put(CREATED_BY, createdBy + ';' + agent);
        }
    }

    /**
     * Get the value of a key.
     *
     * @param key The key to retrieve
     * @return The value
     */
    public String getValue(String key) {
        return get(key).toString();
    }

    /**
     * Extracts the list of hashtags from the comment text.
     * @param comment The comment with the hashtags
     * @return the hashtags separated by ";" or null
     */
    String findHashTags(String comment) {
        String foundHashtags = Arrays.stream(comment.split("\\s", -1))
            .map(s -> Utils.strip(s, ",;"))
            .filter(s -> s.matches("#[a-zA-Z0-9][-_a-zA-Z0-9]+"))
            .distinct().collect(Collectors.joining(";"));
        return foundHashtags.isEmpty() ? null : foundHashtags;
    }

    /**
     * Returns the given comment with appended hashtags from dataset changeset tags, if not already present.
     * @param comment changeset comment. Can be null
     * @param dataSet optional dataset, which can contain hashtags in its changeset tags
     * @return comment with dataset changesets tags, if any, not duplicated
     */
    static String addHashTagsFromDataSet(String comment, DataSet dataSet) {
        StringBuilder result = comment == null ? new StringBuilder() : new StringBuilder(comment);
        if (dataSet != null) {
            String hashtags = dataSet.getChangeSetTags().get("hashtags");
            if (hashtags != null) {
                Set<String> sanitizedHashtags = new LinkedHashSet<>();
                for (String hashtag : hashtags.split(";", -1)) {
                    if (comment == null || !comment.contains(hashtag)) {
                        sanitizedHashtags.add(hashtag.startsWith("#") ? hashtag : "#" + hashtag);
                    }
                }
                if (!sanitizedHashtags.isEmpty()) {
                    result.append(' ').append(String.join(" ", sanitizedHashtags));
                }
            }
        }
        return result.toString();
    }

    /**
     * Inserts all tags from a {@code DataSet}.
     *
     * @param dataSet The DataSet to take tags from.
     */
    public void putAll(DataSet dataSet) {
        if (dataSet != null) {
            putAll(dataSet.getChangeSetTags());
            String comment = addHashTagsFromDataSet(getValue(COMMENT), dataSet);
            if (!Utils.isEmpty(comment))
                put(COMMENT, comment);
        }
    }

    /**
     * Determines if the key is "comment" or "source".
     * @param key changeset key
     * @return {@code true} if the key is "comment" or "source"
     * @since 18283
     */
    public static boolean isCommentOrSource(String key) {
        return COMMENT.equals(key) || SOURCE.equals(key);
    }
}
