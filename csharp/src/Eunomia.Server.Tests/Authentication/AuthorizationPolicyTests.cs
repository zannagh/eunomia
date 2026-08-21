// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using System.Security.Claims;
using Eunomia.Server.Authentication;
using Eunomia.Server.Authentication.Configuration;
using Eunomia.Server.Data.Entities;
using Microsoft.AspNetCore.Authorization;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.FileProviders;
using Microsoft.Extensions.Hosting;

namespace Eunomia.Server.Tests.Authentication;

public class AuthorizationPolicyTests
{
    [Theory]
    [InlineData(IdentityRole.Guest, false, false)]
    [InlineData(IdentityRole.User, false, false)]
    [InlineData(IdentityRole.Moderator, true, false)]
    [InlineData(IdentityRole.Admin, true, true)]
    public async Task Policies_EnforceRoleHierarchy(IdentityRole role, bool staffAllowed, bool adminAllowed)
    {
        IAuthorizationService authorization = BuildAuthorizationService();
        ClaimsPrincipal principal = PrincipalWithRole(role);

        AuthorizationResult staff = await authorization.AuthorizeAsync(principal, null, AuthorizationPolicies.StaffOnly);
        AuthorizationResult admin = await authorization.AuthorizeAsync(principal, null, AuthorizationPolicies.AdminOnly);

        Assert.Equal(staffAllowed, staff.Succeeded);
        Assert.Equal(adminAllowed, admin.Succeeded);
    }

    private static IAuthorizationService BuildAuthorizationService()
    {
        ServiceCollection services = new();
        services.AddLogging();
        IConfiguration configuration = new ConfigurationBuilder().Build();
        services.AddEunomiaAuthentication(configuration, new StubHostEnvironment());
        return services.BuildServiceProvider().GetRequiredService<IAuthorizationService>();
    }

    private static ClaimsPrincipal PrincipalWithRole(IdentityRole role)
    {
        ClaimsIdentity identity = new("TestCookie");
        identity.AddClaim(new Claim(ClaimTypes.Name, "Tester"));
        identity.AddClaim(new Claim(ClaimTypes.Role, role.ToString()));
        return new ClaimsPrincipal(identity);
    }

    private sealed class StubHostEnvironment : IHostEnvironment
    {
        public string EnvironmentName { get; set; } = Environments.Development;

        public string ApplicationName { get; set; } = "Eunomia.Tests";

        public string ContentRootPath { get; set; } = AppContext.BaseDirectory;

        public IFileProvider ContentRootFileProvider { get; set; } = new NullFileProvider();
    }
}
