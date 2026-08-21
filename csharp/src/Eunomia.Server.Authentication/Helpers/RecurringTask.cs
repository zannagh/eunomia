// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

namespace Eunomia.Server.Authentication.Helpers;

/// <summary>Fire-and-forget loop that runs <paramref name="action"/> every <paramref name="interval"/>.</summary>
public static class RecurringTask
{
    public static void Create(Action action, TimeSpan interval, CancellationToken cancellationToken)
    {
        _ = Task.Run(async () =>
        {
            while (!cancellationToken.IsCancellationRequested)
            {
                await Task.Delay(interval, cancellationToken);
                action();
            }
        }, cancellationToken);
    }
}
