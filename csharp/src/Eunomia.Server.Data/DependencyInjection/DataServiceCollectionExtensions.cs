// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using Eunomia.Server.Core.Servers;
using Eunomia.Server.Core.Storage;
using Eunomia.Server.Data.Configuration;
using Eunomia.Server.Data.Servers;
using Eunomia.Server.Data.Storage;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Logging;

namespace Eunomia.Server.Data.DependencyInjection;

/// <summary>
/// Wires up the Postgres-backed data layer: the EF Core context factory, the keyed-packet store, and
/// the settings bound from configuration.
/// </summary>
public static class DataServiceCollectionExtensions
{
    /// <summary>
    /// Registers <see cref="EunomiaDbContext"/> (via a context factory over Npgsql), the
    /// <see cref="PostgresSettings"/>, and the Postgres <see cref="IKeyedPacketStore"/>.
    /// </summary>
    public static IServiceCollection ConfigureDataServices(
        this IServiceCollection services,
        IConfiguration configuration)
    {
        PostgresSettings postgres = PostgresSettings.FromConfiguration(configuration);
        services.AddSingleton(postgres);

        services.AddDbContextFactory<EunomiaDbContext>(options =>
            options.UseNpgsql(postgres.ConnectionString));

        services.AddSingleton<IKeyedPacketStore, PgKeyedPacketStore>();

        // Server telemetry/presence reads and administrative blocking (cached blocked-scope set).
        services.AddSingleton<IServerDirectory, ServerDirectory>();
        services.AddSingleton<IServerBlockService, ServerBlockService>();

        return services;
    }

    /// <summary>
    /// Loads the blocked-scope cache from the database. Call once on startup after migrations.
    /// </summary>
    /// <param name="services">The application service provider.</param>
    /// <param name="isDevelopment">
    /// When <c>true</c> (Development), a failure to load the cache from an unreachable database is logged as a
    /// warning and startup continues (mirroring <see cref="MigrateDatabase"/>); otherwise the failure propagates.
    /// </param>
    public static void InitializeServerBlocking(this IServiceProvider services, bool isDevelopment = false)
    {
        using IServiceScope scope = services.CreateScope();
        IServerBlockService blocking = scope.ServiceProvider.GetRequiredService<IServerBlockService>();

        try
        {
            blocking.InitializeAsync().GetAwaiter().GetResult();
        }
        catch (Exception ex) when (isDevelopment)
        {
            ILogger<EunomiaDbContext> logger =
                scope.ServiceProvider.GetRequiredService<ILogger<EunomiaDbContext>>();
            logger.LogWarning(
                ex,
                "Failed to load the blocked-scope cache; continuing without it because the environment is Development.");
        }
    }

    /// <summary>
    /// Applies pending EF Core migrations. Call once on startup after the host is built.
    /// </summary>
    /// <param name="services">The application service provider.</param>
    /// <param name="isDevelopment">
    /// When <c>true</c> (Development), an unreachable/failed database is logged as a warning and startup
    /// continues so the app can boot without a local Postgres. In non-Development the failure is rethrown
    /// so the app refuses to start against a broken database.
    /// </param>
    public static void MigrateDatabase(this IServiceProvider services, bool isDevelopment = false)
    {
        using IServiceScope scope = services.CreateScope();
        IDbContextFactory<EunomiaDbContext> factory =
            scope.ServiceProvider.GetRequiredService<IDbContextFactory<EunomiaDbContext>>();
        ILogger<EunomiaDbContext> logger =
            scope.ServiceProvider.GetRequiredService<ILogger<EunomiaDbContext>>();

        try
        {
            using EunomiaDbContext context = factory.CreateDbContext();
            context.Database.Migrate();
        }
        catch (Exception ex) when (isDevelopment)
        {
            logger.LogWarning(
                ex,
                "Failed to apply EF Core migrations to the Eunomia database; continuing without a database because the environment is Development.");
        }
        catch (Exception ex)
        {
            logger.LogError(ex, "Failed to apply EF Core migrations to the Eunomia database.");
            throw;
        }
    }
}
