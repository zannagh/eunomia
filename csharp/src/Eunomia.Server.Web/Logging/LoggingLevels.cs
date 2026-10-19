// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using Serilog;
using Serilog.Events;

namespace Eunomia.Server.Web.Logging;

/// <summary>
/// Log levels per category, from the standard <c>Logging:LogLevel</c> configuration section, i.e. appsettings or
/// environment variables such as <c>Logging__LogLevel__Default=Information</c> or
/// <c>Logging__LogLevel__Microsoft.EntityFrameworkCore=Information</c>. Serilog is the one log pipeline and ignores
/// that section on its own, so it is read here. The defaults below apply where the section says nothing. A category's
/// level also covers the categories below it; the longest name wins.
/// </summary>
/// <remarks>
/// The app's own categories (<c>Eunomia.*</c>) fall under <c>Default</c> and must stay at Information or lower: the
/// ServerScope Information lines (connect, refuse, block) feed the per-server DB log that drives the dashboard.
/// </remarks>
public static class LoggingLevels
{
    public const string DefaultCategory = "Default";

    /// <summary>
    /// The framework categories sit at Warning so per-request hosting/routing lines and EF Core "Executed DbCommand"
    /// lines (every relay POST and every ServerLogSink batch insert) no longer ship at Information.
    /// </summary>
    public static readonly IReadOnlyDictionary<string, LogEventLevel> Defaults = new Dictionary<string, LogEventLevel>
    {
        [DefaultCategory] = LogEventLevel.Information,
        ["Microsoft.AspNetCore"] = LogEventLevel.Warning,
        ["Microsoft.EntityFrameworkCore"] = LogEventLevel.Warning,
        ["Microsoft.Hosting.Lifetime"] = LogEventLevel.Information,
    };

    /// <summary>Sets the minimum level and the per-category overrides on <paramref name="logger"/>.</summary>
    public static LoggerConfiguration Apply(LoggerConfiguration logger, IConfiguration configuration)
    {
        Dictionary<string, LogEventLevel> levels = Resolve(configuration);
        logger.MinimumLevel.Is(levels[DefaultCategory]);
        foreach ((string category, LogEventLevel level) in levels.Where(l => l.Key != DefaultCategory))
        {
            logger.MinimumLevel.Override(category, level);
        }

        return logger;
    }

    /// <summary>The defaults overlaid with the configured <c>Logging:LogLevel</c> section.</summary>
    public static Dictionary<string, LogEventLevel> Resolve(IConfiguration configuration)
    {
        Dictionary<string, LogEventLevel> levels = Defaults.ToDictionary(d => d.Key, d => d.Value);
        IEnumerable<(string Category, LogEventLevel? Level)> configured = configuration
            .GetSection("Logging:LogLevel")
            .GetChildren()
            .Select(entry => (entry.Key, Parse(entry.Value)));
        foreach ((string category, LogEventLevel? level) in configured.Where(c => c.Level is not null))
        {
            levels[category] = level!.Value;
        }

        return levels;
    }

    /// <summary>
    /// Maps a Microsoft.Extensions.Logging level name (case-insensitive) to the Serilog level; <c>null</c> for anything
    /// else, including <c>None</c>.
    /// </summary>
    internal static LogEventLevel? Parse(string? value)
    {
        return value?.Trim().ToLowerInvariant() switch
        {
            "trace" => LogEventLevel.Verbose,
            "debug" => LogEventLevel.Debug,
            "information" => LogEventLevel.Information,
            "warning" => LogEventLevel.Warning,
            "error" => LogEventLevel.Error,
            "critical" => LogEventLevel.Fatal,
            _ => null,
        };
    }
}
