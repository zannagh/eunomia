// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using Eunomia.Server.Data.Entities;

namespace Eunomia.Server.Authentication.Resources;

/// <summary>
/// Outcome of resolving an OAuth callback code to a persisted <see cref="User"/>. <see cref="Status"/>
/// distinguishes "the code was never issued by us" from "the provider rejected it" so callers can map
/// each to the right response.
/// </summary>
public sealed record OAuthLoginResult(OAuthLoginStatus Status, User? User, string UserName, string UserId)
{
    public static OAuthLoginResult Failed(OAuthLoginStatus status)
    {
        return new OAuthLoginResult(status, null, string.Empty, string.Empty);
    }

    public static OAuthLoginResult Succeeded(User user, string userName, string userId)
    {
        return new OAuthLoginResult(OAuthLoginStatus.Success, user, userName, userId);
    }
}
