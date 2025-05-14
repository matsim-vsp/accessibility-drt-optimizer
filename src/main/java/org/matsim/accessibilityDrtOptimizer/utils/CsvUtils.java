package org.matsim.accessibilityDrtOptimizer.utils;

import org.apache.commons.lang.StringUtils;
import org.matsim.core.utils.io.IOUtils;

import java.io.BufferedReader;
import java.io.IOException;

/**
 * This can be deleted after switching to a newer MATSim version.
 */
public class CsvUtils {
    /**
     * Detects possibly used delimiter from the header of a csv or tsv file.
     */
    public static Character detectDelimiter(String path) throws IOException {
        try (BufferedReader reader = IOUtils.getBufferedReader(path)) {
            int[] comma = new int[5];
            int[] semicolon = new int[5];
            int[] tab = new int[5];
            String[] lines = new String[5];

//			check five first lines for separator chars. It might be that the csv file has additional info in the first x lines (e.g. EPSG)
            for (int i = 0; i < 5; i++) {
                lines[i] = reader.readLine();
                if (lines[i] == null) {
                    comma[i] = 0;
                    semicolon[i] = 0;
                    tab[i] = 0;
                } else {
                    comma[i] = StringUtils.countMatches(lines[i], ",");
                    semicolon[i] = StringUtils.countMatches(lines[i], ";");
                    tab[i] = StringUtils.countMatches(lines[i], "\t");
                }
            }

            Integer index = null;

            for (int i = 0; i < comma.length - 1; i++) {
//				only check next index if line with separators was not found
                if (index == null) {
                    if (!(comma[i] == 0 && semicolon[i] == 0 && tab[i] == 0)) {
                        index = i;
                    }
                }
            }

            if (index == null) {
                throw new IllegalArgumentException("No delimiter found in the first line of the file.");
            } else {
                // Comma is preferred as the more likely format
                if (comma[index] >= semicolon[index] && comma[index] >= tab[index]) {
                    return ',';
                } else if (tab[index] >= semicolon[index])
                    return '\t';
                else
                    return ';';
            }
        }
    }
}
