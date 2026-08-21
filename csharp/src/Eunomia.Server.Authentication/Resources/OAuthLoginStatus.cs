// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

namespace Eunomia.Server.Authentication.Resources;

/// <summary>Why an OAuth code exchange succeeded or failed.</summary>
public enum OAuthLoginStatus
{
    /// <summary>The code resolved to a provider profile and a persisted user.</summary>
    Success,

    /// <summary>The code was not one we handed out (unknown, already spent, or swept).</summary>
    UnknownCode,

    /// <summary>The provider refused the code exchange or returned no usable profile.</summary>
    ProviderRejected,

    /// <summary>The provider answered, but without the id/name we need to build an identifier.</summary>
    IncompleteProfile,
}
