// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

namespace Eunomia.Server.Authentication.Configuration;

/// <summary>
/// Public-facing server coordinates. <see cref="JwtIssuer"/> doubles as the token issuer so
/// self-issued JWTs validate against the same origin that produced them.
/// </summary>
public class AuthServerSettings
{
    public string Url { get; set; } = "https://localhost:5001";

    public int Port { get; set; } = 5001;

    public string JwtIssuer => Url;
}
