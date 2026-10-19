// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using System.Diagnostics;

namespace Eunomia.Server.Web.Logging;

/// <summary>
/// Logs an HTTP request that took longer than <see cref="Threshold"/> at Warning (method, path without the query,
/// status, time). The per-request hosting lines are at Warning by default (see <see cref="LoggingLevels"/>), so this is
/// what remains to spot slow endpoints in the console/Loki.
/// </summary>
public static class SlowRequestLogging
{
    public static readonly TimeSpan Threshold = TimeSpan.FromSeconds(1);

    public static void UseSlowRequestLogging(this WebApplication app)
    {
        ILogger logger = app.Services.GetRequiredService<ILoggerFactory>().CreateLogger("Eunomia.SlowRequests");
        app.Use(async (context, next) =>
        {
            long started = Stopwatch.GetTimestamp();
            try
            {
                await next();
            }
            finally
            {
                TimeSpan elapsed = Stopwatch.GetElapsedTime(started);
                if (elapsed >= Threshold && IsMeasurable(context))
                {
                    logger.LogWarning(
                        "Slow request: {Method} {Path} -> {Status} in {ElapsedMs} ms",
                        context.Request.Method,
                        context.Request.Path.Value,
                        context.Response.StatusCode,
                        (long)elapsed.TotalMilliseconds);
                }
            }
        });
    }

    // The relay websocket and the dashboard's Blazor circuit (websocket or long polling) legitimately stay open long.
    internal static bool IsMeasurable(HttpContext context) =>
        !context.WebSockets.IsWebSocketRequest &&
        !context.Request.Path.StartsWithSegments("/_blazor");
}
