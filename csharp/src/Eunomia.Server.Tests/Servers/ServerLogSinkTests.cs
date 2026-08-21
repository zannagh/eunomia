// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using Eunomia.Server.Data;
using Eunomia.Server.Data.Entities;
using Eunomia.Server.Data.Logging;
using Eunomia.Server.Tests.TestSupport;
using Serilog.Events;
using Serilog.Parsing;

namespace Eunomia.Server.Tests.Servers;

/// <summary>
/// Verifies the per-server sink is the filter: it persists a <see cref="ServerLogEntry"/> for events
/// carrying the <c>ServerScope</c> property and silently drops every other event. Disposing the sink
/// drains the background queue, so persistence can be asserted deterministically afterwards.
/// </summary>
public class ServerLogSinkTests : IDisposable
{
    private const string Scope = "mc.logsink-tests:25565";

    private readonly SqliteDbContextFactory _factory = new();

    public void Dispose()
    {
        _factory.Dispose();
    }

    [Fact]
    public void Emit_WritesOnlyEventsThatCarryServerScope()
    {
        ServerLogSink sink = new(_factory);

        sink.Emit(EventWithScope(Scope, LogEventLevel.Warning, "server said hi"));
        sink.Emit(EventWithoutScope());
        sink.Emit(EventWithScope(Scope, LogEventLevel.Error, "server tripped"));

        // Draining happens on dispose; after this all accepted events are persisted.
        sink.Dispose();

        using EunomiaDbContext context = _factory.CreateDbContext();
        List<ServerLogEntry> rows = context.ServerLogs.OrderBy(l => l.Message).ToList();
        Assert.Equal(2, rows.Count);
        Assert.All(rows, r => Assert.Equal(Scope, r.Scope));
        Assert.Contains(rows, r => r.Level == "Warning" && r.Message.Contains("hi"));
        Assert.Contains(rows, r => r.Level == "Error" && r.Message.Contains("tripped"));
    }

    [Fact]
    public void Emit_EventWithoutServerScope_PersistsNothing()
    {
        ServerLogSink sink = new(_factory);

        sink.Emit(EventWithoutScope());
        sink.Dispose();

        using EunomiaDbContext context = _factory.CreateDbContext();
        Assert.Empty(context.ServerLogs);
    }

    private static LogEvent EventWithScope(string scope, LogEventLevel level, string message)
    {
        return NewEvent(level, message, new LogEventProperty("ServerScope", new ScalarValue(scope)));
    }

    private static LogEvent EventWithoutScope()
    {
        return NewEvent(LogEventLevel.Information, "unrelated app log");
    }

    private static LogEvent NewEvent(LogEventLevel level, string message, params LogEventProperty[] properties)
    {
        MessageTemplate template = new MessageTemplateParser().Parse(message);
        return new LogEvent(DateTimeOffset.UtcNow, level, exception: null, template, properties);
    }
}
