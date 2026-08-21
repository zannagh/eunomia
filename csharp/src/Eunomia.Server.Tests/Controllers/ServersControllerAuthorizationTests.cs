// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using System.Reflection;
using Eunomia.Server.Api.Controllers.V0_3;
using Eunomia.Server.Authentication.Configuration;
using Microsoft.AspNetCore.Authorization;

namespace Eunomia.Server.Tests.Controllers;

/// <summary>
/// Pins the authorization surface of <see cref="ServersController"/>: viewing telemetry/logs is StaffOnly
/// (Moderator or Admin) via the class-level policy, while blocking/unblocking a server is tightened to
/// AdminOnly on the individual actions. A Moderator can view but must be forbidden from block/unblock.
/// </summary>
public class ServersControllerAuthorizationTests
{
    [Fact]
    public void Controller_IsStaffOnly_ForViewingByDefault()
    {
        AuthorizeAttribute? attribute = typeof(ServersController)
            .GetCustomAttribute<AuthorizeAttribute>(inherit: false);

        Assert.NotNull(attribute);
        Assert.Equal(AuthorizationPolicies.StaffOnly, attribute!.Policy);
    }

    [Theory]
    [InlineData(nameof(ServersController.BlockAsync))]
    [InlineData(nameof(ServersController.UnblockAsync))]
    public void BlockAndUnblock_AreAdminOnly(string actionName)
    {
        AuthorizeAttribute attribute = MethodPolicy(actionName);

        Assert.Equal(AuthorizationPolicies.AdminOnly, attribute.Policy);
    }

    [Theory]
    [InlineData(nameof(ServersController.ListAsync))]
    [InlineData(nameof(ServersController.GetAsync))]
    [InlineData(nameof(ServersController.LogsAsync))]
    public void ViewActions_InheritStaffOnly_WithoutTighteningToAdmin(string actionName)
    {
        AuthorizeAttribute? attribute = typeof(ServersController)
            .GetMethod(actionName)!
            .GetCustomAttribute<AuthorizeAttribute>(inherit: false);

        // No method-level policy: these inherit the class-level StaffOnly, so a Moderator keeps read access.
        Assert.Null(attribute);
    }

    private static AuthorizeAttribute MethodPolicy(string actionName)
    {
        AuthorizeAttribute? attribute = typeof(ServersController)
            .GetMethod(actionName)!
            .GetCustomAttribute<AuthorizeAttribute>(inherit: false);

        Assert.NotNull(attribute);
        return attribute!;
    }
}
