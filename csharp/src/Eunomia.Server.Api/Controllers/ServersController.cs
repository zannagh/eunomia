// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using Eunomia.Server.Api.Models;
using Eunomia.Server.Authentication.Configuration;
using Eunomia.Server.Core.Servers;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace Eunomia.Server.Api.Controllers;

/// <summary>
/// Dashboard-facing telemetry and moderation surface over the known Minecraft servers (scopes).
/// Cookie-authenticated and gated to staff (Moderator or Admin) via <see cref="AuthorizationPolicies.StaffOnly"/>
/// for viewing telemetry and logs. Blocking/unblocking a server is a destructive moderation action and is
/// tightened to Admin-only via <see cref="AuthorizationPolicies.AdminOnly"/>, alongside user-role management.
/// </summary>
[ApiController]
[Route("api/servers")]
[Authorize(Policy = AuthorizationPolicies.StaffOnly)]
[Produces("application/json")]
public class ServersController : ControllerBase
{
    private readonly IServerDirectory directory;
    private readonly IServerBlockService blockService;

    public ServersController(IServerDirectory directory, IServerBlockService blockService)
    {
        this.directory = directory;
        this.blockService = blockService;
    }

    [HttpGet]
    public async Task<ActionResult<IReadOnlyList<ServerSummary>>> ListAsync(CancellationToken cancellationToken)
    {
        return Ok(await directory.ListAsync(cancellationToken));
    }

    [HttpGet("{scope}")]
    public async Task<ActionResult<ServerDetail>> GetAsync(string scope, CancellationToken cancellationToken)
    {
        ServerDetail? detail = await directory.GetAsync(scope, cancellationToken);
        if (detail is null)
        {
            return NotFound();
        }

        return Ok(detail);
    }

    [HttpGet("{scope}/logs")]
    public async Task<ActionResult<IReadOnlyList<ServerLogRecord>>> LogsAsync(
        string scope,
        [FromQuery] string? level,
        [FromQuery] int limit = 100,
        CancellationToken cancellationToken = default)
    {
        return Ok(await directory.GetLogsAsync(scope, level, limit, cancellationToken));
    }

    [HttpPost("{scope}/block")]
    [Authorize(Policy = AuthorizationPolicies.AdminOnly)]
    public async Task<IActionResult> BlockAsync(
        string scope,
        [FromBody] BlockServerRequest? request,
        CancellationToken cancellationToken)
    {
        await blockService.BlockAsync(scope, request?.Reason, cancellationToken);
        return NoContent();
    }

    [HttpPost("{scope}/unblock")]
    [Authorize(Policy = AuthorizationPolicies.AdminOnly)]
    public async Task<IActionResult> UnblockAsync(string scope, CancellationToken cancellationToken)
    {
        await blockService.UnblockAsync(scope, cancellationToken);
        return NoContent();
    }
}
