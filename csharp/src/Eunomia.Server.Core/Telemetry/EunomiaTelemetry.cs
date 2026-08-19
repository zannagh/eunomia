using System.Diagnostics;
using System.Diagnostics.Metrics;

namespace Eunomia.Server.Core.Telemetry;

public class EunomiaTelemetry
{
    public static readonly ActivitySource ActivitySource = new("Eunomia");
    public static readonly Meter Meter = new("Eunomia");

    /// <summary>
    /// Starts timing an operation; dispose the result to record its duration and span.
    /// </summary>
    public static OperationTimer Time(string operation, Guid? serverId = null)
    {
        return new OperationTimer(operation, serverId);
    }
}
