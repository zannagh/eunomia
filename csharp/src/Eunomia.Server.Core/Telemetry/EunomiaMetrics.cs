using System.Diagnostics.Metrics;

namespace Eunomia.Server.Core.Telemetry;

public static class EunomiaMetrics
{
    public static readonly Histogram<double> OperationDuration = EunomiaTelemetry.Meter.CreateHistogram<double>(
        "eunomia.operation.duration", unit: "ms", description: "Duration of instrumented service operations, tagged by operation name.");
}
