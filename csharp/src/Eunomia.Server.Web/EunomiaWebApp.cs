// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using System.Text.Json;
using Asp.Versioning;
using Eunomia.Server.Api.Middlewares;
using Eunomia.Server.Api.Versioning;
using Eunomia.Server.Authentication;
using Eunomia.Server.Authentication.Controllers;
using Eunomia.Server.Core.Clients;
using Eunomia.Server.Core.Communication;
using Eunomia.Server.Core.Telemetry;
using Eunomia.Server.Data.DependencyInjection;
using Eunomia.Server.Data.Logging;
using Eunomia.Server.Web.Components;
using Eunomia.Server.Web.Logging;
using Microsoft.Extensions.Hosting;
using OpenTelemetry.Metrics;
using OpenTelemetry.Trace;
using Serilog;
using Serilog.Sinks.OpenTelemetry;

namespace Eunomia.Server.Web;

public static class EunomiaWebApp
{
    /// <summary>
    /// Full date and level on every console line, so a line read on its own (Loki, grep) is dated, and the host's
    /// Grafana Alloy pipeline can join exception continuation lines to the line that starts with this prefix and
    /// extract the level as a label.
    /// </summary>
    private const string ConsoleOutputTemplate =
        "[{Timestamp:yyyy-MM-dd HH:mm:ss.fff zzz} {Level:u3}] {Message:lj}{NewLine}{Exception}";

    public static void Main(string[] args)
    {
        IConfiguration bootstrapConfiguration = new ConfigurationBuilder().AddEnvironmentVariables().Build();
        LoggerConfiguration bootstrap = LoggingLevels.Apply(new LoggerConfiguration(), bootstrapConfiguration);
        Log.Logger = bootstrap
            .ReadFrom.Configuration(bootstrapConfiguration)
            .WriteTo.Console(outputTemplate: ConsoleOutputTemplate)
            .CreateBootstrapLogger();

        WebApplicationBuilder builder = WebApplication.CreateBuilder(args);
        builder.Host.UseSerilog((context, services, configuration) =>
            ConfigureLogging(configuration, context.Configuration, services));

        ConfigureServices(builder.Services, builder.Configuration, builder.Environment);

        WebApplication app = builder.Build();

        bool isDevelopment = app.Environment.IsDevelopment();
        app.Services.MigrateDatabase(isDevelopment);
        app.Services.InitializeServerBlocking(isDevelopment);

        app.UseWebSockets();
        app.UseSlowRequestLogging();
        app.UseMiddleware<WebSocketMiddleware>();
        app.UseEunomiaAuthentication();

        // MapStaticAssets, not UseStaticFiles: from .NET 9 on, the Blazor framework scripts
        // (/_framework/blazor.web.js) are served from the static asset manifest rather than as physical
        // files under wwwroot, so UseStaticFiles alone 404s them and the dashboard loses all
        // interactivity. This also serves wwwroot itself, with the precompressed variants.
        app.MapStaticAssets();
        app.MapControllers();
        app.MapRazorComponents<EunomiaApp>().AddInteractiveServerRenderMode();
        app.MapPrometheusScrapingEndpoint();
        app.MapGet("/health", () => Results.Ok("ok"));

        app.Run();
    }

    private static void ConfigureLogging(
        LoggerConfiguration logger,
        IConfiguration configuration,
        IServiceProvider services)
    {
        // Levels per category: defaults in LoggingLevels, overridable with Logging__LogLevel__<Category> (env/compose).
        // ReadFrom.Configuration runs after it, so an explicit Serilog section still wins.
        LoggingLevels.Apply(logger, configuration)
            .ReadFrom.Configuration(configuration)
            .ReadFrom.Services(services)

            // stdout: docker captures it and the host's Grafana Alloy ships the container log to Loki.
            .WriteTo.Console(outputTemplate: ConsoleOutputTemplate)

            // Dedicated per-server sink: only events carrying the ServerScope property reach it.
            .WriteToServerScopeLog(services);

        // Ship logs to the OTLP collector (e.g. the Aspire dashboard's Structured Logs view) next to traces and
        // metrics. Only when an endpoint is configured, so a bare `dotnet run` does not retry a missing collector.
        string? otlpEndpoint = configuration["OTEL_EXPORTER_OTLP_ENDPOINT"];
        if (!string.IsNullOrEmpty(otlpEndpoint))
        {
            string serviceName = configuration["OTEL_SERVICE_NAME"] is { Length: > 0 } name ? name : "Eunomia";
            logger.WriteTo.OpenTelemetry(options =>
            {
                options.Endpoint = otlpEndpoint;
                options.Protocol = OtlpProtocol.Grpc;
                options.ResourceAttributes.Add("service.name", serviceName);
            });
        }
    }

    private static void ConfigureServices(IServiceCollection services, IConfiguration configuration, IHostEnvironment environment)
    {
        services.AddControllers()
            .AddApplicationPart(typeof(AccountController).Assembly)
            .AddJsonOptions(options =>
                options.JsonSerializerOptions.PropertyNamingPolicy = JsonNamingPolicy.CamelCase);
        services.AddHttpClient();

        // URL-segment API versioning: /api/v0.3/... . The version mirrors the release semver
        // major.minor with the patch truncated, so 0.3.x -> "0.3". Without this registration the
        // {version:apiVersion} route constraint is unknown to RouteOptions.ConstraintMap and endpoint
        // construction throws at runtime even though the build succeeds.
        services.AddApiVersioning(options =>
            {
                // Tracks the OLDEST still-supported version, never the newest. Unversioned callers are
                // assumed to be legacy clients, so adding 0.4 must NOT move this off 0.3. Read from the
                // controller declarations rather than restated here, so the two cannot drift apart.
                options.DefaultApiVersion = EunomiaApiVersions.Oldest;
                options.AssumeDefaultVersionWhenUnspecified = true;
                options.ReportApiVersions = true;
                options.ApiVersionReader = new UrlSegmentApiVersionReader();
            })
            .AddMvc();

        // Blazor Server dashboard UI. AddCascadingAuthenticationState + antiforgery are already wired by
        // AddEunomiaAuthentication, so only the Razor component + interactive server render mode are added.
        services.AddRazorComponents()
            .AddInteractiveServerComponents();

        // Persistence is backed by Postgres: the DbContext factory + IKeyedPacketStore are registered here
        // and migrations are applied on startup (see MigrateDatabase in Main).
        services.ConfigureDataServices(configuration);

        // Cookie + JWT authentication, the StaffOnly/AdminOnly policies, and OAuth login/link controllers.
        services.AddEunomiaAuthentication(configuration, environment);
        services.AddSingleton<ConnectionManager>();
        services.AddSingleton<WebSocketHandler>();
        services.AddSingleton<MojangProfileClient>();

        services.AddOpenTelemetry()
            .WithTracing(tracing => tracing
                .AddSource(EunomiaTelemetry.ActivitySource.Name)
                .AddAspNetCoreInstrumentation()
                .AddHttpClientInstrumentation())
            .WithMetrics(metrics => metrics
                .AddMeter(EunomiaTelemetry.Meter.Name)
                .AddAspNetCoreInstrumentation()
                .AddHttpClientInstrumentation()
                .AddRuntimeInstrumentation()
                .AddPrometheusExporter());
    }
}
