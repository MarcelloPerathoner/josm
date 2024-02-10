This is my personal edition of JOSM.

Caveat: this JOSM custom version is not binary compatible with the official plugins.

Here are some `fixed plugins <https://github.com/MarcelloPerathoner/josm-plugins/releases>`_.

The home directory is `JOSM-custom` instead of `JOSM`. A parallel installation is
possible.

Tip: If this version crashes start it with --skip-plugins.


Changes From the Official Version
=================================

Contents
--------

.. contents::
    :local:

Editable Properties Dialog
--------------------------

#. The properties dialog is made editable, with drop-down suggestion comboboxes.

   .. image:: demo/properties-editable.png


HiDPI
-----

Make JOSM usable on 4K monitors.  Tested themes: GTK, flatlaf.  (Metal, Nimbus, and CDE
are not designed to scale.)  Please test Windows and Mac themes and open issues with
screenshots attached.

#. Table rows are now the correct height.  Added more functions in `TableHelper` to deal
   with row height.

#. Captions of dialogs in the sidebar are now the correct height.

   .. figure:: demo/properties-18622.png

      Example of wrong caption and row heights as seen in JOSM 18622 on 4K monitor.

   .. figure:: demo/properties.png

      Correct caption and row height.

#. Resizing icons with MapCSS now does resize hit test area.

#. Labels in the status bar are now the correct width.

#. Progress dialogs are the correct size.

#. The graph in the "linked" column of the relation editor is now better visible.

#. The preferences dialog follows the L&F.

   .. image:: demo/prefs-darcula.png

   .. image:: demo/prefs-intellij.png


Tagging Presets
---------------

Tagging presets have been completely rewritten.

#. Conditional tags: Show or hide preset fields based on mapcss queries.

   Example: language switching according to polygons in `boundaries.osm`.

   .. code:: xml

      <switch>
         <case map_css='*[inside("IT-BZ-X-LLD")]'>
            <label text="Languages: Ladin - German - Italian" />
         </case>
         <case map_css='*[inside("IT-BZ-X-IT")]'>
            <label text="Languages: Italian - German" />
         </case>
         <case map_css='*[inside("IT-BZ")]'>
            <label text="Languages: German - Italian" />
         </case>
      </switch>

   The very same preset applied in 3 different localities.
   Watch the computed name:

   .. image:: demo/name-lld-de-it.png

   .. image:: demo/name-de-it.png

   .. image:: demo/name-it-de.png

#. Tabbed panes:

   .. image:: demo/tabs.png

#. Tall preset menus are multi-column instead of scrollable.

#. Icon size in the preset menus is configurable.

#. Preset menu entries can have tooltips.

   .. figure:: demo/preset-icons-multi-big.png

      Example of a traffic sign preset without plugins.

#. `no_menu` attribute for presets that should be searchable but should not have menus,
   eg. the Name Suggestion Index preset.

#. Experimental `append_*` attributes for presets that need to append values to existing
   tags.  eg. traffic_sign

#. The validator now highlights invalid fields and puts error messages in the tooltip.

   .. image:: demo/validator-highlight.png

#. The validator can be enabled/disabled from every preset dialog.

#. Calculated fields can be individually or globally enabled/disabled.

#. The XML reader has been completely rewritten not to use java introspection any more,
   considerably reducing the number of public fields and methods.

#. All classes comprising tagging Presets have been placed into one package, further
   reducing the number of public fields and methods.

#. `TaggingPresets` is not a global static class anymore.
   All unit tests that change presets can run in parallel now.

#. The concepts of `preset template` and `preset instance` are cleanly separated. The
   XML file gets parsed into a tree of *immutable* preset templates. The templates are
   used to create swing `dialogs` and mutable `instances`.

