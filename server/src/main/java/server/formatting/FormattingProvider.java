package server.formatting;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.lsp4j.DocumentFormattingParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;

import server.buildifier.Buildifier;
import server.buildifier.BuildifierException;
import server.buildifier.BuildifierFileType;
import server.buildifier.FormatInput;
import server.buildifier.FormatOutput;
import server.utils.DocumentTracker;

public class FormattingProvider {

    private static final Logger logger = LogManager.getLogger(FormattingProvider.class);


    public static CompletableFuture<List<? extends TextEdit>> getDocumentFormatting(DocumentFormattingParams params) {
        logger.info("Invoked FormattingProvider");

        DocumentTracker documentTracker = DocumentTracker.getInstance();
        FormatInput formatInput = new FormatInput();
        String stringUri = params.getTextDocument().getUri();
        logger.info("Formatting file from: " + stringUri);
        File file = getFileFromUriString(stringUri);

        if (file == null) {
            // TODO: handle this problem
            logger.info("Could not find file");
            return CompletableFuture.completedFuture(new ArrayList<TextEdit>());
        }

        String name = file.getName();
        logger.info("Formatting file: " + name);

        if (name.equals("BUILD")) {
            formatInput.setType(BuildifierFileType.BUILD);
        } else if (name.equals("WORKSPACE")) {
            formatInput.setType(BuildifierFileType.WORKSAPCE);
        } else {
            // This assumes that this code will not be called on any incompatible file types
            formatInput.setType(BuildifierFileType.BZL);
        }

        String content = documentTracker.getContents(file.toURI());
        formatInput.setContent(content);
        formatInput.setShouldApplyLintFixes(true);

        Buildifier buildifier = getBuildifier();
        FormatOutput formatOutput = null;
        try {
            formatOutput = buildifier.format(formatInput);
        } catch(BuildifierException exception) {
            logger.error(exception);
            return CompletableFuture.completedFuture(new ArrayList<TextEdit>());
        }
        logger.info("File formatted");

        TextEdit result = makeTextEditFromChanges(content, formatOutput.getResult());
        List<TextEdit> results = new ArrayList<>();
        results.add(result);


        return CompletableFuture.completedFuture(results);
    }

    static File getFileFromUriString(String uriString) {
        try {
            URI uri = new URI(uriString);
            File file = new File(uri);
            return file;
        } catch (URISyntaxException e) {
            logger.error(e);
            return null;
        }
    }

    private static TextEdit makeTextEditFromChanges(String oldContents, String newContents) {
        Position start = new Position(0, 0);
        Position end = getLastPosition(oldContents);

        Range wholeDocument = new Range(start, end);

        return new TextEdit(wholeDocument, newContents);

    }

    private static Position getLastPosition(String contents) {
        // Is this safe? Only in a Linux environment?
        String[] lines = contents.split("\n");
        int linePosition = lines.length -1;
        int charPosition = lines[linePosition].length();

        return new Position(linePosition, charPosition);
    }

    static Buildifier getBuildifier() {
        return new Buildifier();
    }
}