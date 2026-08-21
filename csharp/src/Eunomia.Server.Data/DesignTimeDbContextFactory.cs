// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Design;

namespace Eunomia.Server.Data;

/// <summary>
/// Design-time factory used by <c>dotnet ef</c> tooling to build the context for migrations. The
/// connection string here is a dev placeholder and is never used at runtime.
/// </summary>
public class DesignTimeDbContextFactory : IDesignTimeDbContextFactory<EunomiaDbContext>
{
    public EunomiaDbContext CreateDbContext(string[] args)
    {
        DbContextOptionsBuilder<EunomiaDbContext> optionsBuilder = new();
        optionsBuilder.UseNpgsql("Host=localhost;Port=18566;Database=eunomia;Username=postgres;Password=eunomia_dev");
        return new EunomiaDbContext(optionsBuilder.Options);
    }
}
