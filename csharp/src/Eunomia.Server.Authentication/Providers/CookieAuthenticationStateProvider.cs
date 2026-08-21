// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using System.Security.Claims;
using System.Text;
using Microsoft.AspNetCore.Authentication;
using Microsoft.AspNetCore.Authentication.Cookies;
using Microsoft.AspNetCore.Components.Authorization;
using Microsoft.AspNetCore.DataProtection;
using Microsoft.AspNetCore.Http;
using Serilog;

namespace Eunomia.Server.Authentication.Providers;

/// <summary>
/// Supplies the Blazor authentication state from the request principal, falling back to manually
/// decrypting the cookie ticket when the principal is not yet materialized (e.g. during the initial
/// render before the auth middleware has populated <c>HttpContext.User</c>).
/// </summary>
public class CookieAuthenticationStateProvider : AuthenticationStateProvider
{
    private readonly IHttpContextAccessor httpContextAccessor;
    private readonly IDataProtectionProvider dataProtectionProvider;
    private AuthenticationState? cachedState;
    private bool isInitialized;

    public CookieAuthenticationStateProvider(IHttpContextAccessor httpContextAccessor, IDataProtectionProvider dataProtectionProvider)
    {
        this.dataProtectionProvider = dataProtectionProvider;
        this.httpContextAccessor = httpContextAccessor;
    }

    public override Task<AuthenticationState> GetAuthenticationStateAsync()
    {
        if (isInitialized && cachedState != null)
        {
            return Task.FromResult(cachedState);
        }

        HttpContext? httpContext = httpContextAccessor.HttpContext;

        if (httpContext is { User.Identity.IsAuthenticated: true })
        {
            cachedState = new AuthenticationState(httpContext.User);
            isInitialized = true;
            return Task.FromResult(cachedState);
        }

        string cookieName = $".AspNetCore.{CookieAuthenticationDefaults.AuthenticationScheme}";
        if (httpContext?.Request.Cookies.TryGetValue(cookieName, out string? cookieValue) == true
            && !string.IsNullOrEmpty(cookieValue))
        {
            try
            {
                ClaimsPrincipal? principal = ParseCookieTicket(cookieValue);
                if (principal?.Identity?.IsAuthenticated == true)
                {
                    cachedState = new AuthenticationState(principal);
                    return Task.FromResult(cachedState);
                }
            }
            catch (Exception ex)
            {
                Log.Error(ex, "[Cookie Authentication] Failed to parse cookie");
            }
        }

        cachedState = new AuthenticationState(new ClaimsPrincipal(new ClaimsIdentity()));
        return Task.FromResult(cachedState);
    }

    private ClaimsPrincipal? ParseCookieTicket(string cookieValue)
    {
        try
        {
            IDataProtector dataProtector = dataProtectionProvider.CreateProtector(
                "Microsoft.AspNetCore.Authentication.Cookies.CookieAuthenticationMiddleware",
                CookieAuthenticationDefaults.AuthenticationScheme,
                "v2");

            string ticketData = dataProtector.Unprotect(cookieValue);
            AuthenticationTicket? ticket = TicketSerializer.Default.Deserialize(Encoding.UTF8.GetBytes(ticketData));

            if (ticket?.Principal != null &&
                ticket.Properties.ExpiresUtc > DateTimeOffset.UtcNow)
            {
                return ticket.Principal;
            }
        }
        catch (Exception e)
        {
            Log.Error(e, "[Cookie Authentication] Failed to parse cookie, it may have expired or been tampered with");
        }

        return null;
    }
}
