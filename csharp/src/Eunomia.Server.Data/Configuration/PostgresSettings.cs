// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using Microsoft.Extensions.Configuration;

namespace Eunomia.Server.Data.Configuration;

/// <summary>
/// Postgres connection settings, bound from the <c>Postgres</c> configuration section with
/// <c>POSTGRES__*</c> environment-variable fallbacks (matching the docker-compose env layout).
/// </summary>
public class PostgresSettings
{
    public string Host { get; set; } = "localhost";

    public int Port { get; set; } = 5432;

    public string Database { get; set; } = "eunomia";

    public string Username { get; set; } = "postgres";

    public string Password { get; set; } = string.Empty;

    public string ConnectionString =>
        $"Host={Host};Port={Port};Database={Database};Username={Username};Password={Password};SSL Mode=Prefer";

    /// <summary>
    /// Builds settings from configuration, preferring <c>Postgres:*</c> keys and falling back to
    /// <c>POSTGRES__*</c> environment variables.
    /// </summary>
    public static PostgresSettings FromConfiguration(IConfiguration configuration)
    {
        IConfigurationSection section = configuration.GetSection("Postgres");

        return new PostgresSettings
        {
            Host = section["Host"] ?? Environment.GetEnvironmentVariable("POSTGRES__HOST") ?? "localhost",
            Port = int.TryParse(
                    section["Port"] ?? Environment.GetEnvironmentVariable("POSTGRES__PORT"),
                    out int port)
                ? port
                : 5432,
            Database = section["Database"] ?? Environment.GetEnvironmentVariable("POSTGRES__DATABASE") ?? "eunomia",
            Username = section["Username"] ?? Environment.GetEnvironmentVariable("POSTGRES__USERNAME") ?? "postgres",
            Password = section["Password"] ?? Environment.GetEnvironmentVariable("POSTGRES__PASSWORD") ?? string.Empty,
        };
    }
}
