// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

namespace Eunomia.Server.Authentication.Resources;

/// <summary>Outcome of exchanging an OAuth code and reading the provider's user profile.</summary>
public class VerificationResult
{
    public required bool Success { get; set; }

    public required string UserName { get; set; }

    public required string UserId { get; set; }
}
