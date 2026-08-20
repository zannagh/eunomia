// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using System.Diagnostics;
using Eunomia.Server.Core.Clients;
using Eunomia.Server.Core.Communication;
using Xunit.Abstractions;

namespace Eunomia.Server.Tests.Benchmarks;

/// <summary>
/// Times the relay fan-out path (<see cref="ConnectionManager.BroadcastToScopeAsync"/>) without a
/// real network: clients here have a null <see cref="EunomiaClient.Socket"/>, so
/// <see cref="EunomiaClient.SendAsync"/> is a no-op and this measures the manager's own
/// dictionary-iteration/dispatch overhead, not wire transmission time. Reported to test output so
/// a regression in the fan-out path itself is visible across runs.
/// </summary>
public class ConnectionManagerBenchmarks
{
    private readonly ITestOutputHelper _output;

    public ConnectionManagerBenchmarks(ITestOutputHelper output)
    {
        _output = output;
    }

    [Fact]
    public async Task Benchmark_BroadcastToScope_FanOutOverhead()
    {
        const string scope = "bench-scope";
        const int clientCount = 200;
        const int broadcastCount = 100;

        ConnectionManager manager = new();
        for (int i = 0; i < clientCount; i++)
        {
            manager.OnConnectionAdded(new EunomiaClient(Guid.NewGuid()) { Scope = scope });
        }

        Stopwatch watch = Stopwatch.StartNew();
        for (int i = 0; i < broadcastCount; i++)
        {
            await manager.BroadcastToScopeAsync(scope, $"{{\"type\":\"envelope\",\"data\":{{\"i\":{i}}}}}");
        }

        watch.Stop();

        long totalSends = (long)clientCount * broadcastCount;
        double sendsPerSecond = totalSends / Math.Max(watch.Elapsed.TotalSeconds, 0.000001);

        _output.WriteLine(
            $"[Broadcast fan-out] {broadcastCount} broadcasts x {clientCount} clients " +
            $"= {totalSends} dispatches in {watch.Elapsed.TotalMilliseconds:F3} ms ({sendsPerSecond:F0} dispatches/s)");
    }
}
