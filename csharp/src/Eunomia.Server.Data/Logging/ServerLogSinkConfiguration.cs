// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using Eunomia.Server.Core.Servers;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.DependencyInjection;
using Serilog;
using Serilog.Filters;

namespace Eunomia.Server.Data.Logging;

/// <summary>
/// Serilog wiring for the per-server log sink: a sub-logger that only lets events carrying the
/// <see cref="ServerScope.PropertyName"/> property through to a <see cref="ServerLogSink"/>. Serilog owns
/// the sink's lifetime and disposes (flushes) it on logger close.
/// </summary>
public static class ServerLogSinkConfiguration
{
    /// <summary>
    /// Attaches the filtered per-server sink to the given logger configuration, resolving the EF context
    /// factory from the application's service provider.
    /// </summary>
    public static LoggerConfiguration WriteToServerScopeLog(
        this LoggerConfiguration configuration,
        IServiceProvider services)
    {
        IDbContextFactory<EunomiaDbContext> factory =
            services.GetRequiredService<IDbContextFactory<EunomiaDbContext>>();

        return configuration.WriteTo.Logger(sub => sub
            .Filter.ByIncludingOnly(Matching.WithProperty(ServerScope.PropertyName))
            .WriteTo.Sink(new ServerLogSink(factory)));
    }
}
