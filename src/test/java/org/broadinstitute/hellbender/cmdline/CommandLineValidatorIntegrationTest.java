package org.broadinstitute.hellbender.cmdline;

import org.broadinstitute.barclay.argparser.CommandLineException;
import org.broadinstitute.hellbender.CommandLineValidatorMain;
import org.broadinstitute.hellbender.GATKBaseTest;
import org.testng.annotations.Test;

public class CommandLineValidatorIntegrationTest extends GATKBaseTest {

    @Test
    public void testValidatorPositive() {
        CommandLineValidatorMain.main(new String[] {
                "PrintReads",
                "-I",
                "filesDontNeedToExistForCommandLineValidation.bam",
                "-O",
                "filesDontNeedToExistForCommandLineValidation.bam"
        });
    }

    @Test(expectedExceptions = CommandLineException.class)
    public void testValidatorNegative() {
        // force a command line parsing exception
        CommandLineValidatorMain.main(new String[] {
                "PrintReads",
                "-unrecognizedOption"
        });
    }

}
