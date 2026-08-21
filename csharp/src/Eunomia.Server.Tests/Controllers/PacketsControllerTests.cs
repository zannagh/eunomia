// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using System.Text.Json;
using Eunomia.Server.Api.Controllers.V0_3;
using Eunomia.Server.Api.Models.V0_3;
using Eunomia.Server.Core.Clients;
using Eunomia.Server.Core.Communication;
using Eunomia.Server.Core.Servers;
using Eunomia.Server.Core.Storage;
using Eunomia.Server.Data.Storage;
using Eunomia.Server.Tests.TestSupport;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Mvc;
using Microsoft.Extensions.Logging.Abstractions;
using NSubstitute;

namespace Eunomia.Server.Tests.Controllers;

/// <summary>
/// Exercises PacketController's gating logic directly (no HTTP transport, no real sockets): a
/// <see cref="DefaultHttpContext"/> stands in for the request, a real <see cref="ConnectionManager"/>
/// with a null-socket <see cref="EunomiaClient"/> registered stands in for "has a live WS session"
/// (its SendAsync/broadcast path is a safe no-op with a null socket), and a real
/// <see cref="PgKeyedPacketStore"/> (over an in-process SQLite database) proves keyed puts actually
/// land in the store.
/// </summary>
public class PacketsControllerTests : IDisposable
{
    private const string Scope = "mc.controller-tests:25565";
    private const string Channel = "eunomia:controller-test";

    private readonly SqliteDbContextFactory _factory = new();

    public void Dispose()
    {
        _factory.Dispose();
    }

    [Fact]
    public async Task NotificationAsync_InvalidSenderGuid_Returns400()
    {
        ConnectionManager connectionManager = new();
        PacketsController controller = CreateController(connectionManager, NewStore());
        PacketEnvelope env = NewEnvelope(sender: "not-a-guid");

        IActionResult result = await controller.NotificationAsync(env);

        Assert.IsType<BadRequestObjectResult>(result);
    }

    [Fact]
    public async Task NotificationAsync_NoLiveSession_Returns409()
    {
        ConnectionManager connectionManager = new();
        PacketsController controller = CreateController(connectionManager, NewStore());
        PacketEnvelope env = NewEnvelope(sender: Guid.NewGuid().ToString());

        // Deliberately not registered with connectionManager - no live WS session for this identity.
        IActionResult result = await controller.NotificationAsync(env);

        Assert.IsType<ConflictObjectResult>(result);
    }

    [Fact]
    public async Task NotificationAsync_OversizeBody_Returns413()
    {
        ConnectionManager connectionManager = new();
        PacketsController controller = CreateController(connectionManager, NewStore(), contentLength: 1024 * 1024 + 1);
        PacketEnvelope env = NewEnvelope(sender: Guid.NewGuid().ToString());

        // The size gate runs before the connection check, so an unregistered sender still hits 413.
        IActionResult result = await controller.NotificationAsync(env);

        StatusCodeResult statusResult = Assert.IsType<StatusCodeResult>(result);
        Assert.Equal(StatusCodes.Status413PayloadTooLarge, statusResult.StatusCode);
    }

    [Fact]
    public async Task NotificationAsync_HappyPath_ConnectedSender_Returns200()
    {
        ConnectionManager connectionManager = new();
        Guid senderId = Guid.NewGuid();
        connectionManager.OnConnectionAdded(new EunomiaClient(senderId) { Scope = Scope });
        PacketsController controller = CreateController(connectionManager, NewStore());
        PacketEnvelope env = NewEnvelope(sender: senderId.ToString());

        IActionResult result = await controller.NotificationAsync(env);

        Assert.IsType<OkResult>(result);
    }

    [Fact]
    public async Task KeyedNotificationAsync_NullKey_Returns400()
    {
        ConnectionManager connectionManager = new();
        Guid senderId = Guid.NewGuid();
        connectionManager.OnConnectionAdded(new EunomiaClient(senderId) { Scope = Scope });
        PacketsController controller = CreateController(connectionManager, NewStore());
        PacketEnvelope env = NewEnvelope(sender: senderId.ToString(), key: null);

        IActionResult result = await controller.KeyedNotificationAsync(env);

        Assert.IsType<BadRequestObjectResult>(result);
    }

