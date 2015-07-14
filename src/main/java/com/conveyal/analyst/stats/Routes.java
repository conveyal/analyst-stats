package com.conveyal.analyst.stats;

import com.csvreader.CsvWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.opentripplanner.analyst.cluster.TaskStatistics;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.util.Collection;

import static spark.Spark.*;

/**
 * Routes for the stats collector.
 */
public class Routes {
    private static final ObjectMapper mapper = new ObjectMapper();

    public static void routes () {
        // serve the web app
        staticFileLocation("/public");

        // and the API
        get("/stats", StatsController::stats, mapper::writeValueAsString);
        after("/stats", (q, s) -> s.type("application/json"));

        get("/csv", StatsController::stats, ts -> toCsv((Collection<TaskStatistics>) ts));
        after("/csv", (q, s) -> {
            s.type("text/csv");
            s.header("Content-Disposition", "attachment;filename=jobs.csv");
        });

        get("/values", StatsController::values, mapper::writeValueAsString);
        after("/values", (q, s) -> s.type("application/json"));
    }

    public static <T> String toCsv(Collection<T> coll) {
        if (coll.isEmpty())
            return "";

        Class clazz = coll.iterator().next().getClass();
        Field[] fields = clazz.getFields();

        // create a CSV
        // TODO don't buffer entire CSV in memory
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        CsvWriter w = new CsvWriter(baos, ',', Charset.forName("UTF-8"));

        try {
            for (Field f : fields) {
                w.write(f.getName());
            }
            w.endRecord();

            for (T t : coll) {
                for (Field f : fields) {
                    Object val = f.get(t);
                    w.write(val != null ? val.toString() : "");
                }
                w.endRecord();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            w.close();
        }

        return baos.toString();
    }
}
