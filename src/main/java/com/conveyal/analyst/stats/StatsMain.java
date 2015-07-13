package com.conveyal.analyst.stats;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;

import static spark.Spark.*;

/**
 * Main entry point for the stats server.
 */
public class StatsMain {
    private static final Logger LOG = LoggerFactory.getLogger(StatsMain.class);

    public static final Properties config = new Properties();

    static StatsStore store;

    public static void main (String... args) throws Exception {
            LOG.info("Welcome to Transport Analyst Statistics Collector by conveyal");
            LOG.info("Reading properties . . .");
            // TODO don't hardwire
            FileInputStream in = new FileInputStream(new File("application.conf"));
            config.load(in);
            in.close();

            LOG.info("Initializing datastore . . .");
            store = new StatsStore(new File(config.getProperty("application.database")));

            LOG.info("Listening for statistics");
            new Thread(new StatsListener()).start();

            // figure out host and port
            LOG.info("Starting server");
            int portNo = Integer.parseInt(config.getProperty("application.port", "9090"));
            String ip = config.getProperty("application.ip");
            if (ip != null) ipAddress(ip);

            port(portNo);

            // set routes
            Routes.routes();

            LOG.info("Server started.");
    }
}
