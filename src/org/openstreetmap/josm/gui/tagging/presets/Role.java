// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.tagging.presets;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.openstreetmap.josm.data.osm.OsmPrimitiveType;
import org.openstreetmap.josm.data.osm.search.SearchCompiler;
import org.openstreetmap.josm.data.osm.search.SearchParseError;
import org.openstreetmap.josm.data.osm.search.SearchSetting;
import org.openstreetmap.josm.tools.GBC;
import org.openstreetmap.josm.tools.ImageProvider;

/**
 * The <code>role</code> element in tagging preset definition.
 * <p>
 * Information on a certain role, which is expected for the relation members.
 * <p>
 * FIXME: RelationChecker needs this class to be public but it should be package-private.
 */
public final class Role extends TextItem {
    /** the right margin */
    private static final int RIGHT = 10;

    /** Role name used in a relation */
    private final String key;
    /** Presets types expected for this role */
    private final EnumSet<TaggingPresetType> types;
    /** Is the role name a regular expression */
    private final boolean regexp;
    /** How often must the element appear */
    private final int count;
    /** An expression (cf. search dialog) for objects of this role */
    private final SearchCompiler.Match memberExpression;
    /** Is this role required at least once in the relation? */
    private final boolean required;

    /**
     * Private constructor. Use {@link #fromXML} instead.
     * @param attributes the XML attributes
     * @throws IllegalArgumentException on illegal attributes
     */
    private Role(Map<String, String> attributes) throws IllegalArgumentException {
        super(attributes);
        key = attributes.get("key");
        types = TaggingPresetType.getOrDefault(attributes.get("type"), EnumSet.noneOf(TaggingPresetType.class));
        regexp = TaggingPresetUtils.parseBoolean(attributes.getOrDefault("regexp", "false"));
        count = Integer.parseInt(attributes.getOrDefault("count", "0"));
        memberExpression = parseSearchExpression(attributes.get("member_expression"));
        required = parseRequisite(attributes.getOrDefault("requisite", "optional"));
    }

    /**
     * Convenience constructor (also for testing purposes)
     * @param key the key
     * @param types the tagging preset types this role applies to
     */
    public Role(String key, Set<TaggingPresetType> types) {
        super(ItemFactory.attributesToMap());
        this.key = key;
        this.types = EnumSet.copyOf(types);
        this.regexp = false;
        this.count = 0;
        this.memberExpression = null;
        this.required = false;
    }

    /**
     * Create this class from an XML element's attributes.
     * @param attributes the XML attributes
     * @return the new instance
     * @throws IllegalArgumentException on invalid attributes
     */
    static Role fromXML(Map<String, String> attributes) throws IllegalArgumentException {
        return new Role(attributes);
    }

    /**
     * Returns the key
     * @return the key
     */
    public String getKey() {
        return key;
    }

    /**
     * Returns the member expression
     * @return the member expression
     */
    public SearchCompiler.Match getMemberExpression() {
        return memberExpression;
    }

    /**
     * Sets whether this role is required at least once in the relation.
     * @param str "required" or "optional"
     * @return true if required
     * @throws IllegalArgumentException if str is neither "required" or "optional"
     */
    private boolean parseRequisite(String str) throws IllegalArgumentException {
        if ("required".equals(str)) {
            return true;
        } else if ("optional".equals(str)) {
            return false;
        }
        throw new IllegalArgumentException(tr("Unknown requisite: {0}", str));
    }

    /**
     * Sets an expression (cf. search dialog) for objects of this role
     * @param memberExpression an expression (cf. search dialog) for objects of this role
     * @return the match expression
     * @throws IllegalArgumentException in case of parsing error
     */
    private SearchCompiler.Match parseSearchExpression(String memberExpression) throws IllegalArgumentException {
        if (memberExpression == null)
            return null;
        try {
            final SearchSetting searchSetting = new SearchSetting();
            searchSetting.text = memberExpression;
            searchSetting.caseSensitive = true;
            searchSetting.regexSearch = true;
            return SearchCompiler.compile(searchSetting);
        } catch (SearchParseError ex) {
            throw new IllegalArgumentException(tr("Illegal member expression: {0}", ex.getMessage()), ex);
        }
    }

    /**
     * Return either argument, the highest possible value or the lowest allowed value.  Used by
     * relation checker.
     * @param c count
     * @return the highest possible value or the lowest allowed value
     * @see #required
     */
    public long getValidCount(long c) {
        if (count > 0 && !required) {
            return c != 0 ? count : 0;
        } else if (count > 0) {
            return count;
        } else if (!required) {
            return c != 0 ? c : 0;
        } else {
            return c != 0 ? c : 1;
        }
    }

    /**
     * Returns the preset types that this role may be applied to
     * @return the preset types
     */
    public EnumSet<TaggingPresetType> getTypes() {
        return EnumSet.copyOf(types);
    }

    /**
     * Returns true if this role may be applied to the given preset type, eg. node / way ...
     *
     * @param presetType The preset type
     * @return true if this role may be applied to the given preset type.
     */
    public boolean appliesTo(TaggingPresetType presetType) {
        return types.contains(presetType);
    }

    /**
     * Returns true if this role may be applied to the given primitive type, eg. node / way ...
     *
     * @param primitiveType The OSM primitive type
     * @return true if this role may be applied to the given primitive type.
     */
    public boolean appliesTo(OsmPrimitiveType primitiveType) {
        return types.contains(TaggingPresetType.forPrimitiveType(primitiveType));
    }

    /**
     * Returns true if this role may be applied to all of the given preset types.
     * <p>
     * Returns true if {@code role.types} contains all elements of {@code types}.
     *
     * @param presetTypes The preset types.
     * @return true if this role may be applied to all of the given preset types.
     */
    public boolean appliesToAll(Collection<TaggingPresetType> presetTypes) {
        return types.containsAll(presetTypes);
    }

    /**
     * Role if the given role matches this class (required to check regexp role types)
     * @param role role to check
     * @return <code>true</code> if role matches
     * @since 11989
     */
    public boolean isRole(String role) {
        if (regexp && role != null) { // pass null through, it will anyway fail
            return role.matches(this.key);
        }
        return this.key.equals(role);
    }

    /**
     * Adds this role to the given panel.
     * @param p panel where to add this role
     * @return {@code true}
     */
    @Override
    boolean addToPanel(JComponent p, Composite.Instance parentInstance) {
        GBC std = GBC.std().insets(0, 0, RIGHT, 0);
        GBC eol = GBC.eol().insets(0, 0, RIGHT, 0);

        String cstring;
        if (count > 0 && !required) {
            cstring = "0,"+count;
        } else if (count > 0) {
            cstring = String.valueOf(count);
        } else if (!required) {
            cstring = "0-...";
        } else {
            cstring = "1-...";
        }
        p.add(new JLabel(localeText+':'), std);
        p.add(new JLabel(key), std);
        p.add(new JLabel(cstring), std);

        JPanel typesPanel = new JPanel();
        for (TaggingPresetType t : types) {
            typesPanel.add(new JLabel(ImageProvider.get(t.getIconName())));
        }
        p.add(typesPanel, eol);
        return true;
    }

    @Override
    boolean isInteractive() {
        return true;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((key == null) ? 0 : key.hashCode());
        result = prime * result + ((types == null) ? 0 : types.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Role other = (Role) obj;
        if (key == null) {
            if (other.key != null)
                return false;
        } else if (!key.equals(other.key))
            return false;
        return types.equals(other.types);
    }

    @Override
    public String toString() {
        return key;
    }
}
