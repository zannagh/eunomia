// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using System.IO;
using System.Text;
using Eunomia.Server.Authentication.Configuration;
using Eunomia.Server.Authentication.Handlers;
using Eunomia.Server.Authentication.Middleware;
using Eunomia.Server.Authentication.Providers;
using Eunomia.Server.Authentication.Services;
using Eunomia.Server.Data.Entities;
using Microsoft.AspNetCore.Authentication.Cookies;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.AspNetCore.Builder;
using Microsoft.AspNetCore.Components.Authorization;
using Microsoft.AspNetCore.DataProtection;
using Microsoft.AspNetCore.Http;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Hosting;
using Microsoft.IdentityModel.Tokens;

namespace Eunomia.Server.Authentication;

/// <summary>
/// Wires the cookie + JWT authentication stack and the StaffOnly/AdminOnly authorization policies, and
/// exposes the middleware ordering (authentication → optional dev bypass → authorization → antiforgery).
/// </summary>
public static class AuthenticationServices
{
    /// <summary>The policy scheme that routes <c>Authorization: Bearer</c> to JWT and everything else to the cookie.</summary>
    public const string PolicyScheme = "EunomiaPolicy";

    public static IServiceCollection AddEunomiaAuthentication(
        this IServiceCollection services,
        IConfiguration configuration,
        IHostEnvironment environment)
    {
        EunomiaAuthSettings settings = new(configuration);
        services.AddSingleton(settings);

        services.AddHttpClient();
        services.AddHttpContextAccessor();
        services.AddCascadingAuthenticationState();

        IDataProtectionBuilder dataProtection = services.AddDataProtection().SetApplicationName("Eunomia");
        if (!environment.IsDevelopment())
        {
            // Persist the key ring to the mounted volume (compose maps ./dpkeys -> /app/keys) so auth
            // cookies and OAuth state survive redeploys instead of logging everyone out on each deploy.
            dataProtection.PersistKeysToFileSystem(new DirectoryInfo("/app/keys"));
        }

        services.AddSingleton<RedirectUriProvider>();
        services.AddSingleton<CodeBasedAuthProvider>();
        services.AddSingleton<IRefreshTokenHandler, RefreshTokenHandler>();
        services.AddSingleton<ISecurityTokenHandler, JwtTokenHandler>();

        services.AddScoped<AuthenticationStateProvider, CookieAuthenticationStateProvider>();
        services.AddScoped<ICurrentUserService, CurrentUserService>();
        services.AddScoped<IAccountLinkService, AccountLinkService>();
        services.AddScoped<IOAuthLoginService, OAuthLoginService>();
        services.AddScoped<IUserAdminService, UserAdminService>();

        services.AddAuthentication(PolicyScheme)
            .AddPolicyScheme(PolicyScheme, PolicyScheme, options =>
            {
                options.ForwardDefaultSelector = context =>
                {
                    string? authHeader = context.Request.Headers.Authorization.FirstOrDefault();
                    if (!string.IsNullOrEmpty(authHeader) && authHeader.StartsWith("Bearer ", StringComparison.OrdinalIgnoreCase))
                    {
                        return JwtBearerDefaults.AuthenticationScheme;
                    }

                    return CookieAuthenticationDefaults.AuthenticationScheme;
                };
            })
            .AddJwtBearer(options =>
            {
                options.TokenValidationParameters = new TokenValidationParameters
                {
                    LogValidationExceptions = true,
                    ValidateLifetime = true,
                    ValidateIssuerSigningKey = true,
                    IssuerSigningKey = new SymmetricSecurityKey(Encoding.UTF8.GetBytes(settings.JwtKey)),
                    ValidateIssuer = true,
                    ValidIssuer = settings.Server.JwtIssuer,
                    ValidateAudience = false,
                };
            })
            .AddCookie(CookieAuthenticationDefaults.AuthenticationScheme, options =>
            {
                options.LoginPath = "/account/login";
                options.LogoutPath = "/account/logout";

                // Authenticated-but-under-privileged users are forbidden by the authorization
                // middleware on the initial SSR request; send them to the Blazor 403 page instead of
                // the framework default (/Account/AccessDenied), which has no handler and 404s.
                options.AccessDeniedPath = "/account/access-denied";
                options.SlidingExpiration = true;
                options.ExpireTimeSpan = TimeSpan.FromHours(8);
            });

        services.AddAuthorization(options =>
        {
            options.AddPolicy(AuthorizationPolicies.StaffOnly, policy =>
                policy.RequireRole(nameof(IdentityRole.Moderator), nameof(IdentityRole.Admin)));
            options.AddPolicy(AuthorizationPolicies.AdminOnly, policy =>
                policy.RequireRole(nameof(IdentityRole.Admin)));
        });

        services.AddAntiforgery(options =>
        {
            options.Cookie.SecurePolicy = environment.IsDevelopment()
                ? CookieSecurePolicy.SameAsRequest
                : CookieSecurePolicy.Always;
            options.Cookie.HttpOnly = true;
            options.Cookie.SameSite = SameSiteMode.Strict;
        });

        return services;
    }

    public static WebApplication UseEunomiaAuthentication(this WebApplication app)
    {
        app.UseAuthentication();

        // Opt-in dev bypass: only when running Development AND EUNOMIA_DEV_USER names an identifier to act as.
        if (app.Environment.IsDevelopment()
            && !string.IsNullOrWhiteSpace(Environment.GetEnvironmentVariable("EUNOMIA_DEV_USER")))
        {
            app.UseMiddleware<DevAuthenticationMiddleware>();
        }

        app.UseAuthorization();
        app.UseAntiforgery();
        return app;
    }
}
