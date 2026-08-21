// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using Eunomia.Server.Core.Logging;

namespace Eunomia.Server.Core.Servers;

/// <summary>
/// The single log-enrichment property that marks a log event as server-related. Only events carrying
/// this property reach the dedicated per-server log sink; every other event is ignored by it. Enrich
/// a call site by wrapping it in <c>using (logger.BeginScope(ServerScope.Property(scope)))</c>.
/// </summary>
public static class ServerScope
{
    /// <summary>The log property/scope key the per-server sink filters on.</summary>
    public const string PropertyName = "ServerScope";

    /// <summary>
    /// Builds the logging scope state that stamps <see cref="PropertyName"/> onto every event emitted
    /// inside the scope, so the per-server sink picks them up.
    /// </summary>
    public static IReadOnlyDictionary<string, object> Property(string scope)
    {
        // Scopes come from an unauthenticated handshake and are stamped onto every event in the scope,
        // so they are sanitised once here rather than at each call site.
        return new Dictionary<string, object> { [PropertyName] = LogSafe.Value(scope) };
    }
}
