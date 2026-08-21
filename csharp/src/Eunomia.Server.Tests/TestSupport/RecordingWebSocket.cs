// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using System.Net.WebSockets;

namespace Eunomia.Server.Tests.TestSupport;

/// <summary>
/// A minimal in-memory <see cref="WebSocket"/> that records the close status/reason it was asked to
/// send, standing in for a real accepted socket so the block-close (1008) path can be asserted without
/// a live handshake. Receive never returns (the middleware's block path closes before pumping).
/// </summary>
public sealed class RecordingWebSocket : WebSocket
{
    private WebSocketState _state = WebSocketState.Open;

    public WebSocketCloseStatus? RecordedStatus { get; private set; }

    public string? RecordedReason { get; private set; }

    public override WebSocketCloseStatus? CloseStatus => RecordedStatus;

    public override string? CloseStatusDescription => RecordedReason;

    public override WebSocketState State => _state;

    public override string? SubProtocol => null;

    public override void Abort()
    {
        _state = WebSocketState.Aborted;
    }

    public override Task CloseAsync(WebSocketCloseStatus closeStatus, string? statusDescription, CancellationToken cancellationToken)
    {
        RecordedStatus = closeStatus;
        RecordedReason = statusDescription;
        _state = WebSocketState.Closed;
        return Task.CompletedTask;
    }

    public override Task CloseOutputAsync(WebSocketCloseStatus closeStatus, string? statusDescription, CancellationToken cancellationToken)
    {
        return CloseAsync(closeStatus, statusDescription, cancellationToken);
    }

    public override void Dispose()
    {
        _state = WebSocketState.Closed;
    }

    public override Task<WebSocketReceiveResult> ReceiveAsync(ArraySegment<byte> buffer, CancellationToken cancellationToken)
    {
        return new TaskCompletionSource<WebSocketReceiveResult>().Task;
    }

    public override Task SendAsync(ArraySegment<byte> buffer, WebSocketMessageType messageType, bool endOfMessage, CancellationToken cancellationToken)
    {
        return Task.CompletedTask;
    }
}
