// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using System.Net;
using Eunomia.Server.Core.Clients;
using Eunomia.Server.Tests.TestSupport;
using Microsoft.Extensions.Logging.Abstractions;

namespace Eunomia.Server.Tests.Clients;

public class MojangProfileClientTests
{
    private const string DisableGateEnvVar = "EUNOMIA_DISABLE_MOJANG_GATE";

    [Fact]
    public async Task IsRealAccountAsync_Returns_True_On_200()
    {
        StubHttpMessageHandler handler = StubHttpMessageHandler.ReturningStatus(HttpStatusCode.OK);
        MojangProfileClient client = NewClient(handler);

        bool result = await client.IsRealAccountAsync(Guid.NewGuid(), CancellationToken.None);

        Assert.True(result);
    }

    [Theory]
    [InlineData(HttpStatusCode.NoContent)]
    [InlineData(HttpStatusCode.NotFound)]
    public async Task IsRealAccountAsync_Returns_False_On_KnownNegativeStatuses(HttpStatusCode statusCode)
    {
        StubHttpMessageHandler handler = StubHttpMessageHandler.ReturningStatus(statusCode);
        MojangProfileClient client = NewClient(handler);

        bool result = await client.IsRealAccountAsync(Guid.NewGuid(), CancellationToken.None);

        Assert.False(result);
    }

    [Fact]
    public async Task IsRealAccountAsync_FailsClosed_OnUnexpectedStatus()
    {
        StubHttpMessageHandler handler = StubHttpMessageHandler.ReturningStatus(HttpStatusCode.InternalServerError);
        MojangProfileClient client = NewClient(handler);

        bool result = await client.IsRealAccountAsync(Guid.NewGuid(), CancellationToken.None);

        Assert.False(result);
    }

    [Fact]
    public async Task IsRealAccountAsync_FailsClosed_OnNetworkException()
    {
        StubHttpMessageHandler handler = StubHttpMessageHandler.ThrowingException(new HttpRequestException("boom"));
        MojangProfileClient client = NewClient(handler);

        bool result = await client.IsRealAccountAsync(Guid.NewGuid(), CancellationToken.None);

        Assert.False(result);
    }

    [Fact]
    public async Task IsRealAccountAsync_CachesPositiveResult_WithoutReHittingMojang()
    {
        StubHttpMessageHandler handler = StubHttpMessageHandler.ReturningStatus(HttpStatusCode.OK);
        MojangProfileClient client = NewClient(handler);
        Guid uuid = Guid.NewGuid();

        bool first = await client.IsRealAccountAsync(uuid, CancellationToken.None);
        bool second = await client.IsRealAccountAsync(uuid, CancellationToken.None);

        Assert.True(first);
        Assert.True(second);
        Assert.Equal(1, handler.InvocationCount);
    }

    [Fact]
    public async Task IsRealAccountAsync_CachesNegativeResult_WithoutReHittingMojang()
    {
        StubHttpMessageHandler handler = StubHttpMessageHandler.ReturningStatus(HttpStatusCode.NotFound);
        MojangProfileClient client = NewClient(handler);
        Guid uuid = Guid.NewGuid();

        bool first = await client.IsRealAccountAsync(uuid, CancellationToken.None);
        bool second = await client.IsRealAccountAsync(uuid, CancellationToken.None);

        Assert.False(first);
        Assert.False(second);
        Assert.Equal(1, handler.InvocationCount);
    }

    [Fact]
    public async Task IsRealAccountAsync_CachesIndependently_PerUuid()
    {
        StubHttpMessageHandler handler = StubHttpMessageHandler.ReturningStatus(HttpStatusCode.OK);
        MojangProfileClient client = NewClient(handler);

        await client.IsRealAccountAsync(Guid.NewGuid(), CancellationToken.None);
        await client.IsRealAccountAsync(Guid.NewGuid(), CancellationToken.None);

        Assert.Equal(2, handler.InvocationCount);
    }

    [Theory]
    [InlineData("true")]
    [InlineData("TRUE")]
    [InlineData("1")]
    public async Task IsRealAccountAsync_ReturnsTrue_WhenGateDisabledViaEnvVar_WithoutHittingMojang(string envValue)
    {
        // The failing status would normally return false; the disabled gate must short-circuit before that.
        StubHttpMessageHandler handler = StubHttpMessageHandler.ReturningStatus(HttpStatusCode.NotFound);
        Environment.SetEnvironmentVariable(DisableGateEnvVar, envValue);
        try
        {
            MojangProfileClient client = NewClient(handler);

            bool result = await client.IsRealAccountAsync(Guid.NewGuid(), CancellationToken.None);

            Assert.True(result);
            Assert.Equal(0, handler.InvocationCount);
        }
        finally
        {
            Environment.SetEnvironmentVariable(DisableGateEnvVar, null);
        }
    }

    [Fact]
    public async Task IsRealAccountAsync_GateStaysEnabled_WhenEnvVarUnset()
    {
        Environment.SetEnvironmentVariable(DisableGateEnvVar, null);
        StubHttpMessageHandler handler = StubHttpMessageHandler.ReturningStatus(HttpStatusCode.OK);

        MojangProfileClient client = NewClient(handler);
        bool result = await client.IsRealAccountAsync(Guid.NewGuid(), CancellationToken.None);

        Assert.True(result);
        Assert.Equal(1, handler.InvocationCount);
    }

    private static MojangProfileClient NewClient(StubHttpMessageHandler handler)
    {
        return new MojangProfileClient(new SingleHandlerHttpClientFactory(handler), NullLogger<MojangProfileClient>.Instance);
    }
}
