// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.testutils.annotations;

import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.openstreetmap.josm.gui.MainApplicationTest;
import org.openstreetmap.josm.gui.tagging.presets.TaggingPresetsTest;
import org.openstreetmap.josm.testutils.JOSMTestRules;
import org.xml.sax.SAXException;

/**
 * Use presets in tests.
 *
 * @author Taylor Smock
 * @see JOSMTestRules#presets()
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD, ElementType.TYPE })
@BasicPreferences
@ExtendWith(TaggingPresets.TaggingPresetsExtension.class)
public @interface TaggingPresets {

    class TaggingPresetsExtension implements BeforeEachCallback {

        @Override
        public void beforeEach(ExtensionContext extensionContext) {
            setup();
        }

        /**
         * Setup the tagging presets
         */
        public static synchronized void setup() {
            try {
                MainApplicationTest.setTaggingPresets(TaggingPresetsTest.initFromDefaultPresets());
            } catch (SAXException | IOException e) {
                MainApplicationTest.setTaggingPresets(null);
            }
        }
    }
}
