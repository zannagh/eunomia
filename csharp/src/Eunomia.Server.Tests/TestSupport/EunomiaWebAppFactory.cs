// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using Eunomia.Server.Web.Components;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Mvc.Testing;
using Microsoft.Extensions.Hosting;

namespace Eunomia.Server.Tests.TestSupport;

/// <summary>
/// Boots the real Eunomia web host in-process so that routing - and therefore the API versioning
/// registration - is actually exercised. The controller-level unit tests construct controllers
/// directly and never touch routing, so nothing else in the suite would notice a missing
/// <c>AddApiVersioning</c> until runtime.
/// <para>
/// The entry-point type parameter only identifies the assembly to scan; <c>EunomiaWebApp</c> itself is
/// a static class and cannot be used as a type argument, so the Razor root component stands in for it.
/// The environment is pinned to Development because startup deliberately downgrades an unreachable
/// database to a warning there, which lets routing be exercised without a live Postgres.
/// </para>
/// </summary>
public sealed class EunomiaWebAppFactory : WebApplicationFactory<EunomiaApp>
{
    protected override void ConfigureWebHost(IWebHostBuilder builder)
    {
        builder.UseEnvironment(Environments.Development);
    }
}
