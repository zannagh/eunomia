// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using System.Diagnostics;
using System.Text.Json;
using Eunomia.Server.Core.Storage;
using Microsoft.Extensions.Logging.Abstractions;
using Xunit.Abstractions;

namespace Eunomia.Server.Tests.Benchmarks;

/// <summary>
/// Not strict assertions of a target number (CI hardware varies too much for that) - these report
/// wall-clock timings for the store's hot paths to the test output, so a regression is visible by
/// eye across runs/PRs. The concurrent stress test additionally asserts correctness under load.
/// </summary>
public class KeyedPacketStoreBenchmarks : IDisposable
{
    private readonly ITestOutputHelper _output;
    private readonly string _dataDir = Path.Combine(Path.GetTempPath(), "eunomia-bench-" + Guid.NewGuid());

    public KeyedPacketStoreBenchmarks(ITestOutputHelper output)
    {
        _output = output;
    }

    public void Dispose()
    {
        if (Directory.Exists(_dataDir))
        {
            Directory.Delete(_dataDir, recursive: true);
        }
    }

    [Fact]
    public void Benchmark_SinglePut_LatencyPercentiles()
    {
        const int iterations = 500;
        KeyedPacketStore store = new(NullLogger<KeyedPacketStore>.Instance, _dataDir);
        JsonElement payload = JsonDocument.Parse("""{"value":"latency"}""").RootElement;
        double[] microsPerOp = new double[iterations];

        for (int i = 0; i < iterations; i++)
        {
            Stopwatch watch = Stopwatch.StartNew();
            store.Put("latency-scope", "eunomia:latency", $"key-{i}", payload);
            watch.Stop();
            microsPerOp[i] = watch.Elapsed.TotalMilliseconds * 1000.0;
        }

        Array.Sort(microsPerOp);
        double p50 = Percentile(microsPerOp, 0.50);
        double p99 = Percentile(microsPerOp, 0.99);

        _output.WriteLine($"[Put latency] n={iterations} p50={p50:F1}us p99={p99:F1}us min={microsPerOp[0]:F1}us max={microsPerOp[^1]:F1}us");
    }

    [Fact]
    public void Benchmark_SnapshotFor_LatencyPercentiles()
    {
        const int entryCount = 500;
        const int iterations = 500;
        KeyedPacketStore store = new(NullLogger<KeyedPacketStore>.Instance, _dataDir);
        JsonElement payload = JsonDocument.Parse("""{"value":"snapshot"}""").RootElement;
        for (int i = 0; i < entryCount; i++)
        {
            store.Put("snapshot-scope", "eunomia:snapshot", $"key-{i}", payload);
        }

        double[] microsPerOp = new double[iterations];
        for (int i = 0; i < iterations; i++)
        {
            Stopwatch watch = Stopwatch.StartNew();
            IReadOnlyList<StoreSyncPayload> snapshot = store.SnapshotFor("snapshot-scope");
            watch.Stop();
            Assert.Single(snapshot);
            microsPerOp[i] = watch.Elapsed.TotalMilliseconds * 1000.0;
        }

        Array.Sort(microsPerOp);
        double p50 = Percentile(microsPerOp, 0.50);
        double p99 = Percentile(microsPerOp, 0.99);

        _output.WriteLine(
            $"[SnapshotFor latency, {entryCount} entries] n={iterations} p50={p50:F1}us p99={p99:F1}us " +
            $"min={microsPerOp[0]:F1}us max={microsPerOp[^1]:F1}us");
    }