    [Fact]
    public async Task KeyedNotificationAsync_NoLiveSession_Returns409_AndDoesNotStore()
    {
        ConnectionManager connectionManager = new();
        IKeyedPacketStore store = NewStore();
        PacketsController controller = CreateController(connectionManager, store);
        PacketEnvelope env = NewEnvelope(sender: Guid.NewGuid().ToString(), key: "some-key");

        IActionResult result = await controller.KeyedNotificationAsync(env);

        Assert.IsType<ConflictObjectResult>(result);
        Assert.Empty(store.SnapshotFor(Scope));
    }

    [Fact]
    public async Task KeyedNotificationAsync_HappyPath_Returns200_AndStoresThePayload()
    {
        ConnectionManager connectionManager = new();
        Guid senderId = Guid.NewGuid();
        connectionManager.OnConnectionAdded(new EunomiaClient(senderId) { Scope = Scope });
        IKeyedPacketStore store = NewStore();
        PacketsController controller = CreateController(connectionManager, store);
        PacketEnvelope env = NewEnvelope(sender: senderId.ToString(), key: "player-1");

        IActionResult result = await controller.KeyedNotificationAsync(env);

        Assert.IsType<OkResult>(result);
        StoreSyncPayload snapshot = Assert.Single(store.SnapshotFor(Scope));
        Assert.Equal(Channel, snapshot.Channel);
        Assert.True(snapshot.Entries.ContainsKey("player-1"));
    }

    [Fact]
    public async Task NotificationAsync_BlockedScope_Returns403()
    {
        ConnectionManager connectionManager = new();
        Guid senderId = Guid.NewGuid();
        connectionManager.OnConnectionAdded(new EunomiaClient(senderId) { Scope = Scope });
        PacketsController controller = CreateController(connectionManager, NewStore(), blockService: BlockedFor(Scope));
        PacketEnvelope env = NewEnvelope(sender: senderId.ToString());

        IActionResult result = await controller.NotificationAsync(env);

        StatusCodeResult statusResult = Assert.IsType<StatusCodeResult>(result);
        Assert.Equal(StatusCodes.Status403Forbidden, statusResult.StatusCode);
    }

    [Fact]
    public async Task KeyedNotificationAsync_BlockedScope_Returns403_AndDoesNotStore()
    {
        ConnectionManager connectionManager = new();
        Guid senderId = Guid.NewGuid();
        connectionManager.OnConnectionAdded(new EunomiaClient(senderId) { Scope = Scope });
        IKeyedPacketStore store = NewStore();
        PacketsController controller = CreateController(connectionManager, store, blockService: BlockedFor(Scope));
        PacketEnvelope env = NewEnvelope(sender: senderId.ToString(), key: "player-1");

        IActionResult result = await controller.KeyedNotificationAsync(env);

        StatusCodeResult statusResult = Assert.IsType<StatusCodeResult>(result);
        Assert.Equal(StatusCodes.Status403Forbidden, statusResult.StatusCode);
        Assert.Empty(store.SnapshotFor(Scope));
    }

    private IKeyedPacketStore NewStore()
    {
        return new PgKeyedPacketStore(_factory);
    }

    private static IServerBlockService BlockedFor(string blockedScope)
    {
        IServerBlockService blockService = Substitute.For<IServerBlockService>();
        blockService.IsBlocked(blockedScope).Returns(true);
        return blockService;
    }

    private static PacketsController CreateController(
        ConnectionManager connectionManager,
        IKeyedPacketStore store,
        long? contentLength = null,
        IServerBlockService? blockService = null)
    {
        DefaultHttpContext httpContext = new();
        if (contentLength.HasValue)
        {
            httpContext.Request.ContentLength = contentLength;
        }

        return new PacketsController(
            connectionManager,
            store,
            blockService ?? Substitute.For<IServerBlockService>(),
            NullLogger<PacketsController>.Instance)
        {
            ControllerContext = new ControllerContext { HttpContext = httpContext },
        };
    }

    private static PacketEnvelope NewEnvelope(string sender, string? key = "unused-key")
    {
        return new PacketEnvelope
        {
            Scope = Scope,
            Channel = Channel,
            Key = key,
            Replicated = true,
            Sender = sender,
            Payload = JsonDocument.Parse("""{"value":1}""").RootElement,
        };
    }
}
