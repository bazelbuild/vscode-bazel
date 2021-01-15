package server.buildifier;

import com.google.common.base.Preconditions;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import server.utils.FileRepository;
import server.workspace.ExtensionConfig;
import server.workspace.Workspace;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * A wrapper around the buildifier CLI. This allows callers to invoke buildifier commands which
 * format documents, check linting, etc.
 */
public final class Buildifier {
    public static final String DEFAULT_BUILDIFIER_NAME = "buildifier";
    private static final Executor FALLBACK_EXECUTOR = new ExecutorFallback();
    private static final Logger logger = LogManager.getLogger(Buildifier.class);

    private FileRepository fileRepository;
    private Executor executor;

    public Buildifier() {
        fileRepository = null;
        executor = null;
    }

    FileRepository getFileRepository() {
        return fileRepository;
    }

    void setFileRepository(FileRepository fileRepository) {
        this.fileRepository = fileRepository;
    }

    Executor getExecutor() {
        return executor;
    }

    void setExecutor(Executor executor) {
        this.executor = executor;
    }

    private FileRepository getEffectiveFileRepository() {
        return getFileRepository() == null ? FileRepository.getDefault() : getFileRepository();
    }

    private Executor getEffectiveExecutor() {
        return getExecutor() == null ? FALLBACK_EXECUTOR : getExecutor();
    }

    /**
     * Checks to see if the buildififer executable exists.
     *
     * @return Whether the buildififer executable exists.
     */
    public boolean exists() {
        try {
            locateExecutable();
            return true;
        } catch (BuildifierNotFoundException e) {
            return false;
        }
    }

    /**
     * Invokes the buildifier in format mode.
     *
     * @param args The arguments to run the buildifier with.
     * @return The formatted content.
     * @throws BuildifierException If the buildifier fails to execute.
     */
    public String format(final BuildifierFormatArgs args) throws BuildifierException {
        Preconditions.checkNotNull(args);
        Preconditions.checkNotNull(args.getContent());
        Preconditions.checkNotNull(args.getType());

        final ExecutorInput input = new ExecutorInput();

        // Build the command line args.
        final List<String> cmdArgs = new ArrayList<>();
        {
            cmdArgs.add("--mode=fix");
            cmdArgs.add(String.format("--type=%s", args.getType().toCLI()));

            if (args.getShouldApplyLintFixes()) {
                cmdArgs.add("--lint=fix");
            }
        }

        logger.info(String.format("Formatting content \"%s\".", args.getContent()));

        // Run the buildifier.
        input.setContent(args.getContent());
        input.setArgs(cmdArgs.toArray(new String[0]));
        input.setExecutable(locateExecutable());
        final ExecutorOutput output = getEffectiveExecutor().run(input);

        // Return the successfully formated contents if the exit code
        // indicated a valid format result.
        if (output.getExitCode() == 0) {
            logger.info(String.format(
                    "Successfully formatted content: \"%s\"",
                    output.getRawOutput()));
            return output.getRawOutput();
        }

        // TODO: Handle errors more appropriately with more information.
        logger.warn(String.format(
                "Failed to format with exit code %d. Error output is \"%s\"",
                output.getExitCode(),
                output.getRawError()));

        throw new BuildifierException();
    }

    /**
     * Locates the path to the buildifier binary. The buildifier specified on the extension
     * configuration will be favored first. If that doesn't exist, this method will search
     * the system path for the buildifier. The returned path is guaranteed to be executable.
     *
     * @return The path to the buildifier binary.
     * @throws BuildifierNotFoundException If the buildifer couldn't be found.
     */
    private Path locateExecutable() throws BuildifierNotFoundException {
        Preconditions.checkNotNull(Workspace.getInstance());
        Preconditions.checkNotNull(getEffectiveFileRepository());

        final ExtensionConfig config = Workspace.getInstance().getExtensionConfig();
        logger.info("Locating buildifier.");

        // The extension config path will take priority over the inferred paths. Try
        // to load the buildifier from the extension configuration.
        {
            final Path path = getFileRepository().getFileSystem().getPath(
                    config.getBazel().getBuildifier().getExecutable()
            );

            if (Files.exists(path, LinkOption.NOFOLLOW_LINKS) && path.toFile().canExecute()) {
                logger.info("Buildifer was located from the configuration settings.");
                return path;
            }
        }

        // Try to find the buildifier in the system PATH.
        {
            final Path path = getEffectiveFileRepository().searchPATH(DEFAULT_BUILDIFIER_NAME);
            if (path != null && Files.exists(path, LinkOption.NOFOLLOW_LINKS) && path.toFile().canExecute()) {
                logger.info("Buildifier was located from the system PATH.");
                return path;
            }
        }

        logger.info("Buildifier not found.");
        throw new BuildifierNotFoundException();
    }
}
