// License: GPL. For details, see LICENSE file.

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.openstreetmap.josm.data.projection.CustomProjection;
import org.openstreetmap.josm.data.projection.CustomProjection.Param;
import org.openstreetmap.josm.data.projection.ProjectionConfigurationException;
import org.openstreetmap.josm.data.projection.Projections;
import org.openstreetmap.josm.data.projection.Projections.ProjectionDefinition;
import org.openstreetmap.josm.data.projection.proj.Proj;
import org.openstreetmap.josm.tools.Logging;

/**
 * Generates the list of projections by combining two sources: The list from the
 * proj.4 project and a list maintained by the JOSM team.
 */
public final class BuildProjectionDefinitions {

    private static final String PROJ_DIR = "nodist/data/projection";
    private static final String JOSM_EPSG_FILE = "josm-epsg";
    private static final String PROJ4_EPSG_FILE = "epsg";
    private static final String PROJ4_ESRI_FILE = "esri";
    private static final String OUTPUT_EPSG_FILE = "resources/data/projection/custom-epsg";

    private static final Map<String, ProjectionDefinition> epsgProj4 = new LinkedHashMap<>();
    private static final Map<String, ProjectionDefinition> esriProj4 = new LinkedHashMap<>();
    private static final Map<String, ProjectionDefinition> epsgJosm = new LinkedHashMap<>();

    private static final boolean printStats = false;

    // statistics:
    private static int noInJosm;
    private static int noInProj4;
    private static int noDeprecated;
    private static int noGeocent;
    private static int noBaseProjection;
    private static int noEllipsoid;
    private static int noNadgrid;
    private static int noDatumgrid;
    private static int noJosm;
    private static int noProj4;
    private static int noEsri;
    private static int noOmercNoBounds;
    private static int noEquatorStereo;

    private static final Map<String, Integer> baseProjectionMap = new TreeMap<>();
    private static final Map<String, Integer> ellipsoidMap = new TreeMap<>();
    private static final Map<String, Integer> nadgridMap = new TreeMap<>();
    private static final Map<String, Integer> datumgridMap = new TreeMap<>();

    private static List<String> knownGeoidgrids;
    private static List<String> knownNadgrids;

    private BuildProjectionDefinitions() {
    }

    /**
     * Program entry point
     * @param args command line arguments (not used)
     * @throws IOException if any I/O error occurs
     */
    public static void main(String[] args) throws IOException {
        Projections.initFrom(null);
        buildList(args.length > 0 ? args[0] : ".",
                  args.length > 1 ? args[1] : OUTPUT_EPSG_FILE);
    }

    static List<String> initList(String baseDir, String ext) throws IOException {
        return Files.list(Paths.get(baseDir).resolve(PROJ_DIR))
                .map(path -> path.getFileName().toString())
                .filter(name -> !name.contains(".") || name.toLowerCase(Locale.ENGLISH).endsWith(ext))
                .collect(Collectors.toList());
    }

