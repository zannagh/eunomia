// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using System.Net;

namespace Eunomia.Server.Tests.TestSupport;

/// <summary>
/// A test double <see cref="HttpMessageHandler"/> that either returns a fixed status code or
/// throws, and counts how many times it was invoked so tests can assert on caching behavior.
/// </summary>
public sealed class StubHttpMessageHandler : HttpMessageHandler
{
    private readonly HttpStatusCode? _statusCode;
    private readonly Exception? _throws;

    private StubHttpMessageHandler(HttpStatusCode? statusCode, Exception? throws)
    {
        _statusCode = statusCode;
        _throws = throws;
    }

    public int InvocationCount { get; private set; }

    public static StubHttpMessageHandler ReturningStatus(HttpStatusCode statusCode)
    {
        return new StubHttpMessageHandler(statusCode, null);
    }

    public static StubHttpMessageHandler ThrowingException(Exception exception)
    {
        return new StubHttpMessageHandler(null, exception);
    }

    protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken)
    {
        InvocationCount++;

        if (_throws is not null)
        {
            throw _throws;
        }

        return Task.FromResult(new HttpResponseMessage(_statusCode!.Value));
    }
}
