package org.broadinstitute.hellbender.cmdline;

import org.broadinstitute.hellbender.exceptions.GATKException;

/**
 * Adapter shim for use within GATK to run tools in command line validation mode. Note that this
 * class does not have it's own CommandLineProgramProperties annotation, and isn't intended to be
 * run directly from the command line.
 */
public class CommandLineProgramValidator extends CommandLineProgram {

    // Our target command line program, to which we forward subsequent calls.
    final private CommandLineProgram targetCommandLineProgram;

    public CommandLineProgramValidator(final CommandLineProgram targetCommandLineProgram) {
        this.targetCommandLineProgram = targetCommandLineProgram;
    }

    /**
     * Entry point to run command line validation only.
     */
    @Override
    public Object instanceMain(final String[] argv) {
        // just call parseArgs and then return
        return targetCommandLineProgram.parseArgs(argv);
    }

    @Override
    protected Object doWork() {
        // This method should never be called directly. Call instanceMain instead.
        throw new GATKException.ShouldNeverReachHereException(
                String.format("Attempt to call the doWork method on the validator test tool \"%s\" directly.",
                        targetCommandLineProgram.getClass().getName()));
    }
}