    static void initMap(String baseDir, String file, Map<String, ProjectionDefinition> map) throws IOException {
        final Path path = Paths.get(baseDir).resolve(PROJ_DIR).resolve(file);
        final List<ProjectionDefinition> list;
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            list = Projections.loadProjectionDefinitions(reader);
        }
        if (list.isEmpty())
            throw new AssertionError("EPSG file seems corrupted");
        Pattern badDmsPattern = Pattern.compile("(\\d+(?:\\.\\d+)?d\\d+(?:\\.\\d+)?')([NSEW])");
        for (ProjectionDefinition pd : list) {
            // DMS notation without second causes problems with cs2cs, add 0"
            Matcher matcher = badDmsPattern.matcher(pd.definition);
            StringBuffer sb = new StringBuffer();
            while (matcher.find()) {
                matcher.appendReplacement(sb, matcher.group(1) + "0\"" + matcher.group(2));
            }
            matcher.appendTail(sb);
            map.put(pd.code, new ProjectionDefinition(pd.code, pd.name, sb.toString()));
        }
    }

    static void buildList(String baseDir, String outputFile) throws IOException {
        initMap(baseDir, JOSM_EPSG_FILE, epsgJosm);
        initMap(baseDir, PROJ4_EPSG_FILE, epsgProj4);
        initMap(baseDir, PROJ4_ESRI_FILE, esriProj4);

        knownGeoidgrids = initList(baseDir, ".gtx");
        knownNadgrids = initList(baseDir, ".gsb");

        Path output = Paths.get(baseDir).resolve(outputFile);
        Files.createDirectories(output.getParent());
        System.out.println("Writing file " + output);
        try (Writer out = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
            out.write("## This file is autogenerated, do not edit!\n");
            out.write("## Run ant task \"epsg\" to rebuild.\n");
            out.write(String.format("## Source files are %s (can be changed), %s and %s (copied from the proj.4 project).%n",
                    JOSM_EPSG_FILE, PROJ4_EPSG_FILE, PROJ4_ESRI_FILE));
            out.write("##\n");
            out.write("## Entries checked and maintained by the JOSM team:\n");
            for (ProjectionDefinition pd : epsgJosm.values()) {
                write(out, pd);
                noJosm++;
            }
            out.write("## Other supported projections (source: proj.4):\n");
            for (ProjectionDefinition pd : epsgProj4.values()) {
                if (doInclude(pd, true, false)) {
                    write(out, pd);
                    noProj4++;
                }
            }
            out.write("## ESRI-specific projections (source: ESRI):\n");
            for (ProjectionDefinition pd : esriProj4.values()) {
                pd = new ProjectionDefinition(pd.code, "ESRI: " + pd.name, pd.definition);
                if (doInclude(pd, true, true)) {
                    write(out, pd);
                    noEsri++;
                }
            }
        }

        if (printStats) {
            System.out.printf("loaded %d entries from %s%n", epsgJosm.size(), JOSM_EPSG_FILE);
            System.out.printf("loaded %d entries from %s%n", epsgProj4.size(), PROJ4_EPSG_FILE);
            System.out.printf("loaded %d entries from %s%n", esriProj4.size(), PROJ4_ESRI_FILE);
            System.out.println();
            System.out.println("some entries from proj.4 have not been included:");
            System.out.printf(" * already in the maintained JOSM list: %d entries%n", noInJosm);
            if (noInProj4 > 0) {
                System.out.printf(" * ESRI already in the standard EPSG list: %d entries%n", noInProj4);
            }
            System.out.printf(" * deprecated: %d entries%n", noDeprecated);
            System.out.printf(" * using +proj=geocent, which is 3D (X,Y,Z) and not useful in JOSM: %d entries%n", noGeocent);
            if (noEllipsoid > 0) {
                System.out.printf(" * unsupported ellipsoids: %d entries%n", noEllipsoid);
                System.out.println("   in particular: " + ellipsoidMap);
            }
            if (noBaseProjection > 0) {
                System.out.printf(" * unsupported base projection: %d entries%n", noBaseProjection);
                System.out.println("   in particular: " + baseProjectionMap);
            }
            if (noDatumgrid > 0) {
                System.out.printf(" * requires data file for vertical datum conversion: %d entries%n", noDatumgrid);
                System.out.println("   in particular: " + datumgridMap);
            }
            if (noNadgrid > 0) {
                System.out.printf(" * requires data file for datum conversion: %d entries%n", noNadgrid);
                System.out.println("   in particular: " + nadgridMap);
            }
            if (noOmercNoBounds > 0) {
                System.out.printf(
                        " * projection is Oblique Mercator (requires bounds), but no bounds specified: %d entries%n", noOmercNoBounds);
            }
            if (noEquatorStereo > 0) {
                System.out.printf(" * projection is Equatorial Stereographic (see #15970): %d entries%n", noEquatorStereo);
            }
            System.out.println();
            System.out.printf("written %d entries from %s%n", noJosm, JOSM_EPSG_FILE);
            System.out.printf("written %d entries from %s%n", noProj4, PROJ4_EPSG_FILE);
            System.out.printf("written %d entries from %s%n", noEsri, PROJ4_ESRI_FILE);
        }
    }

    static void write(Writer out, ProjectionDefinition pd) throws IOException {
        out.write("# " + pd.name + "\n");
        out.write("<"+pd.code.substring("EPSG:".length())+"> "+pd.definition+" <>\n");
    }

    static boolean doInclude(ProjectionDefinition pd, boolean noIncludeJosm, boolean noIncludeProj4) {

        boolean result = true;

        if (noIncludeJosm) {
            // we already have this projection
            if (epsgJosm.containsKey(pd.code)) {
                result = false;
                noInJosm++;
            }
        }
        if (noIncludeProj4) {
            // we already have this projection
            if (epsgProj4.containsKey(pd.code)) {
                result = false;
                noInProj4++;
            }
        }

        // exclude deprecated/discontinued projections
        // EPSG:4296 is also deprecated, but this is not mentioned in the name
        String lowName = pd.name.toLowerCase(Locale.ENGLISH);
        if (lowName.contains("deprecated") || lowName.contains("discontinued") || pd.code.equals("EPSG:4296")) {
            result = false;
            noDeprecated++;
        }

        // exclude projections failing
        // CHECKSTYLE.OFF: LineLength
        if (Arrays.asList(
                // Unsuitable parameters 'lat_1' and 'lat_2' for two point method
                "EPSG:53025", "EPSG:54025", "EPSG:65062",
                // ESRI projection defined as UTM 55N but covering a much bigger area
                "EPSG:102449",
                // Others: errors to investigate
                "EPSG:102061", // omerc/evrst69 - Everest_Modified_1969_RSO_Malaya_Meters [Everest Modified 1969 RSO Malaya Meters]
                "EPSG:102062", // omerc/evrst48 - Kertau_RSO_Malaya_Meters [Kertau RSO Malaya Meters]
                "EPSG:102121", // omerc/NAD83   - NAD_1983_Michigan_GeoRef_Feet_US [NAD 1983 Michigan GeoRef (US Survey Feet)]
                "EPSG:102212", // lcc/NAD83     - NAD_1983_WyLAM [NAD 1983 WyLAM]
                "EPSG:102366", // omerc/GRS80   - NAD_1983_CORS96_StatePlane_Alaska_1_FIPS_5001 [NAD 1983 (CORS96) SPCS Alaska Zone 1]
                "EPSG:102445", // omerc/GRS80   - NAD_1983_2011_StatePlane_Alaska_1_FIPS_5001_Feet [NAD 1983 2011 SPCS Alaska Zone 1 (US Feet)]
                "EPSG:102491", // lcc/clrk80ign - Nord_Algerie_Ancienne_Degree [Voirol 1875 (degrees) Nord Algerie Ancienne]
                "EPSG:102591", // lcc           - Nord_Algerie_Degree [Voirol Unifie (degrees) Nord Algerie]
                "EPSG:102631", // omerc/NAD83   - NAD_1983_StatePlane_Alaska_1_FIPS_5001_Feet [NAD 1983 SPCS Alaska 1 (Feet)]
                "EPSG:103232", // lcc/GRS80     - NAD_1983_CORS96_StatePlane_California_I_FIPS_0401 [NAD 1983 (CORS96) SPCS California I]
                "EPSG:103235", // lcc/GRS80     - NAD_1983_CORS96_StatePlane_California_IV_FIPS_0404 [NAD 1983 (CORS96) SPCS California IV]
                "EPSG:103238", // lcc/GRS80     - NAD_1983_CORS96_StatePlane_California_I_FIPS_0401_Ft_US [NAD 1983 (CORS96) SPCS California I (US Feet)]
                "EPSG:103241", // lcc/GRS80     - NAD_1983_CORS96_StatePlane_California_IV_FIPS_0404_Ft_US [NAD 1983 (CORS96) SPCS California IV (US Feet)]
                "EPSG:103371", // lcc/GRS80     - NAD_1983_HARN_WISCRS_Wood_County_Meters [NAD 1983 HARN Wisconsin CRS Wood (meters)]
                "EPSG:103471", // lcc/GRS80     - NAD_1983_HARN_WISCRS_Wood_County_Feet [NAD 1983 HARN Wisconsin CRS Wood (US feet)]
                "EPSG:103474", // lcc/GRS80     - NAD_1983_CORS96_StatePlane_Nebraska_FIPS_2600 [NAD 1983 (CORS96) SPCS Nebraska]
                "EPSG:103475"  // lcc/GRS80     - NAD_1983_CORS96_StatePlane_Nebraska_FIPS_2600_Ft_US [NAD 1983 (CORS96) SPCS Nebraska (US Feet)]
                ).contains(pd.code)) {
            result = false;
        }
        // CHECKSTYLE.ON: LineLength

        Map<String, String> parameters;
        try {
            parameters = CustomProjection.parseParameterList(pd.definition, true);
        } catch (ProjectionConfigurationException ex) {
            throw new IllegalStateException(pd.code + ":" + ex, ex);
        }
        String proj = parameters.get(CustomProjection.Param.proj.key);
        if (proj == null) {
            result = false;
        }

        // +proj=geocent is 3D (X,Y,Z) "projection" - this is not useful in
        // JOSM as we only deal with 2D maps
        if ("geocent".equals(proj)) {
            result = false;
            noGeocent++;
        }

        // no support for NAD27 datum, as it requires a conversion database
        String datum = parameters.get(CustomProjection.Param.datum.key);
        if ("NAD27".equals(datum)) {
            result = false;
            noDatumgrid++;
        }

        // requires vertical datum conversion database (.gtx)
        String geoidgrids = parameters.get("geoidgrids");
        if (geoidgrids != null && !"@null".equals(geoidgrids) && !knownGeoidgrids.contains(geoidgrids)) {
            result = false;
            noDatumgrid++;
            incMap(datumgridMap, geoidgrids);
        }

        // requires datum conversion database (.gsb)
        String nadgrids = parameters.get("nadgrids");
        if (nadgrids != null && !"@null".equals(nadgrids) && !knownNadgrids.contains(nadgrids)) {
            result = false;
            noNadgrid++;
            incMap(nadgridMap, nadgrids);
        }

        // exclude entries where we don't support the base projection
        Proj bp = Projections.getBaseProjection(proj);
        if (result && !"utm".equals(proj) && bp == null) {
            result = false;
            noBaseProjection++;
            if (!"geocent".equals(proj)) {
                incMap(baseProjectionMap, proj);
            }
        }

        // exclude entries where we don't support the base ellipsoid
        String ellps = parameters.get("ellps");
        if (result && ellps != null && Projections.getEllipsoid(ellps) == null) {
            result = false;
            noEllipsoid++;
            incMap(ellipsoidMap, ellps);
        }

        if (result && "omerc".equals(proj) && !parameters.containsKey(CustomProjection.Param.bounds.key)) {
            result = false;
            noOmercNoBounds++;
        }

        final double eps10 = 1.e-10;

        String lat0 = parameters.get("lat_0");
        if (lat0 != null) {
            try {
                final double latitudeOfOrigin = Math.toRadians(CustomProjection.parseAngle(lat0, Param.lat_0.key));
                // TODO: implement equatorial stereographic, see https://josm.openstreetmap.de/ticket/15970
                if (result && "stere".equals(proj) && Math.abs(latitudeOfOrigin) < eps10) {
                    result = false;
                    noEquatorStereo++;
                }

                // exclude entries which need geodesic computation (equatorial/oblique azimuthal equidistant)
                if (result && "aeqd".equals(proj)) {
                    final double halfPi = Math.PI / 2;
                    if (Math.abs(latitudeOfOrigin - halfPi) >= eps10 &&
                        Math.abs(latitudeOfOrigin + halfPi) >= eps10) {
                        // See https://josm.openstreetmap.de/ticket/16129#comment:21
                        result = false;
                    }
                }
            } catch (NumberFormatException | ProjectionConfigurationException e) {
                e.printStackTrace();
                result = false;
            }
        }

        if (result && "0.0".equals(parameters.get("rf"))) {
            // Proj fails with "reciprocal flattening (1/f) = 0" for
            result = false; // FIXME Only for some projections?
        }

        String k0 = parameters.get("k_0");
        if (result && k0 != null && k0.startsWith("-")) {
            // Proj fails with "k <= 0" for ESRI:102470
            result = false;
        }
        return result;
    }

    private static void incMap(Map<String, Integer> map, String key) {
        map.putIfAbsent(key, 0);
        map.put(key, map.get(key)+1);
    }
}
