// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

namespace Eunomia.Server.Tests.TestSupport;

/// <summary>
/// An <see cref="IHttpClientFactory"/> test double that always hands back an
/// <see cref="HttpClient"/> built on a single, caller-supplied handler, regardless of the
/// requested client name.
/// </summary>
public sealed class SingleHandlerHttpClientFactory : IHttpClientFactory
{
    private readonly HttpMessageHandler _handler;

    public SingleHandlerHttpClientFactory(HttpMessageHandler handler)
    {
        _handler = handler;
    }

    public HttpClient CreateClient(string name)
    {
        return new HttpClient(_handler, disposeHandler: false);
    }
}
