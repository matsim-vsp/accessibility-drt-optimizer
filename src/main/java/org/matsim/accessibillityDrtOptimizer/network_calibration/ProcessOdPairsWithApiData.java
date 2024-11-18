package org.matsim.accessibillityDrtOptimizer.network_calibration;

import org.matsim.application.MATSimAppCommand;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class ProcessOdPairsWithApiData implements MATSimAppCommand {

    @CommandLine.Option(names = "--input", description = "path to input od pairs with api data", required = true)
    private String input;

    @CommandLine.Option(names = "--output", description = "output folder of the processed data", required = true)
    private String output;

    @CommandLine.Option(names = "--num-sub-od-pairs", description = "number of od pairs to keep", arity = "1..*",
            defaultValue = "19000")
    private List<Integer> numbersOfOdPairs;

    public static void main(String[] args) {
        new ProcessOdPairsWithApiData().execute(args);
    }

    @Override
    public Integer call() throws Exception {
        Random random = new Random(1);
        List<String> lines = Files.readAllLines(Paths.get(input));
        // Optional: if the first line is a header, remove it, save it, and add it back after shuffling.
        String header = lines.remove(0);
        // Shuffle the lines
        Collections.shuffle(lines, random);
        // Add header back if it was removed
        lines.add(0, header);

        // write out top x OD pairs
        for (int numberOfOdPairs : numbersOfOdPairs) {
            String outputFilePath = output + "/training-data-" + numberOfOdPairs + ".csv";
            // The first row is header
            Files.write(Paths.get(outputFilePath), lines.subList(0, numberOfOdPairs + 1));
        }

        // write out the last 1000 OD pairs as the validation data
        String outputValidationFilePath = output + "/validation-data.csv";
        List<String> validationLines = new ArrayList<>();
        validationLines.add(lines.get(0));
        validationLines.addAll(lines.subList(19001, 20001));
        Files.write(Paths.get(outputValidationFilePath), validationLines);

        return 0;
    }
}
