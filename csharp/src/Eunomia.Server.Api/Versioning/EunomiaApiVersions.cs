// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using System.Reflection;
using Asp.Versioning;
using Microsoft.AspNetCore.Mvc;

namespace Eunomia.Server.Api.Versioning;

/// <summary>
/// Single source of truth for the HTTP/websocket API versions this build serves. The set is derived
/// from the <see cref="ApiVersionAttribute"/> declarations on the controllers in this assembly, so
/// adding or retiring a version means touching only the controller.
/// <para>
/// The API version mirrors the release semver with the patch truncated: 0.3.x serves "0.3", 1.1.x
/// serves "1.1". The dot is legal in the attribute string and in the URL segment (/api/v0.3/...) but
/// not in a C# identifier, which is why the namespaces use the <c>V0_3</c> form.
/// </para>
/// </summary>
public static class EunomiaApiVersions
{
    /// <summary>Every version declared by a controller in this assembly, ascending.</summary>
    public static IReadOnlyList<ApiVersion> Supported { get; } = Discover();

    /// <summary>
    /// The oldest still-supported version. This - not the newest - is what an unversioned caller is
    /// assumed to be speaking, because unversioned callers are by definition pre-versioning clients.
    /// </summary>
    public static ApiVersion Oldest => Supported[0];

    /// <summary>
    /// Resolves a caller-supplied version string (e.g. "0.3") against <see cref="Supported"/>.
    /// </summary>
    /// <param name="value">The raw version text, or null/empty for a legacy caller.</param>
    /// <param name="version">The resolved version; <see cref="Oldest"/> when <paramref name="value"/> is absent.</param>
    /// <returns><c>true</c> when the version is absent or supported; <c>false</c> when it is unsupported or malformed.</returns>
    public static bool TryResolve(string? value, out ApiVersion version)
    {
        if (string.IsNullOrWhiteSpace(value))
        {
            version = Oldest;
            return true;
        }

        if (!ApiVersionParser.Default.TryParse(value, out ApiVersion? parsed) || parsed is null)
        {
            version = Oldest;
            return false;
        }

        version = parsed;
        return Supported.Contains(parsed);
    }

    private static IReadOnlyList<ApiVersion> Discover()
    {
        List<ApiVersion> versions = typeof(EunomiaApiVersions).Assembly
            .GetTypes()
            .Where(type => type.GetCustomAttribute<ApiControllerAttribute>() is not null)
            .SelectMany(type => type.GetCustomAttributes<ApiVersionAttribute>())
            .SelectMany(attribute => attribute.Versions)
            .Distinct()
            .OrderBy(version => version)
            .ToList();

        if (versions.Count == 0)
        {
            throw new InvalidOperationException(
                "No [ApiVersion] declarations were found on the Eunomia API controllers.");
        }

        return versions;
    }
}
