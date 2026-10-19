// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using Eunomia.Server.Tests.TestSupport;
using Eunomia.Server.Web.Logging;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Http.Features;
using Microsoft.Extensions.Logging;

namespace Eunomia.Server.Tests.Logging;

/// <summary>
/// Drives the slow-request middleware over a <see cref="DefaultHttpContext"/>. The threshold is passed in, so a zero
/// threshold stands in for a slow request and an hour for a fast one without the suite actually waiting.
/// </summary>
public class SlowRequestLoggingTests
{
    private static readonly TimeSpan AlwaysSlow = TimeSpan.Zero;

    private static readonly TimeSpan NeverSlow = TimeSpan.FromHours(1);

    [Fact]
    public void IsMeasurable_WebSocketRequest_IsFalse()
    {
        DefaultHttpContext context = Request("/ws");
        context.Features.Set<IHttpWebSocketFeature>(new FakeWebSocketFeature());

        Assert.False(SlowRequestLogging.IsMeasurable(context));
    }

    [Theory]
    [InlineData("/_blazor")]
    [InlineData("/_blazor/negotiate")]
    public void IsMeasurable_BlazorCircuit_IsFalse(string path)
    {
        Assert.False(SlowRequestLogging.IsMeasurable(Request(path)));
    }

    [Theory]
    [InlineData("/api/v0.3/servers")]
    [InlineData("/_blazorish")]
    public void IsMeasurable_OrdinaryRequest_IsTrue(string path)
    {
        Assert.True(SlowRequestLogging.IsMeasurable(Request(path)));
    }

    [Fact]
    public async Task MeasureAsync_SlowRequest_LogsOneWarning()
    {
        RecordingLogger logger = new();
        DefaultHttpContext context = Request("/api/v0.3/servers", "POST");

        await SlowRequestLogging.MeasureAsync(context, () => Respond(context, 201), logger, AlwaysSlow);

        RecordedLogEntry entry = Assert.Single(logger.Entries);
        Assert.Equal(LogLevel.Warning, entry.Level);
        Assert.Equal("POST", entry.Properties["Method"]);
        Assert.Equal("/api/v0.3/servers", entry.Properties["Path"]);
        Assert.Equal(201, entry.Properties["Status"]);
    }

    [Fact]
    public async Task MeasureAsync_FastRequest_LogsNothing()
    {
        RecordingLogger logger = new();
        DefaultHttpContext context = Request("/api/v0.3/servers");

        await SlowRequestLogging.MeasureAsync(context, () => Respond(context, 200), logger, NeverSlow);

        Assert.Empty(logger.Entries);
    }

    [Fact]
    public async Task MeasureAsync_SlowBlazorCircuit_LogsNothing()
    {
        RecordingLogger logger = new();
        DefaultHttpContext context = Request("/_blazor");

        await SlowRequestLogging.MeasureAsync(context, () => Respond(context, 200), logger, AlwaysSlow);

        Assert.Empty(logger.Entries);
    }

    [Fact]
    public async Task MeasureAsync_ThrowingNext_StillLogsAndRethrows()
    {
        RecordingLogger logger = new();
        DefaultHttpContext context = Request("/api/v0.3/servers");

        await Assert.ThrowsAsync<InvalidOperationException>(() => SlowRequestLogging.MeasureAsync(
            context, () => throw new InvalidOperationException(), logger, AlwaysSlow));

        Assert.Single(logger.Entries);
    }

    [Fact]
    public async Task MeasureAsync_ControlCharactersInRequest_AreSanitized()
    {
        RecordingLogger logger = new();
        DefaultHttpContext context = Request("/api/x\r\n[FAKE] forged line", "GE\nT");

        await SlowRequestLogging.MeasureAsync(context, () => Respond(context, 404), logger, AlwaysSlow);

        RecordedLogEntry entry = Assert.Single(logger.Entries);
        Assert.Equal("/api/x␣␣[FAKE] forged line", entry.Properties["Path"]);
        Assert.Equal("GE␣T", entry.Properties["Method"]);
        Assert.DoesNotContain('\n', entry.Message);
        Assert.DoesNotContain('\r', entry.Message);
    }

    private static DefaultHttpContext Request(string path, string method = "GET")
    {
        DefaultHttpContext context = new();
        context.Request.Method = method;
        context.Request.Path = new PathString(path);
        return context;
    }

    private static Task Respond(HttpContext context, int status)
    {
        context.Response.StatusCode = status;
        return Task.CompletedTask;
    }
}
