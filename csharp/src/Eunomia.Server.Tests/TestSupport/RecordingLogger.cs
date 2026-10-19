// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using Microsoft.Extensions.Logging;

namespace Eunomia.Server.Tests.TestSupport;

/// <summary>
/// An <see cref="ILogger"/> test double that records every entry with its level, rendered message and structured
/// properties, so tests can assert on what was logged and on the values handed to the message template.
/// </summary>
public sealed class RecordingLogger : ILogger
{
    public List<RecordedLogEntry> Entries { get; } = [];

    public IDisposable? BeginScope<TState>(TState state)
        where TState : notnull
    {
        return null;
    }

    public bool IsEnabled(LogLevel logLevel)
    {
        return true;
    }

    public void Log<TState>(
        LogLevel logLevel,
        EventId eventId,
        TState state,
        Exception? exception,
        Func<TState, Exception?, string> formatter)
    {
        Dictionary<string, object?> properties = state is IEnumerable<KeyValuePair<string, object?>> pairs
            ? pairs.ToDictionary(pair => pair.Key, pair => pair.Value)
            : [];
        Entries.Add(new RecordedLogEntry(logLevel, formatter(state, exception), properties));
    }
}
