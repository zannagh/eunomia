// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using Microsoft.Extensions.Logging;

namespace Eunomia.Server.Tests.TestSupport;

/// <summary>One entry captured by <see cref="RecordingLogger"/>.</summary>
public sealed record RecordedLogEntry(LogLevel Level, string Message, IReadOnlyDictionary<string, object?> Properties);
