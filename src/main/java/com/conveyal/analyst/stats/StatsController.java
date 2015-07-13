package com.conveyal.analyst.stats;

import org.mapdb.Fun;
import org.opentripplanner.analyst.cluster.TaskStatistics;
import spark.Request;
import spark.Response;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Handles the work of filtering and serving stats.
 */
public class StatsController {
    /** get task statistics */
    public static Collection<TaskStatistics> stats (Request req, Response res) {
        // implement lots of filters
        ArrayList<Set<Long>> filters = new ArrayList<>();

        Map<String, String[]> qpm = req.queryMap().toMap();
        if (qpm.containsKey("commit")) {
            String commit = qpm.get("commit")[0];

            // java loses the type information from subset (probably due to the raw types) so we
            // add an explicit cast
            filters.add(((Set<Fun.Tuple2<String, Long>>) StatsMain.store.commit
                            .subSet(new Fun.Tuple2(commit, null), new Fun.Tuple2(commit, Fun.HI)))
                            .stream().map(t -> t.b).collect(Collectors.toSet()));
        }

        // instance type
        if (qpm.containsKey("instanceType")) {
            String instanceType = qpm.get("instanceType")[0];

            filters.add(((Set<Fun.Tuple2<String, Long>>) StatsMain.store.instanceType
                            .subSet(new Fun.Tuple2(instanceType, null),
                                    new Fun.Tuple2(instanceType, Fun.HI)))
                            .stream().map(t -> t.b)
                            .collect(Collectors.toSet()));
        }

        // graph ID
        if (qpm.containsKey("graphId")) {
            String graphId = qpm.get("graphId")[0];

            filters.add(((Set<Fun.Tuple2<String, Long>>) StatsMain.store.graphId
                            .subSet(new Fun.Tuple2(graphId, null), new Fun.Tuple2(graphId, Fun.HI)))
                            .stream()
                            .map(t -> t.b).collect(Collectors.toSet())
            );
        }

        // job ID
        if (qpm.containsKey("jobId")) {
            String jobId = qpm.get("jobId")[0];

            filters.add(((Set<Fun.Tuple2<String, Long>>) StatsMain.store.jobId
                            .subSet(new Fun.Tuple2(jobId, null), new Fun.Tuple2(jobId, Fun.HI)))
                            .stream()
                            .map(t -> t.b).collect(Collectors.toSet())
            );
        }

        // single point
        if (qpm.containsKey("single")) {
            Boolean singlePoint = Boolean.parseBoolean(qpm.get("single")[0]);

            // we want single point requests: we already have a map for that
            if (singlePoint)
                filters.add(StatsMain.store.singlePoint);

            // invert the set
            else
                filters.add(new StatsStore.InvertedSet<>(StatsMain.store.singlePoint, StatsMain.store.data));
        }

        // isochrone
        if (qpm.containsKey("isochrone")) {
            Boolean isochrone = Boolean.parseBoolean(qpm.get("isochrone")[0]);

            // we want isochrone requests: we already have a map for that
            if (isochrone)
                filters.add(StatsMain.store.isochrone);

            // invert the set
            else
                filters.add(new StatsStore.InvertedSet<>(StatsMain.store.isochrone, StatsMain.store.data));
        }

        if (filters.isEmpty())
            return StatsMain.store.data.values();

        Collection<Long> ret = StatsStore.intersect(filters);

        return ret.stream().map(id -> StatsMain.store.data.get(id))
                .collect(Collectors.toList());
    }

    /** get all the unique values for the filters */
    public static Object values (Request req, Response res) {
        Values ret = new Values();
        ret.commit = StatsMain.store.commit.stream()
                .map(c -> c.a)
                .collect(Collectors.toSet());

        ret.instanceType = StatsMain.store.instanceType.stream()
                .map(c -> c.a)
                .collect(Collectors.toSet());

        ret.graphId = StatsMain.store.graphId.stream()
                .map(c -> c.a)
                .collect(Collectors.toSet());

        ret.jobId = StatsMain.store.jobId.stream()
                .map(c -> c.a)
                .collect(Collectors.toSet());

        return ret;
    }

    public static class Values {
        public Set<String> commit;
        public Set<String> instanceType;
        public Set<String> graphId;
        public Set<String> jobId;
    }
}
