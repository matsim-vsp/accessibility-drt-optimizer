package org.matsim.accessibillityDrtOptimizer.analysis;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.api.core.v01.population.PopulationWriter;
import org.matsim.application.MATSimAppCommand;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.PopulationUtils;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ExtractCoreRequests implements MATSimAppCommand {
    @CommandLine.Option(names = "--plans", description = "input plans", required = true)
    private String inputPlansFile;

    @CommandLine.Option(names = "--drt-trips", description = "served drt trip files", required = true)
    private Path drtTrips;

    @CommandLine.Option(names = "--output", description = "output plans", required = true)
    private String outputPlansFile;

    public static void main(String[] args) {
        new ExtractCoreRequests().execute(args);
    }

    @Override
    public Integer call() throws Exception {
        Population inputPlan = PopulationUtils.readPopulation(inputPlansFile);
        Population outputPlans = PopulationUtils.createPopulation(ConfigUtils.createConfig());

        List<Id<Person>> servedPerson = new ArrayList<>();

        try (CSVParser parser = new CSVParser(Files.newBufferedReader(drtTrips),
                CSVFormat.DEFAULT.withDelimiter(';').withFirstRecordAsHeader())) {
            for (CSVRecord record : parser.getRecords()) {
                servedPerson.add(Id.createPersonId(record.get("personId")));
            }
        }

        for (Person person : inputPlan.getPersons().values()) {
            if (servedPerson.contains(person.getId())) {
                outputPlans.addPerson(person);
            }
        }

        new PopulationWriter(outputPlans).write(outputPlansFile);

        return 0;
    }
}
