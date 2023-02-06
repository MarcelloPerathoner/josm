This is my personal edition of JOSM.


Changes From the Official Version
=================================

Caveat: this changed version is not binary compatible with most official plugins.
Plugins have been fixed but are not yet published.

HiDPI
-----

Make JOSM more HiDPI-friendly especially when using non-integer scaling factors.  Tested
themes: GTK, flatlaf.  Metal, Nimbus, CDE do not scale.  Please test Windows and Mac
themes and open issues with screenshots attached.

#. Table rows are now the correct height.  Added more functions in TableHelper to deal
   with row height.

#. Captions of dialogs in the sidebar are now the correct height.

#. Resizing icons with MapCSS now does resize hit test area.

#. Labels in the status bar are now the correct width.

#. Progress dialogs are the correct size.

#. The "linked" column in the relation editor is now better visible.

#. Preferences have been fixed re. correct use of L&F.

   .. image:: demo/prefs-darcula.png

   .. image:: demo/prefs-intellij.png


Tagging Presets
---------------

#. Conditional tags have been implemented:
   Show or hide preset fields based on mapcss queries.

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

   The very same preset applied in 3 different localities:

   .. image:: demo/name-lld-de-it.png

   .. image:: demo/name-de-it.png

   .. image:: demo/name-it-de.png

#. Tabbed panes for preset dialogs have been implemented.

   .. image:: demo/tabs.png

#. Tall preset menus are multi-column instead of scrollable.

#. Icon size in the preset menus is configurable.

#. Preset menu entries can have tooltips.

   .. figure:: demo/preset-icons-multi-big.png

      Example of a traffic sign preset without plugins.

#. The validator now highlights invalid fields and puts error messages in the tooltip.

   .. image:: demo/validator-highlight.png

#. The validator can be enabled/disabled from every preset dialog.

#. Calculated fields can be individually or globally enabled/disabled.

#. The XML reader is completely rewritten not to use java introspection any more,
   considerably reducing the number of public fields and methods.

#. All classes comprising tagging Presets have been placed into one package, further
   reducing the number of public fields and methods.

#. TaggingPresets is not a global static class anymore.
   All tests that change presets can run in parallel now.

#. The concepts of `preset template` and `preset instance` are cleanly separated. The
   XML file gets parsed into a tree of *immutable* preset templates. The templates are
   used to create `dialogs` and mutable `instances`.

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


Property Dialog Made Editable
-----------------------------

#. The properties dialog is made editable, with drop-down suggestion comboboxes.

   .. image:: demo/properties-editable.png


Heading in MapCSS
-----------------

#. A new function heading() has been added to rotate symbols in the direction of a way.
   Requested in #10271, #22539.

   .. image:: demo/heading.png

#. Rotation has been fixed to rotate around the centerpoint.
   Correct rotation of text and additional panels:

   .. image:: demo/rotation.png


ImageViewerDialog Rewritten
---------------------------

#. ImageViewerDialog now uses a tabbed pane with the correct L&F.

#. Big code cleanup

   .. figure:: demo/imageviewer.png

      Image viewer using the *flatlaf darcula* theme.


Built with Gradle
-----------------

Gradle replaces Ant as building tool. (Not all tasks yet.)
