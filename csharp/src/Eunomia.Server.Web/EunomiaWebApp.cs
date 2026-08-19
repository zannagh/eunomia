// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using System.Text.Json;
using Eunomia.Server.Api.Middlewares;
using Eunomia.Server.Core.Clients;
using Eunomia.Server.Core.Communication;
using Eunomia.Server.Core.Storage;
using Eunomia.Server.Core.Telemetry;
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
            .WriteTo.Console());

        ConfigureServices(builder.Services);

        WebApplication app = builder.Build();

        app.UseWebSockets();
        app.UseMiddleware<WebSocketMiddleware>();
        app.MapControllers();
        app.MapPrometheusScrapingEndpoint();
        app.MapGet("/health", () => Results.Ok("ok"));

        app.Run();
    }

    private static void ConfigureServices(IServiceCollection services)
    {
        services.AddControllers().AddJsonOptions(options =>
            options.JsonSerializerOptions.PropertyNamingPolicy = JsonNamingPolicy.CamelCase);
        services.AddHttpClient();

        services.AddSingleton<IKeyedPacketStore, KeyedPacketStore>();
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
