package org.broadinstitute.hellbender.utils.help;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import org.apache.commons.io.FilenameUtils;
import org.broadinstitute.barclay.argparser.CommandLineProgramProperties;
import org.broadinstitute.barclay.argparser.RuntimeProperties;
import org.broadinstitute.barclay.help.*;

import java.io.*;
import java.util.List;
import java.util.Map;

/**
 * Custom Barclay-based Javadoc Doclet used for generating tool WDL.
 *
 * NOTE: Methods in this class are intended to be called by Gradle/Javadoc only, and should not be called
 * by methods that are used by the GATK runtime. This class has a dependency on com.sun.javadoc classes,
 * which may not be present since they're not provided as part of the normal GATK runtime classpath.
 */
@SuppressWarnings("removal")
public class GATKWDLDoclet extends WDLDoclet {

    // emit an index file with links to all of the .wdl files
    private final static String GATK_FREEMARKER_INDEX_TEMPLATE_NAME = "wdlIndexTemplate.html.ftl";

    // the directory where the wdlgen build is running
    public final static String OPT_BUILD_DIR = "-build-dir";
    private String buildDir;

    /**
     * Validates the given options against options supported by this doclet.
     *
     * @param option Option to validate.
     * @return Number of potential parameters; 0 if not supported.
     */
    public static int optionLength(final String option) {
        // Any arguments used for the doclet need to be recognized here. Many javadoc plugins (ie. gradle)
        // automatically add some such as "-doctitle", "-windowtitle", which we ignore.
        if (option.equals(OPT_BUILD_DIR)) {
            return 2;
        }
        return WDLDoclet.optionLength(option);
    }

    @Override
    protected boolean parseOption(final String[] option) {
        if (option[0].equals(OPT_BUILD_DIR)) {
            buildDir = option[1];
            return true;
        } else {
            return super.parseOption(option);
        }
    }

    /**
     * Create a WDL doclet and generate the FreeMarker templates properties.
     * @param rootDoc
     * @throws IOException
     */
    public static boolean start(final com.sun.javadoc.RootDoc rootDoc) throws IOException {
        return new GATKWDLDoclet().startProcessDocs(rootDoc);
    }

    /**
     * @return the location where the build is running; used in the cromwell validation tests to generate a dummy
     * input file to satisfy cromwell file localization
     */
    public String getBuildDir() { return buildDir; }

    /**
     * Return the name of the freemarker template to be used for the index generated by Barclay.
     * For WDL gen, we create an index file that links to each of the generated WDL files.
     * Must reside in the folder passed to the Barclay Javadc Doclet via the "-settings-dir" parameter.
     * @return name of freemarker index template
     */
    @Override
    public String getIndexTemplateName() {
        return GATK_FREEMARKER_INDEX_TEMPLATE_NAME;
    }

    /**
     * @return Create and return a DocWorkUnit-derived object to handle documentation
     * for the target feature(s) represented by documentedFeature.
     *
     * @param documentedFeature DocumentedFeature annotation for the target feature
     * @param classDoc javadoc classDoc for the target feature
     * @param clazz class of the target feature
     * @return DocWorkUnit to be used for this feature
     */
    @Override
    protected DocWorkUnit createWorkUnit(
            final DocumentedFeature documentedFeature,
            final com.sun.javadoc.ClassDoc classDoc,
            final Class<?> clazz)
    {
        return includeInDocs(documentedFeature, classDoc, clazz) ?
                // for WDL we don't need to customize the work unit, only the handler, so just use the
                // Barclay default WorkUnit class
                new DocWorkUnit(
                    new GATKWDLWorkUnitHandler(this),
                    documentedFeature,
                    classDoc,
                    clazz) :
                null;
    }

