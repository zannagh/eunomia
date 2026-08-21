// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using System.Text.Json;
using Eunomia.Server.Api.Middlewares;
using Eunomia.Server.Authentication;
using Eunomia.Server.Authentication.Controllers;
using Eunomia.Server.Core.Clients;
using Eunomia.Server.Core.Communication;
using Eunomia.Server.Core.Telemetry;
using Eunomia.Server.Data.DependencyInjection;
using Eunomia.Server.Data.Logging;
using Eunomia.Server.Web.Components;
using Microsoft.Extensions.Hosting;
using OpenTelemetry.Metrics;
using OpenTelemetry.Trace;
using Serilog;

namespace Eunomia.Server.Web;

public static class EunomiaWebApp
{
    public static void Main(string[] args)
    {
        Log.Logger = new LoggerConfiguration()
            .ReadFrom.Configuration(new ConfigurationBuilder().AddEnvironmentVariables().Build())
            .WriteTo.Console()
            .CreateBootstrapLogger();

        WebApplicationBuilder builder = WebApplication.CreateBuilder(args);
        builder.Host.UseSerilog((context, services, configuration) => configuration
            .ReadFrom.Configuration(context.Configuration)
            .ReadFrom.Services(services)
            .WriteTo.Console()

            // Dedicated per-server sink: only events carrying the ServerScope property reach it.
            .WriteToServerScopeLog(services));

        ConfigureServices(builder.Services, builder.Configuration, builder.Environment);

        WebApplication app = builder.Build();

        bool isDevelopment = app.Environment.IsDevelopment();
        app.Services.MigrateDatabase(isDevelopment);
        app.Services.InitializeServerBlocking(isDevelopment);

        app.UseStaticFiles();
        app.UseWebSockets();
        app.UseMiddleware<WebSocketMiddleware>();
        app.UseEunomiaAuthentication();
        app.MapControllers();
        app.MapRazorComponents<EunomiaApp>().AddInteractiveServerRenderMode();
        app.MapPrometheusScrapingEndpoint();
        app.MapGet("/health", () => Results.Ok("ok"));

        app.Run();
    }

    private static void ConfigureServices(IServiceCollection services, IConfiguration configuration, IHostEnvironment environment)
    {
        services.AddControllers()
            .AddApplicationPart(typeof(AccountController).Assembly)
            .AddJsonOptions(options =>
                options.JsonSerializerOptions.PropertyNamingPolicy = JsonNamingPolicy.CamelCase);
        services.AddHttpClient();

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
