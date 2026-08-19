// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using System.Net.WebSockets;
using System.Text;

namespace Eunomia.Server.Core.Clients;

/// <summary>
/// A connected websocket client, scoped to a single MC server (<see cref="Scope"/>).
/// </summary>
public class EunomiaClient
{
    private const int RateLimitPerSecond = 20;

    private readonly SemaphoreSlim _sendLock = new(1, 1);
    private readonly object _rateLimitLock = new();

    private int _tokens = RateLimitPerSecond;
    private DateTimeOffset _lastRefill = DateTimeOffset.UtcNow;

    public EunomiaClient(Guid id)
    {
        Id = id;
    }

    public Guid Id { get; }

    public WebSocket? Socket { get; set; }

    /// <summary>
    /// Gets or sets the MC-server identity this client is subscribed to.
    /// </summary>
    public string Scope { get; set; } = string.Empty;

    /// <summary>
    /// Gets or sets the remote IP the client connected from, used for per-IP connection caps.
    /// </summary>
    public string? RemoteIp { get; set; }

    /// <summary>
    /// Sends a single text frame to this client, serializing concurrent sends on the same socket.
    /// </summary>
    public async Task SendAsync(string json, CancellationToken cancellationToken)
    {
        if (Socket is null || Socket.State != WebSocketState.Open)
        {
            return;
        }

        byte[] bytes = Encoding.UTF8.GetBytes(json);

        await _sendLock.WaitAsync(cancellationToken);
        try
        {
            await Socket.SendAsync(
                new ArraySegment<byte>(bytes),
                WebSocketMessageType.Text,
                endOfMessage: true,
                cancellationToken);
        }
        finally
        {
            _sendLock.Release();
        }
    }

    /// <summary>
    /// Simple token-bucket check to drop abusive inbound traffic. Refills at
    /// <see cref="RateLimitPerSecond"/> tokens/sec, capped at the same burst size.
    /// </summary>
    public bool TryConsumeInboundToken()
    {
        lock (_rateLimitLock)
        {
            DateTimeOffset now = DateTimeOffset.UtcNow;
            double elapsedSeconds = (now - _lastRefill).TotalSeconds;
            if (elapsedSeconds > 0)
            {
                int refill = (int)(elapsedSeconds * RateLimitPerSecond);
                if (refill > 0)
                {
                    _tokens = Math.Min(RateLimitPerSecond, _tokens + refill);
                    _lastRefill = now;
                }
            }

            if (_tokens <= 0)
            {
                return false;
            }

            _tokens--;
            return true;
        }
    }
}
