package com.conveyal.analyst.stats;

import com.google.common.collect.Iterators;
import org.mapdb.*;
import org.opentripplanner.analyst.cluster.TaskStatistics;

import java.io.File;
import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.ToIntBiFunction;
import java.util.stream.Collectors;

/**
 * Store statistics in a way that can quickly be queried.
 */
public class StatsStore {
    private final DB db;

    /** the main data store */
    public final BTreeMap<Long, TaskStatistics> data;

    /** next ID to store */
    private final Atomic.Long nextId;

    /** index on OTP commit */
    public final NavigableSet<Fun.Tuple2<String, Long>> commit;

    /** index on instance type */
    public final NavigableSet<Fun.Tuple2<String, Long>> instanceType;

    /** index by graph ID */
    public final NavigableSet<Fun.Tuple2<String, Long>> graphId;

    /** index by job ID */
    public final NavigableSet<Fun.Tuple2<String, Long>> jobId;

    /** index by compute time */
    public final NavigableSet<Fun.Tuple2<Integer, Long>> computeTime;

    /** index by date run (not date of the query but the date the query was run on our infrastructure */
    public final NavigableSet<Fun.Tuple2<Long, Long>> date;

    /** index of all single point jobs (index single point tasks not multipoint as there are fewer single-point) */
    public final NavigableSet<Long> singlePoint;

    /** index of all isochrone requests */
    public final NavigableSet<Long> isochrone;

    /** maximum target count */
    public final Atomic.Integer maxTargetCount;

    /** minimum target count */
    public final Atomic.Integer minTargetCount;

    /** index by target count */
    public final NavigableSet<Fun.Tuple2<Integer, Long>> targetCount;

    /** maximum targets reached */
    public final Atomic.Integer maxTargetsReached;

    /** minimum targets reached */
    public final Atomic.Integer minTargetsReached;

    /** targets reached */
    public final NavigableSet<Fun.Tuple2<Integer, Long>> targetsReached;

    /** maximum initial stops found */
    public final Atomic.Integer maxInitialStopsFound;

    /** minimum initial stops found */
    public final Atomic.Integer minInitialStopsFound;

    /** initial stops found */
    public final NavigableSet<Fun.Tuple2<Integer, Long>> initialStopsFound;

    public StatsStore(File file) {
        db = DBMaker.newFileDB(file)
                .mmapFileEnable()
                .make();

        data = db.createTreeMap("data")
                .keySerializer(BTreeKeySerializer.ZERO_OR_POSITIVE_LONG)
                .makeOrGet();

        nextId = db.getAtomicLong("nextId");

        // indices
        commit = db.createTreeSet("commit").makeOrGet();
        Bind.secondaryKey(data, commit, (k, v) -> v.otpCommit);

        instanceType = db.createTreeSet("instanceType").makeOrGet();
        Bind.secondaryKey(data, instanceType, (k, v) -> v.awsInstanceType);

        graphId = db.createTreeSet("graphId").makeOrGet();
        Bind.secondaryKey(data, graphId, (k, v) -> v.graphId);

        jobId = db.createTreeSet("jobId").makeOrGet();
        Bind.secondaryKey(data, jobId, (k, v) -> v.jobId);

        computeTime = db.createTreeSet("computeTime").makeOrGet();
        Bind.secondaryKey(data, computeTime, (k, v) -> v.compute);

        date = db.createTreeSet("date").makeOrGet();
        Bind.secondaryKey(data, date, (k, v) -> v.computeDate);

        singlePoint = db.createTreeSet("singlePoint")
                .serializer(BTreeKeySerializer.ZERO_OR_POSITIVE_LONG)
                .makeOrGet();
        truth(data, singlePoint, (k, v) -> v.single);

        isochrone = db.createTreeSet("isochrone")
                .serializer(BTreeKeySerializer.ZERO_OR_POSITIVE_LONG)
                .makeOrGet();
        truth(data, isochrone, (k, v) -> v.isochrone);

        targetCount = db.createTreeSet("targetCount").makeOrGet();
        minTargetCount = db.getAtomicInteger("minTargetCount");//, Integer.MAX_VALUE);
        maxTargetCount = db.getAtomicInteger("maxTargetCount");//, Integer.MIN_VALUE);
        integer(data, targetCount, minTargetCount, maxTargetCount, (k, v) -> v.targetCount);

        targetsReached = db.createTreeSet("targetsReached").makeOrGet();
        minTargetsReached = db.getAtomicInteger("minTargetsReached");//, Integer.MAX_VALUE);
        maxTargetsReached = db.getAtomicInteger("maxTargetsReached");//, Integer.MIN_VALUE);
        integer(data, targetsReached, minTargetsReached, maxTargetsReached, (k, v) -> v.targetsReached);

        initialStopsFound = db.createTreeSet("initialStopsFound").makeOrGet();
        minInitialStopsFound = db.getAtomicInteger("minInitialStopsFound");//, Integer.MAX_VALUE);
        maxInitialStopsFound = db.getAtomicInteger("maxInitialStopsFound");//, Integer.MIN_VALUE);
        integer(data, initialStopsFound, minInitialStopsFound, maxInitialStopsFound, (k, v) -> v.initialStopCount);
    }