    [Fact]
    public void Benchmark_PutThenSnapshot_RoundtripLatency()
    {
        const int iterations = 500;
        KeyedPacketStore store = new(NullLogger<KeyedPacketStore>.Instance, _dataDir);
        JsonElement payload = JsonDocument.Parse("""{"value":"benchmark"}""").RootElement;

        Stopwatch putWatch = Stopwatch.StartNew();
        for (int i = 0; i < iterations; i++)
        {
            store.Put("bench-scope", "eunomia:bench", $"key-{i}", payload);
        }

        putWatch.Stop();

        Stopwatch snapshotWatch = Stopwatch.StartNew();
        IReadOnlyList<StoreSyncPayload> snapshot = store.SnapshotFor("bench-scope");
        snapshotWatch.Stop();

        Assert.Single(snapshot);
        Assert.Equal(iterations, snapshot[0].Entries.Count);

        ReportTimings(
            "Put+snapshot roundtrip",
            iterations,
            putWatch.Elapsed,
            $"snapshot({iterations} entries) took {snapshotWatch.Elapsed.TotalMilliseconds:F3} ms");
    }

    [Fact]
    public void Benchmark_SequentialPut_Throughput()
    {
        const int iterations = 2_000;
        KeyedPacketStore store = new(NullLogger<KeyedPacketStore>.Instance, _dataDir);
        JsonElement payload = JsonDocument.Parse("""{"value":"throughput"}""").RootElement;

        Stopwatch watch = Stopwatch.StartNew();
        for (int i = 0; i < iterations; i++)
        {
            store.Put("throughput-scope", "eunomia:throughput", $"key-{i}", payload);
        }

        watch.Stop();

        ReportTimings("Sequential Put (includes file persistence)", iterations, watch.Elapsed);
    }

    [Fact]
    public async Task Benchmark_ConcurrentPutStress_StaysConsistentUnderLoad()
    {
        const string scope = "stress-scope";
        const string channel = "eunomia:stress";
        const int writerTasks = 32;
        const int putsPerTask = 50;
        const int totalPuts = writerTasks * putsPerTask;

        KeyedPacketStore store = new(NullLogger<KeyedPacketStore>.Instance, _dataDir);

        Stopwatch watch = Stopwatch.StartNew();
        await Task.WhenAll(Enumerable.Range(0, writerTasks).Select(taskIndex => Task.Run(() =>
        {
            for (int i = 0; i < putsPerTask; i++)
            {
                string key = $"key-{taskIndex}-{i}";
                JsonElement payload = JsonDocument.Parse($$"""{"task":{{taskIndex}},"i":{{i}}}""").RootElement;
                store.Put(scope, channel, key, payload);
            }
        })));
        watch.Stop();

        // Consistency check: a fresh instance reading the file back must see every write, proving
        // the per-(scope,channel) lock + atomic temp-file-then-rename swap held under contention.
        KeyedPacketStore reloaded = new(NullLogger<KeyedPacketStore>.Instance, _dataDir);
        StoreSyncPayload snapshot = Assert.Single(reloaded.SnapshotFor(scope));
        Assert.Equal(totalPuts, snapshot.Entries.Count);

        ReportTimings($"Concurrent Put ({writerTasks} tasks x {putsPerTask})", totalPuts, watch.Elapsed);
    }

    private void ReportTimings(string label, int operationCount, TimeSpan elapsed, string? extra = null)
    {
        double opsPerSecond = operationCount / Math.Max(elapsed.TotalSeconds, 0.000001);
        double avgMicros = elapsed.TotalMilliseconds * 1000.0 / operationCount;

        _output.WriteLine(
            $"[{label}] {operationCount} ops in {elapsed.TotalMilliseconds:F3} ms " +
            $"({opsPerSecond:F0} ops/s, {avgMicros:F1} us/op avg)");

        if (extra is not null)
        {
            _output.WriteLine($"  {extra}");
        }
    }

    /// <summary>Nearest-rank percentile over an already-sorted sample.</summary>
    private static double Percentile(double[] sortedSample, double percentile)
    {
        int rank = (int)Math.Ceiling(percentile * sortedSample.Length) - 1;
        return sortedSample[Math.Clamp(rank, 0, sortedSample.Length - 1)];
    }
}
