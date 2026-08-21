// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using System.ComponentModel;
using System.Text.Json;
using Eunomia.Server.Api.Models;
using Eunomia.Server.Core.Communication;
using Eunomia.Server.Core.Serialization;
using Eunomia.Server.Core.Servers;
using Eunomia.Server.Core.Storage;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Mvc;
using Microsoft.Extensions.Logging;

namespace Eunomia.Server.Api.Controllers;

/// <summary>
/// Accepts plain and keyed packet notifications from a client's REST fallback transport and
/// relays them to the rest of that client's scope over websocket. Requires the sender to already
/// hold a live websocket session for the target scope, so REST puts cannot be spoofed by an
/// identity that has never connected. A blocked scope is refused with 403 before any store/relay.
/// </summary>
[ApiController]
[Route("api/packets")]
[AllowAnonymous]
[Produces("application/json")]
public class PacketController : ControllerBase
{
    private const long MaxBodyBytes = 1024 * 1024;

    private readonly ConnectionManager _connectionManager;
    private readonly IKeyedPacketStore _store;
    private readonly IServerBlockService _blockService;
    private readonly ILogger<PacketController> _logger;

    public PacketController(
        ConnectionManager connectionManager,
        IKeyedPacketStore store,
        IServerBlockService blockService,
        ILogger<PacketController> logger)
    {
        _connectionManager = connectionManager;
        _store = store;
        _blockService = blockService;
        _logger = logger;
    }

    [HttpPut("plain")]
    public async Task<IActionResult> NotificationAsync([FromBody] PacketEnvelope env)
    {
        IActionResult? gate = ValidateRequest(env, out Guid sender);
        if (gate is not null)
        {
            return gate;
        }

        string frame = JsonSerializer.Serialize(WsFrame<PacketEnvelope>.Envelope(env), EunomiaJsonOptions.Wire);
        await _connectionManager.BroadcastToScopeAsync(env.Scope, frame, sender);
        using (_logger.BeginScope(ServerScope.Property(env.Scope)))
        {
            _logger.LogDebug("Relayed plain packet on {Channel} for {Scope}", env.Channel, env.Scope);
        }

        return Ok();
    }

    [HttpPut("keyed")]
    [Description("A keyed notification that gets stored by the server.")]
    public async Task<IActionResult> KeyedNotificationAsync([FromBody] PacketEnvelope env)
    {
        IActionResult? gate = ValidateRequest(env, out Guid sender);
        if (gate is not null)
        {
            return gate;
        }

        if (env.Key is null)
        {
            return BadRequest("Keyed notifications require a non-null key.");
        }

        _store.Put(env.Scope, env.Channel, env.Key, env.Payload);

        string frame = JsonSerializer.Serialize(WsFrame<PacketEnvelope>.Envelope(env), EunomiaJsonOptions.Wire);
        await _connectionManager.BroadcastToScopeAsync(env.Scope, frame, sender);
        using (_logger.BeginScope(ServerScope.Property(env.Scope)))
        {
            _logger.LogDebug("Stored keyed packet {Key} on {Channel} for {Scope}", env.Key, env.Channel, env.Scope);
        }

        return Ok();
    }

    private IActionResult? ValidateRequest(PacketEnvelope env, out Guid sender)
    {
        sender = Guid.Empty;

        if (Request.ContentLength is > MaxBodyBytes)
        {
            return StatusCode(StatusCodes.Status413PayloadTooLarge);
        }

        if (_blockService.IsBlocked(env.Scope))
        {
            return StatusCode(StatusCodes.Status403Forbidden);
        }

        // These land in varchar(512) columns; reject over-long values here rather than letting the
        // insert fail with a DbUpdateException after the packet has already been relayed.
        if (!StorageLimits.IsWithinLimit(env.Scope)
            || !StorageLimits.IsWithinLimit(env.Channel)
            || !StorageLimits.IsWithinLimit(env.Key))
        {
            return BadRequest($"Scope, channel, and key must each be at most {StorageLimits.MaxIdentifierLength} characters.");
        }

        if (!Guid.TryParse(env.Sender, out sender))
        {
            return BadRequest("Sender must be a valid UUID.");
        }

        if (!_connectionManager.IsConnected(env.Scope, sender))
        {
            return Conflict("No live websocket session for this identity/scope.");
        }

        return null;
    }
}