#. Preset patch files have been added as experimental feature to allow further
   customization of existing preset files. A preset patch file's main function is to
   override chunks in the preset file. A preset patch file has the same structure as the
   `defaultpresets.xml` file. All items in the root of the preset patch file will be
   appended to the root of the respective presets file, ie. the root elements of both
   files will be merged while chunks in the patch file will override chunks with the
   same `id` in the presets file. The patch file must be placed in the `josmdir://` and
   have the same filename and extension with an added extension of `.local` eg.
   `<josmdir>/defaultpresets.xml.local`.

#. Clean interface for plugins that need to explore known tags.

#. The preset system now uses a pluggable handler for all data access so any preset can
   operate on the dataset or any other key/value store like the tag table in the
   relation editor. Fixes #21221

#. Autocomplete suggestions can be filtered in the relation editor. Comboboxes have been
   added that provide suggestions. Fixes #21227


MapCSS
------

#. A new `globals` sections, for global properties.

   .. code:: css

      globals {
         my-regexp: "my very big regexp goes here";
      }
      node {
         test: get(regexp_match(prop("my-regexp", globals), tag("test")), 1);
      }
      way {
         test: get(regexp_match(prop("my-regexp", globals), tag("test")), 1);
      }

#. A new `map` type with `map_build` and `map_get` functions.

   .. code:: css

      globals {
         colors: map_build("primary", red, "secondary", orange, "tertiary", yellow);
      }
      way[highway] {
         color: map_get(prop(colors, globals), tag(highway), #fff);
      }

#. Generic CSS transformations to translate, rotate, scale, and skew have been
   implemented.

   .. code:: css

      way[highway] > node[traffic_sign][direction=forward] {
          icon-transform: transform(translate(10, 20), rotate(heading()));
      }

   .. image:: demo/transform.png

#. New function `heading` to rotate symbols in the direction of a way.
   Requested in #10271, #22539.

   .. image:: demo/heading.png

#. New command to rotate icons.

   .. image:: demo/rotate_traffic_signs.gif

#. MapCSS rotation has been fixed to rotate around the centerpoint.

#. Patching of SVG files: `icon-image: path/to/maxspeed.svg?maxspeed=70` will search for
   `{{maxspeed}}` in the SVG and replace it with `70`. Use one icon for all speeds.
   Multiple replacements are possible.

#. New functions: `split_traffic_sign` and `URL_query_encode`.

#. Experimental: Caching of expressions has been implemented to speed up applying of
   stylesheets.

   Expressions can specify if they are IMMUTABLE, STABLE or VOLATILE.  Results of
   evaluating IMMUTABLE expressions can always be cached.  Results of STABLE expression
   can be cached as long as the DataSet does not change.  Cacheability does propagate:
   `max(1, 2)` is IMMUTABLE but `max(1, tag(lanes))` is STABLE.

#. Experimental: Parallel rendering has been implemented.  Renders about twice as fast
   on an 8-core machine.  Memory bandwith seems to be a bottleneck.  Further profiling
   is needed.


Traffic signs
-------------

This clip showcases how some of the enhancements described above work together to
implement easy tagging of traffic signs.  The improvements that work together are:

- multi-column menus
- resizable icons in menus
- append_* in tagging presets
- parameterized SVGs
- new functions in mapcss
- icon rotation

.. raw:: html

   <video src="demo/add-traffic-signs.mkv"></video>


Plugin Preferences Rewritten
----------------------------

The preference pane for the plugin system has been rewritten from scratch. It is now
possible to download plugins from GitHub assets.

.. figure:: demo/plugins.gif

   The new plugin preference pane


Notification System Rewritten
-----------------------------

Multiple notifcations now stack up in the bottom-left corner of the main window.
Notifications can have progressbars.


ImageViewerDialog Rewritten
---------------------------

#. ImageViewerDialog now uses a tabbed pane with the correct L&F.

#. Big code cleanup

   .. figure:: demo/imageviewer.png

      Image viewer using the *flatlaf darcula* theme.


Built with Gradle
-----------------

Gradle replaces Ant as building tool. (Not all tasks yet.)
