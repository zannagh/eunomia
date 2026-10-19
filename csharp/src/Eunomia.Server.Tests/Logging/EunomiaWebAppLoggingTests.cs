// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using Eunomia.Server.Web;
using Microsoft.Extensions.Configuration;

namespace Eunomia.Server.Tests.Logging;

/// <summary>
/// Covers when the web host ships logs to an OTLP collector: only with <c>OTEL_EXPORTER_OTLP_ENDPOINT</c> set, so a
/// bare <c>dotnet run</c> does not keep retrying a missing collector, and under <c>OTEL_SERVICE_NAME</c> or the
/// <c>Eunomia</c> fallback.
/// </summary>
public class EunomiaWebAppLoggingTests
{
    [Theory]
    [InlineData(null)]
    [InlineData("")]
    public void ResolveOtlpLogExport_NoEndpoint_ReturnsNull(string? endpoint)
    {
        IConfiguration configuration = Configuration(endpoint, serviceName: "eunomia-test");

        Assert.Null(EunomiaWebApp.ResolveOtlpLogExport(configuration));
    }

    [Fact]
    public void ResolveOtlpLogExport_EndpointWithoutServiceName_FallsBackToEunomia()
    {
        IConfiguration configuration = Configuration("http://collector:4317", serviceName: null);

        (string Endpoint, string ServiceName)? export = EunomiaWebApp.ResolveOtlpLogExport(configuration);

        Assert.Equal(("http://collector:4317", "Eunomia"), export);
    }

    [Fact]
    public void ResolveOtlpLogExport_EndpointAndServiceName_UsesBoth()
    {
        IConfiguration configuration = Configuration("http://collector:4317", serviceName: "eunomia-staging");

        (string Endpoint, string ServiceName)? export = EunomiaWebApp.ResolveOtlpLogExport(configuration);

        Assert.Equal(("http://collector:4317", "eunomia-staging"), export);
    }

    private static IConfiguration Configuration(string? endpoint, string? serviceName) =>
        new ConfigurationBuilder()
            .AddInMemoryCollection(new Dictionary<string, string?>
            {
                ["OTEL_EXPORTER_OTLP_ENDPOINT"] = endpoint,
                ["OTEL_SERVICE_NAME"] = serviceName,
            })
            .Build();
}
