package org.matsim.accessibilityDrtOptimizer.utils;

import org.matsim.application.MATSimAppCommand;
import picocli.CommandLine;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class RemoveEventsFiles implements MATSimAppCommand {
    @CommandLine.Option(names = "--output", description = "output root directory", required = true)
    private String outputRootDirectory;

    @CommandLine.Option(names = "--iterations", description = "outer iterations", defaultValue = "200")
    private int outerIterations;

    public static void main(String[] args) {
        new RemoveEventsFiles().execute(args);
    }

    @Override
    public Integer call() throws Exception {
        File outputRootDirectoryFile = new File(outputRootDirectory);
        List<String> filesToRemove = new ArrayList<>();
        for (File fleetFolder : Objects.requireNonNull(outputRootDirectoryFile.listFiles())) {
            if (fleetFolder.isDirectory()) {
                for (int i = 0; i < outerIterations; i++) {
                    String iterFolderString = fleetFolder.getAbsolutePath() + "/iter-" + i;
//                    File iterFolderFile = new File(iterFolderString);
//                    for (File file : Objects.requireNonNull(iterFolderFile.listFiles())) {
//                        if (file.isFile() && file.getName().endsWith("events.xml.gz")) {
//                            filesToRemove.add(file.getAbsolutePath());
//                        }
//                    }

                    String innerIterFolder = iterFolderString + "/ITERS";
                    for (int j = 0; j < 3; j++) {
                        File folder = new File(innerIterFolder + "/it." + j);
                        for (File file : Objects.requireNonNull(folder.listFiles())) {
                            if (file.getName().endsWith("events.xml.gz")) {
                                filesToRemove.add(file.getAbsolutePath());
                            }
                        }
                    }
                }
            }
        }

        for (String fileToDelete : filesToRemove) {
            Files.delete(Path.of(fileToDelete));
        }

        return 0;
    }
}
