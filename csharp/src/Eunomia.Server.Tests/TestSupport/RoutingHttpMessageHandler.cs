// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using System.Net;
using System.Net.Http.Headers;

namespace Eunomia.Server.Tests.TestSupport;

/// <summary>
/// A test double <see cref="HttpMessageHandler"/> that returns a JSON body chosen by matching a
/// substring of the request URL, letting a single client stand in for a provider's token + user
/// endpoints. Records the requests it saw so tests can assert on headers/URLs.
/// </summary>
public sealed class RoutingHttpMessageHandler : HttpMessageHandler
{
    private readonly IReadOnlyList<(string UrlContains, string JsonBody)> routes;

    public RoutingHttpMessageHandler(params (string UrlContains, string JsonBody)[] routes)
    {
        this.routes = routes;
    }

    public List<HttpRequestMessage> Requests { get; } = [];

    protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken)
    {
        Requests.Add(request);

        string url = request.RequestUri?.ToString() ?? string.Empty;
        foreach ((string urlContains, string jsonBody) in routes)
        {
            if (url.Contains(urlContains, StringComparison.OrdinalIgnoreCase))
            {
                HttpResponseMessage matched = new(HttpStatusCode.OK)
                {
                    Content = new StringContent(jsonBody),
                };
                matched.Content.Headers.ContentType = new MediaTypeHeaderValue("application/json");
                return Task.FromResult(matched);
            }
        }

        return Task.FromResult(new HttpResponseMessage(HttpStatusCode.NotFound));
    }
}
