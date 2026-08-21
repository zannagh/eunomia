// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using System.Threading.Channels;
using Eunomia.Server.Core.Servers;
using Eunomia.Server.Data.Entities;
using Microsoft.EntityFrameworkCore;
using Serilog.Core;
using Serilog.Debugging;
using Serilog.Events;

namespace Eunomia.Server.Data.Logging;

/// <summary>
/// Serilog sink that persists a <see cref="ServerLogEntry"/> for each log event carrying the
/// <see cref="ServerScope.PropertyName"/> property, and silently ignores every other event (that is the
/// per-server filter). Writes are handed to a bounded background channel so logging never blocks the
/// request path, drops on backpressure rather than deadlocking, and can never crash the app: all write
/// failures are swallowed to <see cref="SelfLog"/>.
/// </summary>
public sealed class ServerLogSink : ILogEventSink, IDisposable
{
    private const int QueueCapacity = 10_000;
    private const int BatchSize = 100;

    private readonly IDbContextFactory<EunomiaDbContext> _contextFactory;
    private readonly Channel<ServerLogEntry> _queue;
    private readonly Task _consumer;
    private int _disposed;

    public ServerLogSink(IDbContextFactory<EunomiaDbContext> contextFactory)
    {
        _contextFactory = contextFactory;
        _queue = Channel.CreateBounded<ServerLogEntry>(new BoundedChannelOptions(QueueCapacity)
        {
            FullMode = BoundedChannelFullMode.DropWrite,
            SingleReader = true,
        });
        _consumer = Task.Run(ConsumeAsync);
    }

    public void Emit(LogEvent logEvent)
    {
        if (!TryReadScope(logEvent, out string scope))
        {
            return;
        }

        ServerLogEntry entry = new()
        {
            Scope = scope,
            Timestamp = logEvent.Timestamp.UtcDateTime,
            Level = logEvent.Level.ToString(),
            Message = logEvent.RenderMessage(),
            Exception = logEvent.Exception?.ToString(),
        };

        // Non-blocking: on backpressure the event is dropped rather than stalling the caller.
        _queue.Writer.TryWrite(entry);
    }

    public void Dispose()
    {
        if (Interlocked.Exchange(ref _disposed, 1) != 0)
        {
            return;
        }

        _queue.Writer.TryComplete();
        try
        {
            _consumer.Wait(TimeSpan.FromSeconds(5));
        }
        catch (Exception ex)
        {
            SelfLog.WriteLine("ServerLogSink drain on dispose failed: {0}", ex);
        }
    }

    private static bool TryReadScope(LogEvent logEvent, out string scope)
    {
        scope = string.Empty;
        if (!logEvent.Properties.TryGetValue(ServerScope.PropertyName, out LogEventPropertyValue? value)
            || value is not ScalarValue { Value: string raw }
            || string.IsNullOrEmpty(raw))
        {
            return false;
        }

        scope = raw;
        return true;
    }

    private async Task ConsumeAsync()
    {
        ChannelReader<ServerLogEntry> reader = _queue.Reader;
        while (await reader.WaitToReadAsync())
        {
            List<ServerLogEntry> batch = DrainBatch(reader);
            if (batch.Count == 0)
            {
                continue;
            }

            await WriteBatchAsync(batch);
        }
    }

    private static List<ServerLogEntry> DrainBatch(ChannelReader<ServerLogEntry> reader)
    {
        List<ServerLogEntry> batch = new(BatchSize);
        while (batch.Count < BatchSize && reader.TryRead(out ServerLogEntry? entry))
        {
            batch.Add(entry);
        }

        return batch;
    }

    private async Task WriteBatchAsync(List<ServerLogEntry> batch)
    {
        try
        {
            await using EunomiaDbContext context = await _contextFactory.CreateDbContextAsync();
            context.ServerLogs.AddRange(batch);
            await context.SaveChangesAsync();
        }
        catch (Exception ex)
        {
            // A logging sink must never take the app down; drop the batch and record to SelfLog.
            SelfLog.WriteLine("ServerLogSink failed to persist {0} entries: {1}", batch.Count, ex);
        }
    }
}
