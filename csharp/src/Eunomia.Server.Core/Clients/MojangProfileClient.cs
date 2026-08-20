// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using System.Collections.Concurrent;
using System.Net;
using Microsoft.Extensions.Logging;

namespace Eunomia.Server.Core.Clients;

/// <summary>
/// Verifies that a UUID corresponds to a real Mojang account, to keep unauthenticated websocket
/// connections from being spoofed. Fails closed (treats errors as "not real") so a Mojang outage
/// cannot be used to flood the server with unverified sockets.
/// </summary>
public class MojangProfileClient
{
    private const string DisableGateEnvironmentVariable = "EUNOMIA_DISABLE_MOJANG_GATE";

    private static readonly TimeSpan PositiveTtl = TimeSpan.FromHours(24);
    private static readonly TimeSpan NegativeTtl = TimeSpan.FromMinutes(5);

    private readonly IHttpClientFactory _httpClientFactory;
    private readonly ILogger<MojangProfileClient> _logger;
    private readonly ConcurrentDictionary<Guid, (bool Ok, DateTimeOffset Expires)> _cache = new();
    private readonly bool _gateDisabled;

    public MojangProfileClient(IHttpClientFactory httpClientFactory, ILogger<MojangProfileClient> logger)
    {
        _httpClientFactory = httpClientFactory;
        _logger = logger;
        _gateDisabled = IsTruthy(Environment.GetEnvironmentVariable(DisableGateEnvironmentVariable));

        if (_gateDisabled)
        {
            _logger.LogWarning(
                "Mojang gate DISABLED via {EnvVar} — do not use in production.", DisableGateEnvironmentVariable);
        }
    }

    public async Task<bool> IsRealAccountAsync(Guid uuid, CancellationToken cancellationToken)
    {
        if (_gateDisabled)
        {
            return true;
        }

        if (_cache.TryGetValue(uuid, out (bool Ok, DateTimeOffset Expires) cached) && cached.Expires > DateTimeOffset.UtcNow)
        {
            return cached.Ok;
        }

        bool result = await FetchAsync(uuid, cancellationToken);
        TimeSpan ttl = result ? PositiveTtl : NegativeTtl;
        _cache[uuid] = (result, DateTimeOffset.UtcNow + ttl);
        return result;
    }

    private async Task<bool> FetchAsync(Guid uuid, CancellationToken cancellationToken)
    {
        string url = $"https://sessionserver.mojang.com/session/minecraft/profile/{uuid:N}";
        try
        {
            HttpClient client = _httpClientFactory.CreateClient(nameof(MojangProfileClient));
            using HttpResponseMessage response = await client.GetAsync(url, cancellationToken);
            return response.StatusCode switch
            {
                HttpStatusCode.OK => true,
                HttpStatusCode.NoContent => false,
                HttpStatusCode.NotFound => false,
                _ => LogAndFailClosed(uuid, $"unexpected status {response.StatusCode}"),
            };
        }
        catch (Exception ex)
        {
            return LogAndFailClosed(uuid, ex.Message);
        }
    }

    private bool LogAndFailClosed(Guid uuid, string reason)
    {
        _logger.LogWarning("Mojang profile lookup failed for {Uuid}: {Reason}. Failing closed.", uuid, reason);
        return false;
    }

    private static bool IsTruthy(string? value)
    {
        return string.Equals(value, "true", StringComparison.OrdinalIgnoreCase) || value == "1";
    }
}
