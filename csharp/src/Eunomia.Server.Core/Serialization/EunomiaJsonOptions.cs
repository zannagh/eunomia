// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using System.Text.Json;

namespace Eunomia.Server.Core.Serialization;

/// <summary>
/// Shared <see cref="JsonSerializerOptions"/> for the wire contract shared with the Java client.
/// All server->client and stored payloads must use these options so field names stay camelCase.
/// </summary>
public static class EunomiaJsonOptions
{
    public static readonly JsonSerializerOptions Wire = new()
    {
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
    };
}
