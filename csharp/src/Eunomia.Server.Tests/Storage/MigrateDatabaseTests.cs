// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using Eunomia.Server.Data;
using Eunomia.Server.Data.DependencyInjection;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.DependencyInjection;

namespace Eunomia.Server.Tests.Storage;

/// <summary>
/// Covers the dev-tolerant startup migration: an unreachable database (simulated by a factory that throws
/// when a context is created) is swallowed with a warning in Development so the app can boot, but keeps
/// failing hard outside Development so the app refuses to start against a broken database.
/// </summary>
public class MigrateDatabaseTests
{
    [Fact]
    public void MigrateDatabase_InDevelopment_SwallowsUnreachableDatabase()
    {
        IServiceProvider services = BuildProviderWithFailingFactory();

        Exception? exception = Record.Exception(() => services.MigrateDatabase(isDevelopment: true));

        Assert.Null(exception);
    }

    [Fact]
    public void MigrateDatabase_OutsideDevelopment_Rethrows()
    {
        IServiceProvider services = BuildProviderWithFailingFactory();

        Assert.Throws<InvalidOperationException>(() => services.MigrateDatabase(isDevelopment: false));
    }

    private static IServiceProvider BuildProviderWithFailingFactory()
    {
        ServiceCollection services = new();
        services.AddLogging();
        services.AddSingleton<IDbContextFactory<EunomiaDbContext>, ThrowingDbContextFactory>();
        return services.BuildServiceProvider();
    }

    private sealed class ThrowingDbContextFactory : IDbContextFactory<EunomiaDbContext>
    {
        public EunomiaDbContext CreateDbContext()
        {
            throw new InvalidOperationException("Database is unreachable.");
        }
    }
}
