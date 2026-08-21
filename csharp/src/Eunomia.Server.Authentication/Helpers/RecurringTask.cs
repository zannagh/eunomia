// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using Serilog;

namespace Eunomia.Server.Authentication.Helpers;

/// <summary>Fire-and-forget loop that runs an action on a fixed interval until cancelled.</summary>
public static class RecurringTask
{
    public static void Create(Action action, TimeSpan interval, CancellationToken cancellationToken)
    {
        _ = Task.Run(
            async () =>
            {
                while (!cancellationToken.IsCancellationRequested)
                {
                    try
                    {
                        await Task.Delay(interval, cancellationToken);
                        action();
                    }
                    catch (OperationCanceledException)
                    {
                        return;
                    }
                    catch (Exception ex)
                    {
                        // One bad tick must not silently retire the loop for the process lifetime -
                        // these drive the OAuth state/code sweeps.
                        Log.Error(ex, "[RecurringTask] Scheduled action threw; continuing");
                    }
                }
            },
            cancellationToken);
    }
}
