// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

namespace Eunomia.Server.Api.Models;

/// <summary>
/// Body of a block request: the operator-supplied reason recorded on the server record and shown in
/// the dashboard. Null/empty is allowed (blocked without a stated reason).
/// </summary>
public sealed record BlockServerRequest
{
    public string? Reason { get; init; }
}