    /** store task statistics */
    public long store (TaskStatistics ts) {
        // binding handles all secondary indices
        long id = nextId.incrementAndGet();
        data.put(id, ts);

        if (id % 50 == 0)
            db.commit();

        return id;
    }

    /** Intersect the long collections in a somewhat efficient manner */
    public static Collection<Long> intersect (Collection<Set<Long>> input) {
        // sort them by length so we iterate over the smallest first
        List<Set<Long>> sorted = new ArrayList<>(input);
        sorted.sort((s1, s2) -> s1.size() - s2.size());

        List<Long> out = new ArrayList<>();

        INPUT: for (Long l : sorted.get(0)) {
            for (int i = 1; i < sorted.size(); i++) {
                if (!sorted.get(i).contains(l))
                    continue INPUT;
            }

            // if we got here it's in all collections
            out.add(l);
        }

        return out;
    }

    // a few additional bind utils
    /** Create a set with only values for which the function returns true */
    public static <K, V> void truth(BTreeMap<K, V> map, NavigableSet<K> index, BiPredicate<K, V> predicate) {
        // TODO use data pump
        if (index.isEmpty()) {
            map.entrySet().stream().forEach(e -> {
                if (predicate.test(e.getKey(), e.getValue()))
                    index.add(e.getKey());
            });
        }

        map.modificationListenerAdd((k, ov, nv) -> {
            if (ov != null)
                index.remove(k);

            if (nv != null && predicate.test(k, nv))
                index.add(k);
        });
    }

    /** index integers, but keeping track of min/max (although bounds may not be tight if objects are deleted) */
    public static <K, V> void integer(BTreeMap<K, V> map, NavigableSet<Fun.Tuple2<Integer, K>> index,
            Atomic.Integer min, Atomic.Integer max, ToIntBiFunction<K, V> func) {

        map.modificationListenerAdd((k, ov, nv) -> {
            if (ov != null) {
                int oldVal = func.applyAsInt(k, ov);
                index.remove(new Fun.Tuple2(oldVal, k));
            }

            // add if necessary
            if (nv != null) {
                int val = func.applyAsInt(k, nv);
                index.add(new Fun.Tuple2<>(val, k));

                if (val < min.get() || val > max.get()) {
                    synchronized (min) {
                        if (val < min.get()) min.set(val);
                        if (val > max.get()) max.set(val);
                    }
                }
            }
        });
    }

    public static class InvertedSet<E> implements Set<E> {
        private Set<E> wrapped;
        private Map<E, ?> map;

        public InvertedSet(Set<E> wrapped, Map<E, ?> map) {
            this.wrapped = wrapped;
            this.map = map;
        }

        @Override public int size() {
            // assumes all values in wrapped are keys in map
            return map.size() - wrapped.size();
        }

        @Override public boolean isEmpty() {
            return wrapped.isEmpty();
        }

        @Override public boolean contains(Object o) {
            return !wrapped.contains(o);
        }

        @Override public Iterator<E> iterator() {
            return Iterators.filter(map.keySet().iterator(), k -> !wrapped.contains(k));
        }

        @Override public Object[] toArray() {
            return map.keySet().stream()
                    .filter(k -> !wrapped.contains(k))
                    .toArray();
        }

        @Override public <T> T[] toArray(T[] ts) {
            return map.keySet().stream()
                    .filter(k -> !wrapped.contains(k))
                    .collect(Collectors.toList())
                    .toArray(ts);
        }

        @Override public boolean add(E e) {
            return wrapped.remove(e);
        }

        @Override public boolean remove(Object o) {
            return wrapped.add((E) o);
        }

        @Override public boolean containsAll(Collection<?> collection) {
            for (Object item : collection) {
                if (wrapped.contains(item))
                    return false;
            }

            return true;
        }

        @Override public boolean addAll(Collection<? extends E> collection) {
            for (E e : collection) {
                this.add(e);
            }

            return true;
        }

        @Override public boolean retainAll(Collection<?> collection) {
            throw new UnsupportedOperationException();
        }

        @Override public boolean removeAll(Collection<?> collection) {
            for (Object e : collection) {
                this.remove(e);
            }

            return true;
        }

        @Override public void clear() {
            wrapped.addAll(map.keySet());
        }
    }
}
