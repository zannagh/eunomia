// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using System.Net;
using System.Net.Http;
using Asp.Versioning;
using Eunomia.Server.Api.Versioning;
using Eunomia.Server.Tests.TestSupport;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Routing;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Options;

namespace Eunomia.Server.Tests.Versioning;

/// <summary>
/// Boots the real host and drives HTTP through the router. These are the only tests that would catch
/// a missing <c>AddApiVersioning</c> registration: without it the <c>{version:apiVersion}</c> route
/// constraint is unknown and endpoint construction throws at request time, even though the build and
/// every controller unit test still pass.
/// </summary>
public class ApiVersionRoutingTests : IClassFixture<EunomiaWebAppFactory>
{
    private readonly EunomiaWebAppFactory _factory;

    public ApiVersionRoutingTests(EunomiaWebAppFactory factory)
    {
        _factory = factory;
    }

    [Fact]
    public async Task VersionedRoute_IsRouted_AndEnforcesAuthentication()
    {
        using HttpClient client = _factory.CreateClient();

        using HttpResponseMessage response = await client.GetAsync(new Uri("/api/v0.3/servers", UriKind.Relative));

        // 401, not 404: the dotted version segment resolved to the controller and the request died on
        // the StaffOnly policy instead of at routing.
        Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
    }

    [Fact]
    public async Task LegacyUnversionedRoute_ResolvesToTheDefaultVersion()
    {
        using HttpClient client = _factory.CreateClient();

        using HttpResponseMessage response = await client.GetAsync(new Uri("/api/servers", UriKind.Relative));

        // The already-released 0.3.0 Java client calls the unversioned path; it must keep resolving.
        // 401 (not 404) proves it matched an endpoint and not an ambiguous-match failure (500).
        Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
    }

    [Fact]
    public void LegacyUnversionedRoute_IsMappedToTheDefaultVersion()
    {
        Endpoint endpoint = Assert.Single(
            _factory.Services.GetRequiredService<EndpointDataSource>().Endpoints,
            candidate => candidate is RouteEndpoint route
                && string.Equals(route.RoutePattern.RawText, "api/Servers", StringComparison.Ordinal));

        ApiVersionMetadata metadata = endpoint.Metadata.GetMetadata<ApiVersionMetadata>()!;

        Assert.Contains(EunomiaApiVersions.Oldest, metadata.Map(ApiVersionMapping.Implicit).SupportedApiVersions);
    }

    [Theory]
    [InlineData("/api/v3/servers")]
    [InlineData("/api/v0/servers")]
    [InlineData("/api/v0.3.0/servers")]
    public async Task MalformedVersionSegments_DoNotResolve(string path)
    {
        using HttpClient client = _factory.CreateClient();

        using HttpResponseMessage response = await client.GetAsync(new Uri(path, UriKind.Relative));

        Assert.Equal(HttpStatusCode.NotFound, response.StatusCode);
    }

    [Fact]
    public void DefaultApiVersion_TracksTheOldestDeclaredVersion()
    {
        ApiVersioningOptions options = _factory.Services
            .GetRequiredService<IOptions<ApiVersioningOptions>>().Value;

        // Unversioned callers are legacy clients, so the default must follow the OLDEST supported
        // version. A future release adding 0.4 must not silently retarget them at the newest one.
        Assert.Equal(EunomiaApiVersions.Oldest, options.DefaultApiVersion);
        Assert.True(options.AssumeDefaultVersionWhenUnspecified);
        Assert.True(options.ReportApiVersions);
    }
}