    @Override
    protected void processWorkUnitTemplate(
            final Configuration cfg,
            final DocWorkUnit workUnit,
            final List<Map<String, String>> indexByGroupMaps,
            final List<Map<String, String>> featureMaps)
    {
        final String defaultWDLOutputFileName = workUnit.getTargetFileName();
        final String defaultJSONOutputFileName = workUnit.getJSONFileName();

        // generate the default WDL and input JSON, which expose only required args
        exportWorkUnitTemplate(
                cfg,
                workUnit,
                workUnit.getTemplateName(),
                new File(getDestinationDir(), defaultWDLOutputFileName));
        exportWorkUnitTemplate(
                cfg,
                workUnit,
                "wdlJSONTemplate.json.ftl",
                new File(getDestinationDir(), defaultJSONOutputFileName));

        // generate a second pair of files containing ALL arguments
        exportWorkUnitTemplate(
                cfg,
                workUnit,
                "wdlToolTemplateAllArgs.wdl.ftl",
                new File(getDestinationDir(),
                        String.format("%sAllArgs.%s",
                                FilenameUtils.getBaseName(defaultWDLOutputFileName),
                                FilenameUtils.getExtension(defaultWDLOutputFileName)))
        );
        exportWorkUnitTemplate(
                cfg,
                workUnit,
                "wdlJSONTemplateAllArgs.json.ftl",
                new File(getDestinationDir(),
                        String.format("%sAllArgsInputs.json",
                                FilenameUtils.getBaseName(defaultWDLOutputFileName)))
        );

        // Finally, we need to emit a test WDL and JSON pair for use by the cromwell execution test (which
        // runs GATK in command line evaluation only mode). The JSON file is primed with dummy values for any
        // required args. The test WDL specifies no docker image, and has no runtime outputs, since in
        // command line validation mode no outputs are produced, so otherwise cromwell will fail attempting to
        // de-localize them.
        exportWorkUnitTemplate(
                cfg,
                workUnit,
                "wdlToolTemplateAllArgsTest.wdl.ftl",
                new File(getDestinationDir(),
                        String.format("%sAllArgsTest.%s",
                                FilenameUtils.getBaseName(defaultWDLOutputFileName),
                                FilenameUtils.getExtension(defaultWDLOutputFileName)))
        );
        exportWorkUnitTemplate(
                cfg,
                workUnit,
                "wdlJSONTemplateAllArgsTest.json.ftl",
                new File(getDestinationDir(),
                        String.format("%sAllArgsTestInputs.json",
                                FilenameUtils.getBaseName(defaultWDLOutputFileName)))
        );
    }

    /**
     * Export the generated files from templates for a single work unit.
     *
     * @param cfg freemarker config
     * @param workUnit the WorkUnit being processed
     * @param wdlTemplateName name of the template to use
     * @param wdlOutputPath output file
     */
    protected final void exportWorkUnitTemplate(
            final Configuration cfg,
            final DocWorkUnit workUnit,
            final String wdlTemplateName,
            final File wdlOutputPath) {
        try {
            // Merge data-model with wdl template
            final Template wdlTemplate = cfg.getTemplate(wdlTemplateName);
            try (final Writer out = new OutputStreamWriter(new FileOutputStream(wdlOutputPath))) {
                wdlTemplate.process(workUnit.getRootMap(), out);
            }
        } catch (IOException e) {
            throw new DocException("IOException during documentation creation", e);
        } catch (TemplateException e) {
            throw new DocException("TemplateException during documentation creation", e);
        }
    }

    /**
     * Adds a super-category so that we can custom-order the categories in the doc index
     *
     * @param docWorkUnit
     * @return
     */
    @Override
    protected final Map<String, String> getGroupMap(final DocWorkUnit docWorkUnit) {
        final Map<String, String> root = super.getGroupMap(docWorkUnit);

        /**
         * Add-on super-category definitions. The super-category needs to match the string(s) used
         * in the Freemarker template.
         */
        root.put("supercat", HelpConstants.getSuperCategoryProperty(docWorkUnit.getGroupName()));
        return root;
    }

}
