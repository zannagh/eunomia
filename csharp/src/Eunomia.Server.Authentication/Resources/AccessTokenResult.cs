// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using System.Text.Json.Serialization;

namespace Eunomia.Server.Authentication.Resources;

/// <summary>An issued JWT plus its rotation refresh token, shaped as a standard OAuth token response.</summary>
public class AccessTokenResult
{
    [JsonPropertyName("access_token")]
    public required string AccessToken { get; set; }

    [JsonPropertyName("refresh_token")]
    public string? RefreshToken { get; set; }

    [JsonPropertyName("token_type")]
    public required string TokenType { get; set; }

    [JsonPropertyName("expires_in")]
    public required int ExpiresIn { get; set; }
}
