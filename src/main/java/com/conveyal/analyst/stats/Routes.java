package com.conveyal.analyst.stats;

import com.fasterxml.jackson.databind.ObjectMapper;

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
        get("/values", StatsController::values, mapper::writeValueAsString);
        after((q, s) -> s.type("application/json"));
    }
}
