// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using Eunomia.Server.Web.Logging;
using Microsoft.Extensions.Configuration;
using Serilog;
using Serilog.Core;
using Serilog.Events;

namespace Eunomia.Server.Tests.Logging;

/// <summary>
/// Covers how the standard <c>Logging:LogLevel</c> section is turned into Serilog levels: the defaults when nothing is
/// configured, overrides from configuration and environment variables, unparseable values being ignored, the
/// Microsoft-to-Serilog level mapping, and the minimum level/overrides actually landing on the logger.
/// </summary>
public class LoggingLevelsTests
{
    [Fact]
    public void Resolve_NothingConfigured_ReturnsTheDefaults()
    {
        Dictionary<string, LogEventLevel> levels = LoggingLevels.Resolve(Configuration());

        Assert.Equal(LoggingLevels.Defaults.OrderBy(d => d.Key), levels.OrderBy(l => l.Key));
    }

    [Fact]
    public void Resolve_ConfiguredLevels_OverrideDefaultsAndAddCategories()
    {
        IConfiguration configuration = Configuration(
            ("Logging:LogLevel:Default", "Warning"),
            ("Logging:LogLevel:Microsoft.EntityFrameworkCore", "Information"),
            ("Logging:LogLevel:Eunomia.Server.Data", "Debug"));

        Dictionary<string, LogEventLevel> levels = LoggingLevels.Resolve(configuration);

        Assert.Equal(LogEventLevel.Warning, levels[LoggingLevels.DefaultCategory]);
        Assert.Equal(LogEventLevel.Information, levels["Microsoft.EntityFrameworkCore"]);
        Assert.Equal(LogEventLevel.Debug, levels["Eunomia.Server.Data"]);
        Assert.Equal(LogEventLevel.Warning, levels["Microsoft.AspNetCore"]);
    }

    [Fact]
    public void Resolve_EnvironmentVariables_OverrideDefaults()
    {
        // A unique prefix keeps the process-wide environment change from leaking into other tests.
        string prefix = "EUNOMIA_LOGGING_TEST_" + Guid.NewGuid().ToString("N") + "_";
        string variable = prefix + "Logging__LogLevel__Microsoft.AspNetCore";
        Environment.SetEnvironmentVariable(variable, "Information");
        try
        {
            IConfiguration configuration = new ConfigurationBuilder().AddEnvironmentVariables(prefix).Build();

            Dictionary<string, LogEventLevel> levels = LoggingLevels.Resolve(configuration);

            Assert.Equal(LogEventLevel.Information, levels["Microsoft.AspNetCore"]);
        }
        finally
        {
            Environment.SetEnvironmentVariable(variable, null);
        }
    }

    [Theory]
    [InlineData("Nonsense")]
    [InlineData("None")]
    [InlineData("")]
    [InlineData("   ")]
    public void Resolve_UnparseableValue_IsIgnored(string value)
    {
        IConfiguration configuration = Configuration(
            ("Logging:LogLevel:Default", value),
            ("Logging:LogLevel:Eunomia.Unparseable", value));

        Dictionary<string, LogEventLevel> levels = LoggingLevels.Resolve(configuration);

        Assert.Equal(LogEventLevel.Information, levels[LoggingLevels.DefaultCategory]);
        Assert.False(levels.ContainsKey("Eunomia.Unparseable"));
    }

    [Theory]
    [InlineData("Trace", LogEventLevel.Verbose)]
    [InlineData("Debug", LogEventLevel.Debug)]
    [InlineData("Information", LogEventLevel.Information)]
    [InlineData("Warning", LogEventLevel.Warning)]
    [InlineData("Error", LogEventLevel.Error)]
    [InlineData("Critical", LogEventLevel.Fatal)]
    [InlineData(" warning ", LogEventLevel.Warning)]
    [InlineData("CRITICAL", LogEventLevel.Fatal)]
    public void Parse_MicrosoftLevelName_MapsToSerilogLevel(string value, LogEventLevel expected)
    {
        Assert.Equal(expected, LoggingLevels.Parse(value));
    }

    [Theory]
    [InlineData(null)]
    [InlineData("None")]
    [InlineData("Verbose")]
    [InlineData("Fatal")]
    public void Parse_UnknownName_ReturnsNull(string? value)
    {
        Assert.Null(LoggingLevels.Parse(value));
    }

    [Fact]
    public void Apply_SetsMinimumLevelAndCategoryOverrides()
    {
        IConfiguration configuration = Configuration(("Logging:LogLevel:Eunomia.Noisy", "Error"));

        using Logger logger = LoggingLevels.Apply(new LoggerConfiguration(), configuration).CreateLogger();

        Assert.True(logger.IsEnabled(LogEventLevel.Information));
        Assert.False(logger.IsEnabled(LogEventLevel.Debug));
        Assert.False(ForCategory(logger, "Microsoft.AspNetCore.Hosting.Diagnostics").IsEnabled(LogEventLevel.Information));
        Assert.True(ForCategory(logger, "Microsoft.AspNetCore.Hosting.Diagnostics").IsEnabled(LogEventLevel.Warning));
        Assert.True(ForCategory(logger, "Microsoft.Hosting.Lifetime").IsEnabled(LogEventLevel.Information));
        Assert.False(ForCategory(logger, "Eunomia.Noisy").IsEnabled(LogEventLevel.Warning));
        Assert.True(ForCategory(logger, "Eunomia.Server.Api").IsEnabled(LogEventLevel.Information));
    }

    private static Serilog.ILogger ForCategory(Logger logger, string category) =>
        logger.ForContext(Constants.SourceContextPropertyName, category);

    private static IConfiguration Configuration(params (string Key, string? Value)[] values) =>
        new ConfigurationBuilder()
            .AddInMemoryCollection(values.Select(v => new KeyValuePair<string, string?>(v.Key, v.Value)))
            .Build();
}
